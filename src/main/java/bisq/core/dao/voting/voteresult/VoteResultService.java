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

import bisq.core.dao.state.BsqStateListener;
import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.state.blockchain.Block;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.ext.ParamChange;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.state.period.PeriodService;
import bisq.core.dao.voting.ballot.Ballot;
import bisq.core.dao.voting.ballot.BallotList;
import bisq.core.dao.voting.ballot.BallotListService;
import bisq.core.dao.voting.ballot.vote.BooleanVote;
import bisq.core.dao.voting.ballot.vote.Vote;
import bisq.core.dao.voting.blindvote.BlindVote;
import bisq.core.dao.voting.blindvote.BlindVoteConsensus;
import bisq.core.dao.voting.blindvote.BlindVoteService;
import bisq.core.dao.voting.blindvote.BlindVoteUtils;
import bisq.core.dao.voting.blindvote.BlindVoteValidator;
import bisq.core.dao.voting.blindvote.MyBlindVoteList;
import bisq.core.dao.voting.blindvote.VoteWithProposalTxId;
import bisq.core.dao.voting.blindvote.VoteWithProposalTxIdList;
import bisq.core.dao.voting.merit.MeritList;
import bisq.core.dao.voting.proposal.FilteredProposalListService;
import bisq.core.dao.voting.proposal.Proposal;
import bisq.core.dao.voting.proposal.compensation.CompensationProposal;
import bisq.core.dao.voting.proposal.param.ChangeParamProposal;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
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
public class VoteResultService implements BsqStateListener {
    private final VoteRevealService voteRevealService;
    private final FilteredProposalListService filteredProposalListService;
    private final BsqStateService bsqStateService;
    private final PeriodService periodService;
    private final BallotListService ballotListService;
    private final BlindVoteService blindVoteService;
    private final BlindVoteValidator blindVoteValidator;
    private final IssuanceService issuanceService;
    @Getter
    private final ObservableList<VoteResultException> voteResultExceptions = FXCollections.observableArrayList();
    // Use a list to have order by cycle

    // TODO persist, PB
    @Getter
    private final List<EvaluatedProposal> allEvaluatedProposals = new ArrayList<>();
    @Getter
    private final List<DecryptedVote> allDecryptedVotes = new ArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public VoteResultService(VoteRevealService voteRevealService,
                             FilteredProposalListService filteredProposalListService,
                             BsqStateService bsqStateService,
                             PeriodService periodService,
                             BallotListService ballotListService,
                             BlindVoteService blindVoteService,
                             BlindVoteValidator blindVoteValidator,
                             IssuanceService issuanceService) {
        this.voteRevealService = voteRevealService;
        this.filteredProposalListService = filteredProposalListService;
        this.bsqStateService = bsqStateService;
        this.periodService = periodService;
        this.ballotListService = ballotListService;
        this.blindVoteService = blindVoteService;
        this.blindVoteValidator = blindVoteValidator;
        this.issuanceService = issuanceService;

        bsqStateService.addBsqStateListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
        maybeCalculateVoteResult(bsqStateService.getChainHeight());
    }

    public List<EvaluatedProposal> getAllAcceptedEvaluatedProposals() {
        return getAcceptedEvaluatedProposals(allEvaluatedProposals);
    }

    public List<EvaluatedProposal> getAllRejectedEvaluatedProposals() {
        return getRejectedEvaluatedProposals(allEvaluatedProposals);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BsqStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onNewBlockHeight(int blockHeight) {
        // TODO check if we should use onParseTxsComplete for calling maybeCalculateVoteResult
        maybeCalculateVoteResult(blockHeight);
    }

    @Override
    public void onEmptyBlockAdded(Block block) {
    }

    @Override
    public void onParseTxsComplete(Block block) {
    }

    @Override
    public void onParseBlockChainComplete() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void maybeCalculateVoteResult(int chainHeight) {
        if (isInVoteResultPhase(chainHeight)) {
            Set<DecryptedVote> decryptedVotes = getDecryptedVotes(chainHeight);
            allDecryptedVotes.addAll(decryptedVotes);

            List<Proposal> proposals = new ArrayList<>(filteredProposalListService.getActiveOrMyUnconfirmedProposals());

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
                if (isBlindVoteListMatchingMajority(majorityBlindVoteListHash)) {
                    //TODO should we write the decryptedVotes here into the state?

                    List<EvaluatedProposal> evaluatedProposals = getEvaluatedProposals(decryptedVotes, chainHeight);
                    List<EvaluatedProposal> acceptedEvaluatedProposals = getAcceptedEvaluatedProposals(evaluatedProposals);
                    applyAcceptedProposals(acceptedEvaluatedProposals, chainHeight);

                    allEvaluatedProposals.addAll(evaluatedProposals);
                    log.info("processAllVoteResults completed");
                } else {
                    log.warn("Our list of received blind votes do not match the list from the majority of voters.");
                    // TODO request missing blind votes
                }
            } else {
                log.info("There have not been any votes in that cycle. chainHeight={}", chainHeight);
            }

            // Those which did not get accepted will be added to the nonBsq map
            bsqStateService.getIssuanceCandidateTxOutputs().stream()
                    .filter(txOutput -> !bsqStateService.isIssuanceTx(txOutput.getTxId()))
                    .forEach(bsqStateService::addNonBsqTxOutput);
        }
    }

