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

import bisq.core.dao.blockchain.BsqBlockChain;
import bisq.core.dao.blockchain.ReadableBsqBlockChain;
import bisq.core.dao.blockchain.vo.BsqBlock;
import bisq.core.dao.blockchain.vo.Tx;
import bisq.core.dao.blockchain.vo.TxInput;
import bisq.core.dao.blockchain.vo.TxOutput;
import bisq.core.dao.blockchain.vo.TxOutputType;
import bisq.core.dao.blockchain.vo.TxType;
import bisq.core.dao.param.DaoParamService;
import bisq.core.dao.vote.BooleanVote;
import bisq.core.dao.vote.LongVote;
import bisq.core.dao.vote.PeriodService;
import bisq.core.dao.vote.Vote;
import bisq.core.dao.vote.blindvote.BlindVote;
import bisq.core.dao.vote.blindvote.BlindVoteList;
import bisq.core.dao.vote.blindvote.BlindVoteService;
import bisq.core.dao.vote.proposal.ProposalList;
import bisq.core.dao.vote.proposal.ProposalPayload;
import bisq.core.dao.vote.proposal.asset.RemoveAssetProposalPayload;
import bisq.core.dao.vote.proposal.compensation.CompensationRequestPayload;
import bisq.core.dao.vote.proposal.generic.GenericProposalPayload;
import bisq.core.dao.vote.proposal.param.ChangeParamProposalPayload;
import bisq.core.dao.vote.voteresult.issuance.IssuanceService;
import bisq.core.dao.vote.votereveal.RevealedVote;
import bisq.core.dao.vote.votereveal.VoteRevealConsensus;
import bisq.core.dao.vote.votereveal.VoteRevealService;

import bisq.common.crypto.CryptoException;
import bisq.common.util.Utilities;

import com.google.protobuf.InvalidProtocolBufferException;

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

//TODO case that user misses reveal phase not impl. yet

@Slf4j
public class VoteResultService implements BsqBlockChain.Listener {
    private final BlindVoteService blindVoteService;
    private final VoteRevealService voteRevealService;
    private final ReadableBsqBlockChain readableBsqBlockChain;
    private final DaoParamService daoParamService;
    private final PeriodService periodService;
    private final IssuanceService issuanceService;
    @Getter
    private final ObservableList<VoteResultException> voteResultExceptions = FXCollections.observableArrayList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public VoteResultService(BlindVoteService blindVoteService,
                             VoteRevealService voteRevealService,
                             ReadableBsqBlockChain readableBsqBlockChain,
                             DaoParamService daoParamService,
                             PeriodService periodService,
                             IssuanceService issuanceService) {
        this.blindVoteService = blindVoteService;
        this.voteRevealService = voteRevealService;
        this.readableBsqBlockChain = readableBsqBlockChain;
        this.daoParamService = daoParamService;
        this.periodService = periodService;
        this.issuanceService = issuanceService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        readableBsqBlockChain.addListener(this);
    }

