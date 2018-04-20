/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.consensus.vote.result;

import bisq.core.dao.consensus.period.PeriodService;
import bisq.core.dao.consensus.period.Phase;
import bisq.core.dao.consensus.state.StateChangeEventsProvider;
import bisq.core.dao.consensus.state.StateService;
import bisq.core.dao.consensus.state.blockchain.TxBlock;
import bisq.core.dao.consensus.state.events.StateChangeEvent;
import bisq.core.dao.consensus.vote.BooleanVote;
import bisq.core.dao.consensus.vote.LongVote;
import bisq.core.dao.consensus.vote.Vote;
import bisq.core.dao.consensus.vote.blindvote.BlindVoteList;
import bisq.core.dao.consensus.vote.proposal.Proposal;
import bisq.core.dao.consensus.vote.proposal.compensation.CompensationProposal;
import bisq.core.dao.consensus.vote.proposal.param.ChangeParamService;
import bisq.core.dao.consensus.vote.result.issuance.IssuanceService;
import bisq.core.dao.consensus.vote.votereveal.VoteRevealConsensus;
import bisq.core.dao.consensus.vote.votereveal.VoteRevealService;

import bisq.common.util.Utilities;

import javax.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

//TODO case that user misses reveal phase not impl. yet

/**
 * Processes the vote results in the issuance phase.
 * <p>
 * // TODO we might process the preliminary results as soon the voteReveal txs arrive to show preliminary results?
 * // TODO we should store the DecryptedVote list to disk. Either as db file or as extension data structure to the
 * blockchain.
 */

@Slf4j
public class VoteResultService implements StateChangeEventsProvider {
    private final VoteRevealService voteRevealService;
    private final StateService stateService;
    private final ChangeParamService changeParamService;
    private final PeriodService periodService;
    private final IssuanceService issuanceService;
    @Getter
    private final ObservableList<VoteResultException> voteResultExceptions = FXCollections.observableArrayList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public VoteResultService(VoteRevealService voteRevealService,
                             StateService stateService,
                             ChangeParamService changeParamService,
                             PeriodService periodService,
                             IssuanceService issuanceService) {
        this.voteRevealService = voteRevealService;
        this.stateService = stateService;
        this.changeParamService = changeParamService;
        this.periodService = periodService;
        this.issuanceService = issuanceService;

        stateService.registerStateChangeEventsProvider(this);
    }