    private Set<DecryptedVote> getDecryptedVotes(int chainHeight) {
        // We want all voteRevealTxOutputs which are in current cycle we are processing.
        return bsqStateService.getVoteRevealOpReturnTxOutputs().stream()
                .filter(txOutput -> periodService.isTxInCorrectCycle(txOutput.getTxId(), chainHeight))
                .map(txOutput -> {
                    final byte[] opReturnData = txOutput.getOpReturnData();
                    final String voteRevealTxId = txOutput.getTxId();
                    Optional<Tx> optionalVoteRevealTx = bsqStateService.getTx(voteRevealTxId);
                    if (!optionalVoteRevealTx.isPresent()) {
                        log.error("optionalVoteRevealTx is not present. voteRevealTxId={}", voteRevealTxId);
                        return null;
                    }

                    Tx voteRevealTx = optionalVoteRevealTx.get();
                    try {
                        byte[] hashOfBlindVoteList = VoteResultConsensus.getHashOfBlindVoteList(opReturnData);
                        SecretKey secretKey = VoteResultConsensus.getSecretKey(opReturnData);
                        TxOutput blindVoteStakeOutput = VoteResultConsensus.getConnectedBlindVoteStakeOutput(voteRevealTx, bsqStateService);
                        long blindVoteStake = blindVoteStakeOutput.getValue();
                        Tx blindVoteTx = VoteResultConsensus.getBlindVoteTx(blindVoteStakeOutput, bsqStateService, periodService, chainHeight);
                        String blindVoteTxId = blindVoteTx.getId();

                        // Here we deal with eventual consistency of the p2p network data!
                        MyBlindVoteList blindVoteList = BlindVoteConsensus.getSortedBlindVoteListOfCycle(blindVoteService, blindVoteValidator);
                        Optional<BlindVote> optionalBlindVote = BlindVoteUtils.findBlindVote(blindVoteTxId, blindVoteList);
                        if (optionalBlindVote.isPresent()) {
                            BlindVote blindVote = optionalBlindVote.get();
                            VoteWithProposalTxIdList voteWithProposalTxIdList = VoteResultConsensus.getDecryptedVotes(blindVote.getEncryptedVotes(), secretKey);
                            MeritList meritList = VoteResultConsensus.getDecryptMeritList(blindVote.getEncryptedMeritList(), secretKey);

                            // We lookup for the proposals we have in our local list which match the txId from the
                            // voteWithProposalTxIdList and create a ballot list with the proposal and the vote from
                            // the voteWithProposalTxIdList
                            BallotList ballotList = createBallotList(voteWithProposalTxIdList);
                            return new DecryptedVote(hashOfBlindVoteList, voteRevealTxId, blindVoteTxId, blindVoteStake, ballotList, meritList);
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
                .filter(voteWithProposalTxId -> voteWithProposalTxId.getVote() != null)
                .collect(Collectors.toMap(VoteWithProposalTxId::getProposalTxId, VoteWithProposalTxId::getVote));

        Map<String, Ballot> ballotByTxIdMap = ballotListService.getBallotList().stream()
                .collect(Collectors.toMap(Ballot::getProposalTxId, ballot -> ballot));

        List<String> missing = new ArrayList<>();
        List<Ballot> ballots = voteByTxIdMap.entrySet().stream()
                .map(e -> {
                    final String txId = e.getKey();
                    if (ballotByTxIdMap.containsKey(txId)) {
                        final Ballot ballot = ballotByTxIdMap.get(txId);
                        // We apply the vote from our decrypted votes
                        Vote vote = e.getValue();
                        return new Ballot(ballot.getProposal(), vote);
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
            map.putIfAbsent(hash, 0L);
            long aggregatedStake = map.get(hash);
            long meritStake = decryptedVote.getMerit(bsqStateService);
            long stake = decryptedVote.getStake();
            long combinedStake = stake + meritStake;
            log.debug("blindVoteTxId={}, meritStake={}, stake={}, combinedStake={}",
                    decryptedVote.getBlindVoteTxId(), meritStake, stake, combinedStake);
            aggregatedStake += combinedStake;
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
            log.warn("myBlindVoteListHash does not match with majorityVoteListHash. We try permuting our list to " +
                    "find a matching variant");
            // Each voter has re-published his blind vote list when broadcasting the reveal tx so it should have a very
            // high change that we have received all blind votes which have been used by the majority of the
            // voters (e.g. its stake not nr. of voters).
            // It still could be that we have additional blind votes so our hash does not match. We can try to permute
            // our list with excluding items to see if we get a matching list. If not last resort is to request the
            // missing items from the network.
            MyBlindVoteList permutatedListMatchingMajority = findPermutatedListMatchingMajority(majorityVoteListHash);
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

    private MyBlindVoteList findPermutatedListMatchingMajority(byte[] majorityVoteListHash) {
        MyBlindVoteList list = BlindVoteConsensus.getSortedBlindVoteListOfCycle(blindVoteService, blindVoteValidator);
        while (!list.isEmpty() && !isListMatchingMajority(majorityVoteListHash, list)) {
            // We remove first item as it will be sorted anyway...
            list.remove(0);
        }
        return list;
    }

    private boolean isListMatchingMajority(byte[] majorityVoteListHash, MyBlindVoteList list) {
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
        AtomicLong defaultRequiredQuorum = new AtomicLong(0);
        AtomicLong defaultRequiredVoteThreshold = new AtomicLong(0);

        resultListByProposalMap.forEach((proposal, voteWithStakeList) -> {
            long requiredQuorum = bsqStateService.getParamValue(proposal.getQuorumParam(), chainHeight);
            long requiredVoteThreshold = bsqStateService.getParamValue(proposal.getThresholdParam(), chainHeight);
            defaultRequiredQuorum.set(requiredQuorum);
            defaultRequiredVoteThreshold.set(requiredQuorum);

            ProposalVoteResult proposalVoteResult = getResultPerProposal(voteWithStakeList, proposal);
            long reachedQuorum = proposalVoteResult.getQuorum();
            log.info("proposalTxId: {}, required requiredQuorum: {}, requiredVoteThreshold: {}",
                    proposal.getTxId(), requiredVoteThreshold, requiredQuorum);
            if (reachedQuorum >= requiredQuorum) {
                // We multiply by 10000 as we use a long for reachedThreshold and we want precision of 2 with
                // a % value. E.g. 50% is 50.00. We represent 1.0000 for 100%, so 10000 is the long value to reach the
                // required precision.
                long reachedThreshold = proposalVoteResult.getThreshold();

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
                log.warn("Proposal did not reach the requiredQuorum. reachedQuorum={}, requiredQuorum={}", reachedQuorum, requiredQuorum);
            }
        });

        Map<String, EvaluatedProposal> lookupMap = new HashMap<>();
        evaluatedProposals.forEach(evaluatedProposal -> lookupMap.put(evaluatedProposal.getProposalTxId(), evaluatedProposal));

        filteredProposalListService.getActiveOrMyUnconfirmedProposals().stream()
                .filter(proposal -> !lookupMap.containsKey(proposal.getTxId()))
                .forEach(proposal -> {
                    ProposalVoteResult proposalVoteResult = new ProposalVoteResult(proposal, 0, 0, 0, 0, decryptedVotes.size());
                    EvaluatedProposal evaluatedProposal = new EvaluatedProposal(false,
                            proposalVoteResult,
                            defaultRequiredQuorum.get(), defaultRequiredVoteThreshold.get());
                    evaluatedProposals.add(evaluatedProposal);
                    log.info("Proposal ignored by all voters: " + evaluatedProposal);
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
                    voteWithStakeList.add(new VoteWithStake(ballot.getVote(), decryptedVote.getStake(), decryptedVote.getMeritList(), decryptedVote.getBlindVoteTxId()));
                });
    }

    private ProposalVoteResult getResultPerProposal(List<VoteWithStake> voteWithStakeList, Proposal proposal) {
        int numAcceptedVotes = 0;
        int numRejectedVotes = 0;
        int numIgnoredVotes = 0;
        long stakeOfAcceptedVotes = 0;
        long stakeOfRejectedVotes = 0;

        for (VoteWithStake voteWithStake : voteWithStakeList) {
            String blindVoteTxId = voteWithStake.getBlindVoteTxId();
            MeritList meritList = voteWithStake.getMeritList();
            long meritStake = VoteResultConsensus.getMeritStake(blindVoteTxId, meritList, bsqStateService);
            long stake = voteWithStake.getStake();
            long combinedStake = stake + meritStake;
            log.info("proposalTxId={}, stake={}, meritStake={}, combinedStake={}",
                    proposal.getTxId(), stake, meritStake, combinedStake);
            Vote vote = voteWithStake.getVote();
            if (vote != null) {
                if (vote instanceof BooleanVote) {
                    BooleanVote result = (BooleanVote) vote;
                    if (result.isAccepted()) {
                        stakeOfAcceptedVotes += combinedStake;
                        numAcceptedVotes++;
                    } else {
                        stakeOfRejectedVotes += combinedStake;
                        numRejectedVotes++;
                    }
                } /*else if (vote instanceof LongVote) {
                    //not used yet
                }*/
            } else {
                numIgnoredVotes++;
                log.debug("Voter ignored proposal");
            }
        }
        return new ProposalVoteResult(proposal, stakeOfAcceptedVotes, stakeOfRejectedVotes, numAcceptedVotes, numRejectedVotes, numIgnoredVotes);
    }

    private void applyAcceptedProposals(List<EvaluatedProposal> evaluatedProposals, int chainHeight) {
        evaluatedProposals.stream()
                .map(EvaluatedProposal::getProposal)
                .filter(proposal -> proposal instanceof CompensationProposal)
                .forEach(proposal -> issuanceService.issueBsq((CompensationProposal) proposal, chainHeight));

        Map<String, List<EvaluatedProposal>> evaluatedProposalsByParam = new HashMap<>();
        evaluatedProposals.forEach(evaluatedProposal -> {
            if (evaluatedProposal.getProposal() instanceof ChangeParamProposal) {
                ChangeParamProposal changeParamProposal = (ChangeParamProposal) evaluatedProposal.getProposal();
                ParamChange paramChange = getParamChange(changeParamProposal, chainHeight);
                if (paramChange != null) {
                    String key = paramChange.getParamName();
                    evaluatedProposalsByParam.putIfAbsent(key, new ArrayList<>());
                    evaluatedProposalsByParam.get(key).add(evaluatedProposal);
                }
            }
        });

        evaluatedProposalsByParam.forEach((key, list) -> {
            if (list.size() == 1) {
                applyAcceptedChangeParamProposal((ChangeParamProposal) list.get(0).getProposal(), chainHeight);
            } else if (list.size() > 1) {
                // We got multiple proposals for the same parameter. We check which one got the higher stake and that
                // one will be the winner. If both have same stake none will be the winner.
                list.sort(Comparator.comparing(ev -> ev.getProposalVoteResult().getStakeOfAcceptedVotes()));
                Collections.reverse(list);
                EvaluatedProposal first = list.get(0);
                EvaluatedProposal second = list.get(1);
                if (first.getProposalVoteResult().getStakeOfAcceptedVotes() >
                        second.getProposalVoteResult().getStakeOfAcceptedVotes()) {
                    applyAcceptedChangeParamProposal((ChangeParamProposal) first.getProposal(), chainHeight);
                } else {
                    // Rare case that both have the same stake. We don't need to check for a third entry as if 2 have
                    // the same we are already in the abort case to reject all proposals with that param
                    log.warn("We got the rare case that multiple changeParamProposals have received the same stake. " +
                            "None will be accepted in such a case.\n" +
                            "EvaluatedProposal={}", list);
                }
            }
        });
    }

    private void applyAcceptedChangeParamProposal(ChangeParamProposal changeParamProposal, int chainHeight) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n################################################################################\n");
        sb.append("We changed a parameter. ProposalTxId=").append(changeParamProposal.getTxId())
                .append("\nfor changeParamProposal with UID ").append(changeParamProposal.getUid())
                .append("\nParam: ").append(changeParamProposal.getParam().name())
                .append(" new value: ").append(changeParamProposal.getParamValue())
                .append("\n################################################################################\n");
        log.info(sb.toString());

        bsqStateService.setNewParam(chainHeight, changeParamProposal.getParam(),
                changeParamProposal.getParamValue());
    }

    private ParamChange getParamChange(ChangeParamProposal changeParamProposal, int chainHeight) {
        return bsqStateService.getStartHeightOfNextCycle(chainHeight)
                .map(heightOfNewCycle -> new ParamChange(changeParamProposal.getParam().name(),
                        changeParamProposal.getParamValue(), heightOfNewCycle))
                .orElse(null);
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
        private final MeritList meritList;
        private final String blindVoteTxId;

        VoteWithStake(@Nullable Vote vote, long stake, MeritList meritList, String blindVoteTxId) {
            this.vote = vote;
            this.stake = stake;
            this.meritList = meritList;
            this.blindVoteTxId = blindVoteTxId;
        }
    }
}
