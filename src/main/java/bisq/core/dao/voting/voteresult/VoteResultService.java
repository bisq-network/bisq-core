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

package bisq.core.dao.voting.voteresult;

import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.state.period.PeriodService;
import bisq.core.dao.voting.ballot.Ballot;
import bisq.core.dao.voting.ballot.BallotFactory;
import bisq.core.dao.voting.ballot.BallotList;
import bisq.core.dao.voting.ballot.BallotListService;
import bisq.core.dao.voting.ballot.proposal.Proposal;
import bisq.core.dao.voting.ballot.proposal.compensation.CompensationProposal;
import bisq.core.dao.voting.ballot.vote.BooleanVote;
import bisq.core.dao.voting.ballot.vote.LongVote;
import bisq.core.dao.voting.ballot.vote.Vote;
import bisq.core.dao.voting.blindvote.BlindVote;
import bisq.core.dao.voting.blindvote.BlindVoteList;
import bisq.core.dao.voting.blindvote.BlindVoteListService;
import bisq.core.dao.voting.blindvote.BlindVoteUtils;
import bisq.core.dao.voting.blindvote.VoteWithProposalTxId;
import bisq.core.dao.voting.blindvote.VoteWithProposalTxIdList;
import bisq.core.dao.voting.voteresult.issuance.IssuanceService;
import bisq.core.dao.voting.votereveal.VoteRevealConsensus;
import bisq.core.dao.voting.votereveal.VoteRevealService;

import bisq.common.util.Utilities;

import javax.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import javax.crypto.SecretKey;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * Calculates the result of the voting at the VoteResult period.
 * We  take all data from the bitcoin domain and additionally the blindVote list which we received from the p2p network.
 * Due eventually consistency we use the hash of the data view of the voters (majority by stake). If our local
 * blindVote list contains the blindVotes used by the voters we can calculate the result, otherwise we need to request
 * the missing blindVotes from the network.
 */
@Slf4j
public class VoteResultService {
    private final VoteRevealService voteRevealService;
    private final StateService stateService;
    private final PeriodService periodService;
    private final BallotListService ballotListService;
    private final BlindVoteListService blindVoteListService;
    private final IssuanceService issuanceService;
    @Getter
    private final ObservableList<VoteResultException> voteResultExceptions = FXCollections.observableArrayList();
    // Use a list to have order by cycle
    private final List<EvaluatedProposal> allEvaluatedProposals = new ArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public VoteResultService(VoteRevealService voteRevealService,
                             StateService stateService,
                             PeriodService periodService,
                             BallotListService ballotListService,
                             BlindVoteListService blindVoteListService,
                             IssuanceService issuanceService) {
        this.voteRevealService = voteRevealService;
        this.stateService = stateService;
        this.periodService = periodService;
        this.ballotListService = ballotListService;
        this.blindVoteListService = blindVoteListService;
        this.issuanceService = issuanceService;

        stateService.addChainHeightListener(this::maybeCalculateVoteResult);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
        maybeCalculateVoteResult(stateService.getChainHeight());
    }

    public List<EvaluatedProposal> getAllAcceptedEvaluatedProposals() {
        return getAcceptedEvaluatedProposals(allEvaluatedProposals);
    }