    ///////////////////////////////////////////////////////////////////////////////////////////
    // StateChangeEventsProvider
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public Set<StateChangeEvent> provideStateChangeEvents(TxBlock txBlock) {
        final int chainHeight = txBlock.getHeight();
        if (periodService.getPhaseForHeight(chainHeight) == Phase.VOTE_RESULT) {
            applyVoteResult(chainHeight);
        }

        // We have nothing to return as there are no p2p network data for vote reveal.
        return new HashSet<>();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void applyVoteResult(int chainHeight) {
        Set<DecryptedVote> decryptedVoteByVoteRevealTxIdSet = getDecryptedVoteByVoteRevealTxIdSet(chainHeight);
        if (!decryptedVoteByVoteRevealTxIdSet.isEmpty()) {
            // From the decryptedVoteByVoteRevealTxIdSet we create a map with the hash of the blind vote list as key and the
            // aggregated stake as value. That map is used for calculating the majority of the blind vote lists.
            // If there are conflicting versions due the eventually consistency of the P2P network (it might be
            // that some blind votes do not arrive at all voters which would lead to conflicts in the result calculation).
            // To solve that problem we will only consider the majority data view as valid.
            // In case multiple data views would have the same stake we sort additionally by the hex value of the
            // blind vote hash and use the first one in the sorted list as winner.
            Map<byte[], Long> stakeByHashOfVoteListMap = getStakeByHashOfVoteListMap(decryptedVoteByVoteRevealTxIdSet);
            byte[] majorityVoteListHash = getMajorityVoteListHashByTxIdMap(stakeByHashOfVoteListMap);
            if (majorityVoteListHash != null) {
                if (isBlindVoteListMatchingMajority(majorityVoteListHash, chainHeight)) {
                    Map<Proposal, List<VoteWithStake>> resultListByProposalPayloadMap = getResultListByProposalPayloadMap(decryptedVoteByVoteRevealTxIdSet);
                    processAllVoteResults(resultListByProposalPayloadMap, chainHeight, changeParamService);
                    log.info("processAllVoteResults completed");
                } else {
                    log.warn("Our list of received blind votes do not match the list from the majority of voters.");
                    // TODO request missing blind votes
                }
            } else {
                //TODO throw exception as it is likely not a valid option
                log.warn("majorityVoteListHash is null");
            }
        } else {
            log.info("There have not been any votes in that cycle. chainHeight={}", chainHeight);
        }
    }

    private Set<DecryptedVote> getDecryptedVoteByVoteRevealTxIdSet(int chainHeight) {
        // We want all voteRevealTxOutputs which are in current cycle we are processing.
        return stateService.getVoteRevealOpReturnTxOutputs().stream()
                .filter(txOutput -> periodService.isTxInCorrectCycle(txOutput.getTxId(), chainHeight))
                .map(txOutput -> {
                    final byte[] opReturnData = txOutput.getOpReturnData();
                    final String voteRevealTxId = txOutput.getTxId();
                    try {
                        return new DecryptedVote(opReturnData, voteRevealTxId, stateService, periodService, chainHeight);
                    } catch (VoteResultException e) {
                        log.error("Could not create DecryptedVote: " + e.toString());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Map<byte[], Long> getStakeByHashOfVoteListMap(Set<DecryptedVote> decryptedVoteByVoteRevealTxIdSet) {
        Map<byte[], Long> map = new HashMap<>();
        decryptedVoteByVoteRevealTxIdSet.forEach(decryptedVote -> {
            final byte[] hash = decryptedVote.getHashOfBlindVoteList();
            map.computeIfAbsent(hash, e -> 0L);
            long aggregatedStake = map.get(hash);
            aggregatedStake += decryptedVote.getStake();
            map.put(hash, aggregatedStake);
        });
        return map;
    }

    @Nullable
    private byte[] getMajorityVoteListHashByTxIdMap(Map<byte[], Long> stakeByHashOfVoteListMap) {
        List<HashWithStake> list = new ArrayList<>();
        stakeByHashOfVoteListMap.forEach((key, value) -> list.add(new HashWithStake(key, value)));
        return VoteResultConsensus.getMajorityHash(list);
    }

    private boolean isBlindVoteListMatchingMajority(byte[] majorityVoteListHash, int chainHeight) {
        // We reuse the methods at voteReveal domain used when creating the hash
        final BlindVoteList blindVoteList = voteRevealService.getSortedBlindVoteListOfCycle(chainHeight);
        byte[] hashOfBlindVoteList = VoteRevealConsensus.getHashOfBlindVoteList(blindVoteList);
        log.info("majorityVoteListHash " + Utilities.bytesAsHexString(majorityVoteListHash));
        log.info("Sha256Ripemd160 hash of my blindVoteList " + Utilities.bytesAsHexString(hashOfBlindVoteList));
        return Arrays.equals(majorityVoteListHash, hashOfBlindVoteList);
    }


    private Map<Proposal, List<VoteWithStake>> getResultListByProposalPayloadMap(Set<DecryptedVote> decryptedVoteByVoteRevealTxIdSet) {
        Map<Proposal, List<VoteWithStake>> stakeByProposalMap = new HashMap<>();
        decryptedVoteByVoteRevealTxIdSet.forEach(decryptedVote -> {
            iterateProposals(stakeByProposalMap, decryptedVote);
        });
        return stakeByProposalMap;
    }

    private void iterateProposals(Map<Proposal, List<VoteWithStake>> stakeByProposalMap, DecryptedVote decryptedVote) {
        decryptedVote.getBallotListUsedForVoting().getList()
                .forEach(proposal -> {
                    final Proposal proposalPayload = proposal.getProposal();
                    stakeByProposalMap.putIfAbsent(proposalPayload, new ArrayList<>());
                    final List<VoteWithStake> voteWithStakeList = stakeByProposalMap.get(proposalPayload);
                    voteWithStakeList.add(new VoteWithStake(proposal.getVote(), decryptedVote.getStake()));
                });
    }

    private void processAllVoteResults(Map<Proposal, List<VoteWithStake>> map,
                                       int chainHeight,
                                       ChangeParamService changeParamService) {
        map.forEach((payload, voteResultsWithStake) -> {
            ResultPerProposal resultPerProposal = getResultPerProposal(voteResultsWithStake);
            long totalStake = resultPerProposal.getStakeOfAcceptedVotes() + resultPerProposal.getStakeOfRejectedVotes();
            long quorum = changeParamService.getDaoParamValue(payload.getQuorumDaoParam(), chainHeight);
            log.info("totalStake: {}", totalStake);
            log.info("required quorum: {}", quorum);
            if (totalStake >= quorum) {
                long reachedThreshold = resultPerProposal.getStakeOfAcceptedVotes() / totalStake;
                // We multiply by 10000 as we use a long for requiredVoteThreshold and that got added precision, so
                // 50% is 50.00. As we use 100% for 1 we get another multiplied by 100, resulting in 10 000.
                reachedThreshold *= 10_000;
                long requiredVoteThreshold = changeParamService.getDaoParamValue(payload.getThresholdDaoParam(), chainHeight);
                log.info("reached threshold: {} %", reachedThreshold / 100D);
                log.info("required threshold: {} %", requiredVoteThreshold / 100D);
                // We need to exceed requiredVoteThreshold
                if (reachedThreshold > requiredVoteThreshold) {
                    processCompletedVoteResult(payload, chainHeight);
                } else {
                    log.warn("We did not reach the quorum. reachedThreshold={} %, requiredVoteThreshold={} %",
                            reachedThreshold / 100D, requiredVoteThreshold / 100D);
                }
            } else {
                log.warn("We did not reach the quorum. totalStake={}, quorum={}", totalStake, quorum);
            }
        });
    }

    private ResultPerProposal getResultPerProposal(List<VoteWithStake> voteWithStakeList) {
        long stakeOfAcceptedVotes = 0;
        long stakeOfRejectedVotes = 0;
        for (VoteWithStake voteWithStake : voteWithStakeList) {
            long stake = voteWithStake.getStake();
            Vote vote = voteWithStake.getVote();
            if (vote != null) {
                if (vote instanceof BooleanVote) {
                    BooleanVote result = (BooleanVote) vote;
                    if (result.isAccepted()) {
                        stakeOfAcceptedVotes += stake;
                    } else {
                        stakeOfRejectedVotes += stake;
                    }
                } else if (vote instanceof LongVote) {
                    //TODO impl
                }
            } else {
                log.debug("Voter ignored proposal");
            }
        }
        return new ResultPerProposal(stakeOfAcceptedVotes, stakeOfRejectedVotes);
    }

    private void processCompletedVoteResult(Proposal proposal, int chainHeight) {
        if (proposal instanceof CompensationProposal) {
            issuanceService.issueBsq((CompensationProposal) proposal, chainHeight);
        } /*else if (proposal instanceof GenericProposal) {
            //TODO impl
        } else if (proposal instanceof ChangeParamProposal) {
            //TODO impl
        } else if (proposal instanceof RemoveAssetProposalPayload) {
            //TODO impl
        }*/
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Inner classes
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Value
    public class HashWithStake {
        private final byte[] hashOfProposalList;
        private final Long stake;

        HashWithStake(byte[] hashOfProposalList, Long stake) {
            this.hashOfProposalList = hashOfProposalList;
            this.stake = stake;
        }
    }

    @Value
    private static class ResultPerProposal {
        private final long stakeOfAcceptedVotes;
        private final long stakeOfRejectedVotes;

        ResultPerProposal(long stakeOfAcceptedVotes, long stakeOfRejectedVotes) {
            this.stakeOfAcceptedVotes = stakeOfAcceptedVotes;
            this.stakeOfRejectedVotes = stakeOfRejectedVotes;
        }
    }

    @Value
    private static class VoteWithStake {
        @Nullable
        private final Vote vote;
        private final long stake;

        VoteWithStake(@Nullable Vote vote, long stake) {
            this.vote = vote;
            this.stake = stake;
        }
    }
}
