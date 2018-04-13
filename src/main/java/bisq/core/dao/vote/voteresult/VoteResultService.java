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

package bisq.core.dao.vote.voteresult;

import bisq.core.dao.node.NodeExecutor;
import bisq.core.dao.param.DaoParamService;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.vo.BsqBlock;
import bisq.core.dao.vote.BooleanVote;
import bisq.core.dao.vote.LongVote;
import bisq.core.dao.vote.PeriodService;
import bisq.core.dao.vote.Vote;
import bisq.core.dao.vote.blindvote.BlindVoteList;
import bisq.core.dao.vote.blindvote.BlindVoteService;
import bisq.core.dao.vote.proposal.ProposalPayload;
import bisq.core.dao.vote.proposal.asset.RemoveAssetProposalPayload;
import bisq.core.dao.vote.proposal.compensation.CompensationRequestPayload;
import bisq.core.dao.vote.proposal.generic.GenericProposalPayload;
import bisq.core.dao.vote.proposal.param.ChangeParamProposalPayload;
import bisq.core.dao.vote.voteresult.issuance.IssuanceService;
import bisq.core.dao.vote.votereveal.VoteRevealConsensus;
import bisq.core.dao.vote.votereveal.VoteRevealService;

import bisq.common.util.Utilities;

import javax.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
public class VoteResultService {
    private final NodeExecutor nodeExecutor;
    private final BlindVoteService blindVoteService;
    private final VoteRevealService voteRevealService;
    private final StateService stateService;
    private final DaoParamService daoParamService;
    private final PeriodService periodService;
    private final IssuanceService issuanceService;
    @Getter
    private final ObservableList<VoteResultException> voteResultExceptions = FXCollections.observableArrayList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public VoteResultService(NodeExecutor nodeExecutor,
                             BlindVoteService blindVoteService,
                             VoteRevealService voteRevealService,
                             StateService stateService,
                             DaoParamService daoParamService,
                             PeriodService periodService,
                             IssuanceService issuanceService) {
        this.nodeExecutor = nodeExecutor;
        this.blindVoteService = blindVoteService;
        this.voteRevealService = voteRevealService;
        this.stateService = stateService;
        this.daoParamService = daoParamService;
        this.periodService = periodService;
        this.issuanceService = issuanceService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        stateService.addListener(new StateService.Listener() {
            // We set the nodeExecutor as we want to get called in the context of the parser thread
            //TODO issuance fails if parser thread is set. need to refactor class first
          /*  @Override
            public Executor getExecutor() {
                return nodeExecutor.get();
            }*/

            @Override
            public void onBlockAdded(BsqBlock bsqBlock) {
                maybeApplyVoteResult(bsqBlock.getHeight());
            }
        });
    }

    public void shutDown() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We are in parser thread!
    private void maybeApplyVoteResult(int chainHeight) {
        if (periodService.getPhaseForHeight(chainHeight) == PeriodService.Phase.ISSUANCE) {
            applyVoteResult(chainHeight);
        }
    }

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
                    Map<ProposalPayload, List<VoteWithStake>> resultListByProposalPayloadMap = getResultListByProposalPayloadMap(decryptedVoteByVoteRevealTxIdSet);
                    processAllVoteResults(resultListByProposalPayloadMap, chainHeight, daoParamService);
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
                        return new DecryptedVote(opReturnData, voteRevealTxId,
                                stateService, blindVoteService, periodService, chainHeight);
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


    private Map<ProposalPayload, List<VoteWithStake>> getResultListByProposalPayloadMap(Set<DecryptedVote> decryptedVoteByVoteRevealTxIdSet) {
        Map<ProposalPayload, List<VoteWithStake>> stakeByProposalMap = new HashMap<>();
        decryptedVoteByVoteRevealTxIdSet.forEach(decryptedVote -> {
            iterateProposals(stakeByProposalMap, decryptedVote);
        });
        return stakeByProposalMap;
    }

    private void iterateProposals(Map<ProposalPayload, List<VoteWithStake>> stakeByProposalMap, DecryptedVote decryptedVote) {
        decryptedVote.getProposalListUsedForVoting().getList()
                .forEach(proposal -> {
                    final ProposalPayload proposalPayload = proposal.getProposalPayload();
                    stakeByProposalMap.putIfAbsent(proposalPayload, new ArrayList<>());
                    final List<VoteWithStake> voteWithStakeList = stakeByProposalMap.get(proposalPayload);
                    voteWithStakeList.add(new VoteWithStake(proposal.getVote(), decryptedVote.getStake()));
                });
    }

    private void processAllVoteResults(Map<ProposalPayload, List<VoteWithStake>> map,
                                       int chainHeight,
                                       DaoParamService daoParamService) {
        map.forEach((payload, voteResultsWithStake) -> {
            ResultPerProposal resultPerProposal = getResultPerProposal(voteResultsWithStake);
            long totalStake = resultPerProposal.getStakeOfAcceptedVotes() + resultPerProposal.getStakeOfRejectedVotes();
            long quorum = daoParamService.getDaoParamValue(payload.getQuorumDaoParam(), chainHeight);
            log.info("totalStake: {}", totalStake);
            log.info("required quorum: {}", quorum);
            if (totalStake >= quorum) {
                long reachedThreshold = resultPerProposal.getStakeOfAcceptedVotes() / totalStake;
                // We multiply by 10000 as we use a long for requiredVoteThreshold and that got added precision, so
                // 50% is 50.00. As we use 100% for 1 we get another multiplied by 100, resulting in 10 000.
                reachedThreshold *= 10_000;
                long requiredVoteThreshold = daoParamService.getDaoParamValue(payload.getThresholdDaoParam(), chainHeight);
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

    private void processCompletedVoteResult(ProposalPayload proposalPayload, int chainHeight) {
        if (proposalPayload instanceof CompensationRequestPayload) {
            issuanceService.issueBsq((CompensationRequestPayload) proposalPayload, chainHeight);
        } else if (proposalPayload instanceof GenericProposalPayload) {
            //TODO impl
        } else if (proposalPayload instanceof ChangeParamProposalPayload) {
            //TODO impl
        } else if (proposalPayload instanceof RemoveAssetProposalPayload) {
            //TODO impl
        }
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