    public List<EvaluatedProposal> getAllRejectedEvaluatedProposals() {
        return getRejectedEvaluatedProposals(allEvaluatedProposals);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void maybeCalculateVoteResult(int chainHeight) {
        if (isInVoteResultPhase(chainHeight)) {
            Set<DecryptedVote> decryptedVotes = getDecryptedVotes(chainHeight);
            if (!decryptedVotes.isEmpty()) {
                // From the decryptedVotes we create a map with the hash of the blind vote list as key and the
                // aggregated stake as value. That map is used for calculating the majority of the blind vote lists.
                // There might be conflicting versions due the eventually consistency of the P2P network (if some blind
                // votes do not arrive at all voters) which would lead to consensus failure in the result calculation.
                // To solve that problem we will only consider the majority data view as valid.
                // If multiple data views would have the same stake we sort additionally by the hex value of the
                // blind vote hash and use the first one in the sorted list as winner.
                // A node which has a local blindVote list which does not match the winner data view need to recover it's
                // local blindVote list by requesting the correct list from other peers.
                Map<byte[], Long> stakeByHashOfBlindVoteListMap = getStakeByHashOfBlindVoteListMap(decryptedVotes);

                // Get majority hash
                byte[] majorityBlindVoteListHash = getMajorityBlindVoteListHash(stakeByHashOfBlindVoteListMap);

                // Is our local list matching the majority data view?
                final boolean isBlindVoteListMatchingMajority = isBlindVoteListMatchingMajority(majorityBlindVoteListHash);

                if (isBlindVoteListMatchingMajority) {
                    //TODO should we write the decryptedVotes here into the state?

                    List<EvaluatedProposal> evaluatedProposals = getEvaluatedProposals(decryptedVotes, chainHeight);

                    applyAcceptedProposals(getAcceptedEvaluatedProposals(evaluatedProposals), chainHeight);

                    this.allEvaluatedProposals.addAll(evaluatedProposals);
                    log.info("processAllVoteResults completed");
                } else {
                    log.warn("Our list of received blind votes do not match the list from the majority of voters.");
                    // TODO request missing blind votes
                }
            } else {
                log.info("There have not been any votes in that cycle. chainHeight={}", chainHeight);
            }
        }
    }

    private Set<DecryptedVote> getDecryptedVotes(int chainHeight) {
        // We want all voteRevealTxOutputs which are in current cycle we are processing.
        return stateService.getVoteRevealOpReturnTxOutputs().stream()
                .filter(txOutput -> periodService.isTxInCorrectCycle(txOutput.getTxId(), chainHeight))
                .map(txOutput -> {
                    final byte[] opReturnData = txOutput.getOpReturnData();
                    final String voteRevealTxId = txOutput.getTxId();
                    try {
                        byte[] hashOfBlindVoteList = VoteResultConsensus.getHashOfBlindVoteList(opReturnData);
                        SecretKey secretKey = VoteResultConsensus.getSecretKey(opReturnData);
                        Tx voteRevealTx = stateService.getTx(voteRevealTxId).get();
                        TxOutput blindVoteStakeOutput = VoteResultConsensus.getConnectedBlindVoteStakeOutput(voteRevealTx, stateService);
                        long blindVoteStake = blindVoteStakeOutput.getValue();
                        Tx blindVoteTx = VoteResultConsensus.getBlindVoteTx(blindVoteStakeOutput, stateService, periodService, chainHeight);
                        String blindVoteTxId = blindVoteTx.getId();

                        // Here we deal with eventual consistency of the p2p network data!
                        Optional<BlindVote> optionalBlindVote = BlindVoteUtils.findBlindVote(blindVoteTxId, blindVoteListService.getBlindVoteList());
                        if (optionalBlindVote.isPresent()) {
                            BlindVote blindVote = optionalBlindVote.get();
                            VoteWithProposalTxIdList voteWithProposalTxIdList = VoteResultConsensus.getDecryptVotes(blindVote.getEncryptedVotes(), secretKey);

                            // We lookup for the proposals we have in our local list which match the txId from the
                            // voteWithProposalTxIdList and create a ballot list with the proposal and the vote from
                            // the voteWithProposalTxIdList
                            BallotList ballotList = createBallotList(voteWithProposalTxIdList);
                            return new DecryptedVote(hashOfBlindVoteList, voteRevealTxId, blindVoteTxId, blindVoteStake, ballotList);
                        } else {
                            log.warn("We have a blindVoteTx but we do not have the corresponding blindVote in our local list.\n" +
                                    "That can happen if the blindVote item was not properly broadcasted. We will go on " +
                                    "and see if that blindVote was part of the majority data view. If so we need to " +
                                    "recover the missing blind vote by a request to our peers. blindVoteTxId={}", blindVoteTxId);
                            return null;
                        }

                    } catch (MissingBallotException e) {
                        //TODO handle case that we are missing proposals
                        log.error("We are missing proposals to create the vote result: " + e.toString());
                        return null;
                    } catch (Throwable e) {
                        log.error("Could not create DecryptedVote: " + e.toString());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private BallotList createBallotList(VoteWithProposalTxIdList voteWithProposalTxIdList) throws MissingBallotException {
        Map<String, Vote> voteByTxIdMap = voteWithProposalTxIdList.stream()
                .collect(Collectors.toMap(VoteWithProposalTxId::getProposalTxId, VoteWithProposalTxId::getVote));

        Map<String, Ballot> ballotByTxIdMap = ballotListService.getBallotList().stream()
                .collect(Collectors.toMap(Ballot::getProposalTxId, ballot -> ballot));

        List<String> missing = new ArrayList<>();
        List<Ballot> ballots = voteByTxIdMap.entrySet().stream()
                .map(e -> {
                    final String txId = e.getKey();
                    if (ballotByTxIdMap.containsKey(txId)) {
                        final Ballot ballot = ballotByTxIdMap.get(txId);
                        return BallotFactory.getBallot(ballot.getProposal(), e.getValue());
                    } else {
                        missing.add(txId);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (!missing.isEmpty())
            throw new MissingBallotException(ballots, missing);

        return new BallotList(ballots);
    }

    private Map<byte[], Long> getStakeByHashOfBlindVoteListMap(Set<DecryptedVote> decryptedVotes) {
        Map<byte[], Long> map = new HashMap<>();
        decryptedVotes.forEach(decryptedVote -> {
            final byte[] hash = decryptedVote.getHashOfBlindVoteList();
            map.computeIfAbsent(hash, e -> 0L);
            long aggregatedStake = map.get(hash);
            aggregatedStake += decryptedVote.getStake();
            map.put(hash, aggregatedStake);
        });
        return map;
    }

    private byte[] getMajorityBlindVoteListHash(Map<byte[], Long> map) {
        List<HashWithStake> list = map.entrySet().stream()
                .map(entry -> new HashWithStake(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
        return VoteResultConsensus.getMajorityHash(list);
    }

    // Deal with eventually consistency of P2P network
    private boolean isBlindVoteListMatchingMajority(byte[] majorityVoteListHash) {
        // We reuse the method at voteReveal domain used when creating the hash
        byte[] myBlindVoteListHash = voteRevealService.getHashOfBlindVoteList();
        log.info("majorityVoteListHash " + Utilities.bytesAsHexString(majorityVoteListHash));
        log.info("myBlindVoteListHash " + Utilities.bytesAsHexString(myBlindVoteListHash));
        boolean matches = Arrays.equals(majorityVoteListHash, myBlindVoteListHash);
        if (!matches) {
            log.warn("myBlindVoteListHash does not match with majorityVoteListHash. We try permutating our list to " +
                    "find a matching variant");
            // Each voter has re-published his blind vote list when broadcasting the reveal tx so it should have a very
            // high change that we have received all blind votes which have been used by the majority of the
            // voters (e.g. its stake not nr. of voters).
            // It still could be that we have additional blind votes so our hash does not match. We can try to permute
            // our list with excluding items to see if we get a matching list. If not last resort is to request the
            // missing items from the network.
            BlindVoteList permutatedListMatchingMajority = findPermutatedListMatchingMajority(majorityVoteListHash);
            if (!permutatedListMatchingMajority.isEmpty()) {
                log.info("We found a permutation of our blindVote list which matches the majority view. " +
                        "permutatedListMatchingMajority={}", permutatedListMatchingMajority);
                //TODO do we need to apply/store it for later use?
            } else {
                log.info("We did not find a permutation of our blindVote list which matches the majority view. " +
                        "We will request the blindVote data from the peers.");
                // This is async operation. We will restart the whole verification process once we received the data.
                requestBlindVoteListFromNetwork(majorityVoteListHash);
            }
        }
        return matches;
    }

    private BlindVoteList findPermutatedListMatchingMajority(byte[] majorityVoteListHash) {
        final BlindVoteList clonedBlindVoteList = new BlindVoteList(blindVoteListService.getBlindVoteList().getList());
        BlindVoteList list = voteRevealService.getSortedBlindVoteListOfCycle(clonedBlindVoteList);
        while (!list.isEmpty() && !isListMatchingMajority(majorityVoteListHash, list)) {
            // We remove first item as it will be sorted anyway...
            list.remove(0);
        }
        return list;
    }

    private boolean isListMatchingMajority(byte[] majorityVoteListHash, BlindVoteList list) {
        byte[] myBlindVoteListHash = VoteRevealConsensus.getHashOfBlindVoteList(list);
        return Arrays.equals(majorityVoteListHash, myBlindVoteListHash);
    }

    private void requestBlindVoteListFromNetwork(byte[] majorityVoteListHash) {
        //TODO impl
    }

    private List<EvaluatedProposal> getEvaluatedProposals(Set<DecryptedVote> decryptedVotes,
                                                          int chainHeight) {
        // We reorganize the data structure to have a map of proposals with a list of VoteWithStake objects
        Map<Proposal, List<VoteWithStake>> resultListByProposalMap = getVoteWithStakeListByProposalMap(decryptedVotes);
        List<EvaluatedProposal> evaluatedProposals = new ArrayList<>();
        resultListByProposalMap.forEach((proposal, voteWithStakeList) -> {
            long requiredQuorum = stateService.getParamValue(proposal.getQuorumParam(), chainHeight);
            long requiredVoteThreshold = stateService.getParamValue(proposal.getThresholdParam(), chainHeight);

            ProposalVoteResult proposalVoteResult = getResultPerProposal(voteWithStakeList, proposal);
            long totalStake = proposalVoteResult.getStakeOfAcceptedVotes() + proposalVoteResult.getStakeOfRejectedVotes();

            log.info("totalStake: {}, required requiredQuorum: {}", totalStake, requiredQuorum);
            if (totalStake >= requiredQuorum) {
                long reachedThreshold = proposalVoteResult.getStakeOfAcceptedVotes() / totalStake;
                // We multiply by 10000 as we use a long for requiredVoteThreshold and we want precision of 2 with
                // a % value. E.g. 50% is 50.00. We represent 1.0000 for 100%, so 10000 is the long value to reach the
                // required precision.
                reachedThreshold *= 10_000;

                log.info("reached threshold: {} %, required threshold: {} %", reachedThreshold / 100D, requiredVoteThreshold / 100D);
                // We need to exceed requiredVoteThreshold e.g. 50% is not enough but 50.01%
                if (reachedThreshold > requiredVoteThreshold) {
                    evaluatedProposals.add(new EvaluatedProposal(true, proposalVoteResult, requiredQuorum, requiredVoteThreshold));
                } else {
                    evaluatedProposals.add(new EvaluatedProposal(false, proposalVoteResult, requiredQuorum, requiredVoteThreshold));
                    log.warn("Proposal did not reach the requiredVoteThreshold. reachedThreshold={} %, " +
                            "requiredVoteThreshold={} %", reachedThreshold / 100D, requiredVoteThreshold / 100D);
                }
            } else {
                evaluatedProposals.add(new EvaluatedProposal(false, proposalVoteResult, requiredQuorum, requiredVoteThreshold));
                log.warn("Proposal did not reach the requiredQuorum. totalStake={}, requiredQuorum={}", totalStake, requiredQuorum);
            }
        });
        return evaluatedProposals;
    }

    private Map<Proposal, List<VoteWithStake>> getVoteWithStakeListByProposalMap(Set<DecryptedVote> decryptedVotes) {
        Map<Proposal, List<VoteWithStake>> voteWithStakeByProposalMap = new HashMap<>();
        decryptedVotes.forEach(decryptedVote -> {
            iterateProposals(voteWithStakeByProposalMap, decryptedVote);
        });
        return voteWithStakeByProposalMap;
    }

    private void iterateProposals(Map<Proposal, List<VoteWithStake>> voteWithStakeByProposalMap, DecryptedVote decryptedVote) {
        decryptedVote.getBallotList()
                .forEach(ballot -> {
                    final Proposal proposal = ballot.getProposal();
                    voteWithStakeByProposalMap.putIfAbsent(proposal, new ArrayList<>());
                    final List<VoteWithStake> voteWithStakeList = voteWithStakeByProposalMap.get(proposal);
                    voteWithStakeList.add(new VoteWithStake(ballot.getVote(), decryptedVote.getStake()));
                });
    }

    private ProposalVoteResult getResultPerProposal(List<VoteWithStake> voteWithStakeList, Proposal proposal) {
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
        return new ProposalVoteResult(proposal, stakeOfAcceptedVotes, stakeOfRejectedVotes);
    }

    private void applyAcceptedProposals(List<EvaluatedProposal> evaluatedProposals, int chainHeight) {
        evaluatedProposals.forEach(p -> applyAcceptedProposal(p.getProposal(), chainHeight));
    }

    private void applyAcceptedProposal(Proposal proposal, int chainHeight) {
        if (proposal instanceof CompensationProposal) {
            issuanceService.issueBsq((CompensationProposal) proposal, chainHeight);
        } /*else if (evaluatedProposal instanceof GenericProposal) {
            //TODO impl
        } else if (evaluatedProposal instanceof ParamChangeProposal) {
            //TODO impl -> add to state
        } else if (evaluatedProposal instanceof RemoveAssetProposalPayload) {
            //TODO impl
        }*/
    }

    private List<EvaluatedProposal> getAcceptedEvaluatedProposals(List<EvaluatedProposal> evaluatedProposals) {
        return evaluatedProposals.stream()
                .filter(EvaluatedProposal::isAccepted)
                .collect(Collectors.toList());
    }

    private List<EvaluatedProposal> getRejectedEvaluatedProposals(List<EvaluatedProposal> evaluatedProposals) {
        return evaluatedProposals.stream()
                .filter(evaluatedProposal -> !evaluatedProposal.isAccepted())
                .collect(Collectors.toList());
    }

    private boolean isInVoteResultPhase(int chainHeight) {
        return periodService.getFirstBlockOfPhase(chainHeight, DaoPhase.Phase.RESULT) == chainHeight;
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