    public void shutDown() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BsqBlockChain.Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onBlockAdded(BsqBlock bsqBlock) {
        maybeApplyVoteResult(bsqBlock.getHeight());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void maybeApplyVoteResult(int chainHeight) {
        if (periodService.getPhaseForHeight(chainHeight) == PeriodService.Phase.ISSUANCE) {
            applyVoteResult(chainHeight);
        }
    }

    private void applyVoteResult(int chainHeight) {
        // We make a map with txIds of VoteReveal TxOutputs as key and the opReturn data as value (containing secret key
        // and hash of proposal list)
        Map<String, byte[]> opReturnByVoteRevealTxIdMap = getOpReturnByTxIdMap(chainHeight);

        if (!opReturnByVoteRevealTxIdMap.isEmpty()) {
            // From the opReturnByVoteRevealTxIdMap we create a map with the hash of the blind vote list as key and the
            // collected stake as value. That map is used for calculating the majority of the blind vote lists.
            // If there are conflicting versions due the eventually consistency of the P2P network (it might be
            // that some blind votes do not arrive at all voters which would lead to conflicts in the result calculation).
            // To solve that problem we will only consider the majority data view as valid.
            // In case 2 data views would have the same stake we sort both by their hex value and use the first one
            // in the sorted list.
            Map<byte[], Long> stakeByVoteListHash = getStakeByVoteListHashMap(opReturnByVoteRevealTxIdMap);

            // We make a map with the voteReveal TxId as key and the secret key decoded from the opReturn data (the
            // voter has revealed the secret key there).
            Map<String, SecretKey> secretKeysByTxIdMap = getSecretsKeyByTxIdMap(opReturnByVoteRevealTxIdMap);

            // We make a set of BlindVoteWithRevealTxId objects. BlindVoteWithRevealTxId holds the blind vote with
            // the reveal tx ID. The stake output of the blind vote tx is used as the input for the reveal tx,
            // this is used to connect those transactions.
            Set<BlindVoteWithRevealTxId> blindVoteWithRevealTxIdSet = getBlindVoteWithRevealTxIdSet(chainHeight);

            // We have now all data prepared required to get the decrypted vote data so we can calculate the result
            Set<RevealedVote> revealedVotes = getRevealedVotes(blindVoteWithRevealTxIdSet, secretKeysByTxIdMap);

            byte[] majorityVoteListHash = getMajorityVoteListHashByTxIdMap(stakeByVoteListHash);
            if (majorityVoteListHash != null) {
                if (isBlindVoteListMatchingMajority(majorityVoteListHash, chainHeight)) {
                    Map<ProposalPayload, List<VoteWithStake>> resultListByProposalPayloadMap = getResultListByProposalPayloadMap(revealedVotes);
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

    private Map<String, byte[]> getOpReturnByTxIdMap(int chainHeight) {
        Map<String, byte[]> opReturnHashesByTxIdMap = new HashMap<>();
        // We want all voteRevealTxOutputs which are in current cycle we are processing.
        final Set<TxOutput> voteRevealTxOutputs = readableBsqBlockChain.getVoteRevealTxOutputs();
        if (voteRevealTxOutputs.isEmpty())
            log.warn("voteRevealTxOutputs is empty");

        voteRevealTxOutputs.stream()
                .filter(txOutput -> periodService.isTxInCorrectCycle(txOutput.getTxId(), chainHeight))
                .forEach(txOutput -> opReturnHashesByTxIdMap.put(txOutput.getTxId(), txOutput.getOpReturnData()));
        return opReturnHashesByTxIdMap;
    }

    private Map<byte[], Long> getStakeByVoteListHashMap(Map<String, byte[]> opReturnMap) {
        Map<byte[], Long> map = new HashMap<>();
        opReturnMap.forEach((txId, data) -> {
            final byte[] hash = VoteResultConsensus.getBlindVoteListHash(data);
            map.computeIfAbsent(hash, e -> 0L);
            Long aggregatedStake = map.get(hash);
            Optional<Tx> optionalTx = readableBsqBlockChain.getTx(txId);
            if (optionalTx.isPresent()) {
                Tx voteRevealTx = optionalTx.get();
                // We use the stake output of the blind vote tx as first input
                final TxInput txInput = voteRevealTx.getInputs().get(0);
                final TxOutput blindVoteStakeOutput = txInput.getConnectedTxOutput();
                if (blindVoteStakeOutput != null) {
                    long stake = blindVoteStakeOutput.getValue();
                    aggregatedStake += stake;
                    map.put(hash, aggregatedStake);
                }
            }
        });
        return map;
    }

    private Map<String, SecretKey> getSecretsKeyByTxIdMap(Map<String, byte[]> opReturnMap) {
        Map<String, SecretKey> map = new HashMap<>();
        opReturnMap.forEach((key, value) -> map.put(key, VoteResultConsensus.getSecretKey(value)));
        return map;
    }

    private Set<BlindVoteWithRevealTxId> getBlindVoteWithRevealTxIdSet(int chainHeight) {
        return blindVoteService.getValidBlindVotes().stream()
                .filter(blindVote -> periodService.isTxInCorrectCycle(blindVote.getTxId(), chainHeight))
                .map(blindVote -> {
                    return readableBsqBlockChain.getTx(blindVote.getTxId())
                            .filter(blindVoteTx -> blindVoteTx.getTxType() == TxType.BLIND_VOTE) // double check if type is matching
                            .map(blindVoteTx -> blindVoteTx.getTxOutput(0)) // stake need to be output 0
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .filter(stakeTxOutput -> stakeTxOutput.getTxOutputType() == TxOutputType.BLIND_VOTE_LOCK_STAKE_OUTPUT) // double check if type is matching
                            .map(TxOutput::getTxId)
                            .map(blindVoteTxId -> getRevealTxIdForBlindVoteTx(blindVoteTxId, chainHeight))
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .map(revealTxId -> new BlindVoteWithRevealTxId(blindVote, revealTxId))
                            .orElse(null);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    // TODO we could make that easier if we would write the reveal tx outputs to readableBsqBlockChain at parsing time
    // Finds txId of voteReveal tx which has its first input connected to the blind vote tx's first output for
    // transferring the stake.
    private Optional<String> getRevealTxIdForBlindVoteTx(String blindVoteTxId, int chainHeight) {
        Optional<String> optionalBlindVoteStakeTxOutputId = readableBsqBlockChain.getTx(blindVoteTxId)
                .map(Tx::getOutputs)
                .map(outputs -> outputs.get(0))
                .map(TxOutput::getId);

        if (optionalBlindVoteStakeTxOutputId.isPresent()) {
            String blindVoteStakeTxOutputId = optionalBlindVoteStakeTxOutputId.get();
            return readableBsqBlockChain.getTxMap().values().stream()
                    .filter(tx -> tx.getTxType() == TxType.VOTE_REVEAL)
                    .filter(tx -> periodService.isTxInCorrectCycle(tx.getId(), chainHeight))
                    .map(tx -> {
                        TxOutput connectedTxOutput = tx.getInputs().get(0).getConnectedTxOutput();
                        if (connectedTxOutput != null) {
                            if (connectedTxOutput.getId().equals(blindVoteStakeTxOutputId))
                                return tx.getId();
                            else
                                log.warn("connectedTxOutput is null.");
                        } else {
                            log.warn("connectedTxOutput is null.");
                        }
                        return null;
                    })
                    .filter(Objects::nonNull)
                    .findAny();
        } else {
            return Optional.empty();
        }
    }

    private Set<RevealedVote> getRevealedVotes(Set<BlindVoteWithRevealTxId> blindVoteWithRevealTxIdSet,
                                               Map<String, SecretKey> secretKeysByTxIdMap) {
        return blindVoteWithRevealTxIdSet.stream()
                .map(blindVoteWithRevealTxId -> {
                    final BlindVote blindVote = blindVoteWithRevealTxId.getBlindVote();
                    final byte[] encryptedProposalList = blindVote.getEncryptedProposalList();
                    final String revealTxId = blindVoteWithRevealTxId.getRevealTxId();
                    if (secretKeysByTxIdMap.containsKey(revealTxId)) {
                        final SecretKey secretKey = secretKeysByTxIdMap.get(revealTxId);
                        try {
                            final byte[] decrypted = VoteResultConsensus.decryptProposalList(encryptedProposalList, secretKey);
                            ProposalList proposalList = ProposalList.parseProposalList(decrypted);
                            return new RevealedVote(proposalList, blindVote, revealTxId);
                        } catch (CryptoException | InvalidProtocolBufferException e) {
                            log.error(e.toString());
                            voteResultExceptions.add(new VoteResultException("Error at getRevealedVotes", e, blindVote));
                            return null;
                        }
                    } else {
                        log.warn("We don't have the secret key in our secretKeysByTxIdMap");
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    @Nullable
    private byte[] getMajorityVoteListHashByTxIdMap(Map<byte[], Long> stakeByVoteListHash) {
        List<HashWithTxIdList> list = new ArrayList<>();
        stakeByVoteListHash.forEach((key, value) -> list.add(new HashWithTxIdList(key, value)));
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

    private Map<ProposalPayload, List<VoteWithStake>> getResultListByProposalPayloadMap(Set<RevealedVote> revealedVotes) {
        Map<ProposalPayload, List<VoteWithStake>> stakeByProposalMap = new HashMap<>();
        revealedVotes.forEach(revealedVote -> iterateProposals(stakeByProposalMap, revealedVote));
        return stakeByProposalMap;
    }

    private void iterateProposals(Map<ProposalPayload, List<VoteWithStake>> stakeByProposalMap, RevealedVote revealedVote) {
        revealedVote.getProposalList().getList()
                .forEach(proposal -> {
                    final ProposalPayload proposalPayload = proposal.getProposalPayload();
                    stakeByProposalMap.putIfAbsent(proposalPayload, new ArrayList<>());
                    final List<VoteWithStake> voteWithStakes = stakeByProposalMap.get(proposalPayload);
                    voteWithStakes.add(new VoteWithStake(proposal.getVote(), revealedVote.getStake()));
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

    private ResultPerProposal getResultPerProposal(List<VoteWithStake> voteResultsWithStake) {
        long stakeOfAcceptedVotes = 0;
        long stakeOfRejectedVotes = 0;
        for (VoteWithStake voteWithStake : voteResultsWithStake) {
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Inner classes
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Value
    private class BlindVoteWithRevealTxId {
        private final BlindVote blindVote;
        private final String revealTxId;

        BlindVoteWithRevealTxId(BlindVote blindVote, String revealTxId) {
            this.blindVote = blindVote;
            this.revealTxId = revealTxId;
        }
    }

    @Value
    public class HashWithTxIdList {
        private final byte[] hashOfProposalList;
        private final Long stake;

        HashWithTxIdList(byte[] hashOfProposalList, Long stake) {
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
