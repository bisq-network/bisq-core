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

package bisq.core.dao.vote.proposal.compensation.issuance;

import bisq.core.dao.blockchain.BsqBlockChain;
import bisq.core.dao.blockchain.ReadableBsqBlockChain;
import bisq.core.dao.blockchain.WritableBsqBlockChain;
import bisq.core.dao.blockchain.vo.BsqBlock;
import bisq.core.dao.blockchain.vo.Tx;
import bisq.core.dao.blockchain.vo.TxOutput;
import bisq.core.dao.blockchain.vo.TxOutputType;
import bisq.core.dao.blockchain.vo.TxType;
import bisq.core.dao.param.DaoParam;
import bisq.core.dao.param.DaoParamService;
import bisq.core.dao.vote.PeriodService;
import bisq.core.dao.vote.blindvote.BlindVote;
import bisq.core.dao.vote.blindvote.BlindVoteList;
import bisq.core.dao.vote.blindvote.BlindVoteService;
import bisq.core.dao.vote.proposal.ProposalList;
import bisq.core.dao.vote.proposal.ProposalPayload;
import bisq.core.dao.vote.proposal.asset.RemoveAssetProposalPayload;
import bisq.core.dao.vote.proposal.compensation.CompensationRequestPayload;
import bisq.core.dao.vote.proposal.generic.GenericProposalPayload;
import bisq.core.dao.vote.proposal.param.ChangeParamProposalPayload;
import bisq.core.dao.vote.result.BooleanVoteResult;
import bisq.core.dao.vote.result.LongVoteResult;
import bisq.core.dao.vote.result.VoteResult;
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
public class IssuanceService implements BsqBlockChain.Listener {
    private final BlindVoteService blindVoteService;
    private final VoteRevealService voteRevealService;
    private final ReadableBsqBlockChain readableBsqBlockChain;
    private final WritableBsqBlockChain writableBsqBlockChain;
    private final DaoParamService daoParamService;
    private final PeriodService periodService;
    @Getter
    private final ObservableList<IssuanceException> issuanceExceptions = FXCollections.observableArrayList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public IssuanceService(BlindVoteService blindVoteService,
                           VoteRevealService voteRevealService,
                           ReadableBsqBlockChain readableBsqBlockChain,
                           WritableBsqBlockChain writableBsqBlockChain,
                           DaoParamService daoParamService,
                           PeriodService periodService) {
        this.blindVoteService = blindVoteService;
        this.voteRevealService = voteRevealService;
        this.readableBsqBlockChain = readableBsqBlockChain;
        this.writableBsqBlockChain = writableBsqBlockChain;
        this.daoParamService = daoParamService;
        this.periodService = periodService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        readableBsqBlockChain.addListener(this);
    }

    public void shutDown() {
    }

    @Override
    public void onBlockAdded(BsqBlock bsqBlock) {
        if (periodService.getPhaseForHeight(bsqBlock.getHeight()) == PeriodService.Phase.ISSUANCE) {
            // A phase change is triggered by a new block but we need to wait for the parser to complete
            //TODO use handler only triggered at end of parsing. -> Refactor bsqBlockChain and BsqNode handlers
            log.info("blockHeight " + bsqBlock.getHeight());
            applyVoteResult();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void applyVoteResult() {
        // We make a map with txIds of VoteReveal TxOutputs as key and the opReturn data as value (containing secret key
        // and hash of proposal list)
        Map<String, byte[]> opReturnByVoteRevealTxIdMap = getOpReturnByTxIdMap();
        if (!opReturnByVoteRevealTxIdMap.isEmpty()) {
            // From the opReturnByVoteRevealTxIdMap we create a map with the hash of blind vote list as key and a list
            // of txIds as value. That map is used for calculating the majority of the blind vote lists. If there are
            // conflicting versions due the eventually consistency of the P2P network (it might be that some blind votes do
            // not arrive at all voters) we will only consider the majority data view.
            Map<byte[], List<String>> txIdListMap = getTxIdListByVoteListHashMap(opReturnByVoteRevealTxIdMap);

            // We make a map with the VoteReveal TxId as key and the secret key decoded from the opReturn data where the
            // voter has revealed the secret key.
            Map<String, SecretKey> secretKeysByTxIdMap = getSecretsKeyByTxIdMap(opReturnByVoteRevealTxIdMap);

            // We make a set of BlindVoteWithRevealTxId objects. BlindVoteWithRevealTxId holds the blind vote with
            // the reveal tx ID. The stake output of the blind vote tx is used as the input for the reveal tx,
            // this is used to connect those transactions.
            Set<BlindVoteWithRevealTxId> blindVoteWithRevealTxIdSet = getBlindVoteWithRevealTxIdSet();

            // We have now all data prepared required to get the decrypted vote data so we can calculate the result
            Set<RevealedVote> revealedVotes = getRevealedVotes(blindVoteWithRevealTxIdSet, secretKeysByTxIdMap);

            byte[] majorityVoteListHash = getMajorityVoteListHashByTxIdMap(txIdListMap);

            if (majorityVoteListHash != null) {
                if (isBlindVoteListMatchingMajority(majorityVoteListHash)) {
                    Map<ProposalPayload, List<VoteResultWithStake>> resultListByProposalPayloadMap = getResultListByProposalPayloadMap(revealedVotes);
                    processAllVoteResults(resultListByProposalPayloadMap, daoParamService, writableBsqBlockChain, readableBsqBlockChain);
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
            log.info("There have not been any votes in that cycle.");
        }
    }

    private Map<String, byte[]> getOpReturnByTxIdMap() {
        Map<String, byte[]> opReturnHashesByTxIdMap = new HashMap<>();
        // We want all voteRevealTxOutputs which are in current cycle we are processing.
        readableBsqBlockChain.getVoteRevealTxOutputs().stream()
                .filter(txOutput -> periodService.isTxInCurrentCycle(txOutput.getTxId()))
                .forEach(txOutput -> opReturnHashesByTxIdMap.put(txOutput.getTxId(), txOutput.getOpReturnData()));
        return opReturnHashesByTxIdMap;
    }

    private Map<byte[], List<String>> getTxIdListByVoteListHashMap(Map<String, byte[]> opReturnMap) {
        Map<byte[], List<String>> map = new HashMap<>();
        opReturnMap.forEach((txId, data) -> {
            final byte[] hash = IssuanceConsensus.getBlindVoteListHash(data);
            map.computeIfAbsent(hash, bytes -> new ArrayList<>());
            map.get(hash).add(txId); // We add to each of the blindVoteList hashes the txId of the voteReveal tx
        });
        return map;
    }

    private Map<String, SecretKey> getSecretsKeyByTxIdMap(Map<String, byte[]> opReturnMap) {
        Map<String, SecretKey> map = new HashMap<>();
        opReturnMap.forEach((key, value) -> map.put(key, IssuanceConsensus.getSecretKey(value)));
        return map;
    }

    private Set<BlindVoteWithRevealTxId> getBlindVoteWithRevealTxIdSet() {
        //TODO check not in current cycle but in the cycle of the tx
        return blindVoteService.getBlindVoteList().stream()
                .filter(blindVote -> periodService.isTxInCurrentCycle(blindVote.getTxId()))
                .map(blindVote -> {
                    return readableBsqBlockChain.getTx(blindVote.getTxId())
                            .filter(blindVoteTx -> blindVoteTx.getTxType() == TxType.BLIND_VOTE) // double check if type is matching
                            .map(blindVoteTx -> blindVoteTx.getTxOutput(0)) // stake need to be output 0
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .filter(stakeTxOutput -> stakeTxOutput.getTxOutputType() == TxOutputType.BLIND_VOTE_LOCK_STAKE_OUTPUT) // double check if type is matching
                            .map(TxOutput::getTxId)
                            .map(this::getRevealTxIdForBlindVoteTx)
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .map(revealTxId -> new BlindVoteWithRevealTxId(blindVote, revealTxId))
                            .orElse(null);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    // Finds txId of voteReveal tx which has its first input connected to the blind vote tx's first output for
    // transferring the stake.
    private Optional<String> getRevealTxIdForBlindVoteTx(String blindVoteTxId) {
        Optional<String> optionalBlindVoteStakeTxOutputId = readableBsqBlockChain.getTx(blindVoteTxId)
                .map(Tx::getOutputs)
                .filter(outputs -> !outputs.isEmpty())
                .map(outputs -> outputs.get(0))
                .map(TxOutput::getId);

        if (optionalBlindVoteStakeTxOutputId.isPresent()) {
            String blindVoteStakeTxOutputId = optionalBlindVoteStakeTxOutputId.get();
            return readableBsqBlockChain.getTxMap().values().stream()
                    .filter(tx -> tx.getTxType() == TxType.VOTE_REVEAL)
                    .map(tx -> {
                        if (!tx.getInputs().isEmpty()) {
                            TxOutput connectedTxOutput = tx.getInputs().get(0).getConnectedTxOutput();
                            if (connectedTxOutput != null) {
                                if (connectedTxOutput.getId().equals(blindVoteStakeTxOutputId))
                                    return tx.getId();
                            }
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
                            final byte[] decrypted = IssuanceConsensus.decryptProposalList(encryptedProposalList, secretKey);
                            ProposalList proposalList = ProposalList.getProposalListFromBytes(decrypted);
                            return new RevealedVote(proposalList, blindVote);
                        } catch (CryptoException | InvalidProtocolBufferException e) {
                            log.error(e.toString());
                            e.printStackTrace();
                            issuanceExceptions.add(new IssuanceException("Error at getRevealedVotes", e, blindVote));
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
    private byte[] getMajorityVoteListHashByTxIdMap(Map<byte[], List<String>> txIdListMap) {
        List<HashWithTxIdList> list = new ArrayList<>();
        txIdListMap.forEach((key, value) -> list.add(new HashWithTxIdList(key, value)));
        return IssuanceConsensus.getMajorityHash(list);
    }

    private boolean isBlindVoteListMatchingMajority(byte[] majorityVoteListHash) {
        final BlindVoteList blindVoteList = voteRevealService.getSortedBlindVoteListForCurrentCycle();
        byte[] hashOfBlindVoteList = VoteRevealConsensus.getHashOfBlindVoteList(blindVoteList);
        log.info("majorityVoteListHash " + Utilities.bytesAsHexString(majorityVoteListHash));
        log.info("Sha256Ripemd160 hash of my blindVoteList " + Utilities.bytesAsHexString(hashOfBlindVoteList));
        return Arrays.equals(majorityVoteListHash, hashOfBlindVoteList);
    }

    private Map<ProposalPayload, List<VoteResultWithStake>> getResultListByProposalPayloadMap(Set<RevealedVote> revealedVotes) {
        Map<ProposalPayload, List<VoteResultWithStake>> stakeByProposalMap = new HashMap<>();
        revealedVotes.forEach(revealedVote -> revealedVote.getProposalList().getList()
                .forEach(proposal -> {
                    final ProposalPayload proposalPayload = proposal.getProposalPayload();
                    stakeByProposalMap.putIfAbsent(proposalPayload, new ArrayList<>());
                    final List<VoteResultWithStake> voteResultWithStakes = stakeByProposalMap.get(proposalPayload);
                    voteResultWithStakes.add(new VoteResultWithStake(proposal.getVoteResult(), revealedVote.getStake()));
                }));
        return stakeByProposalMap;
    }

    private void processAllVoteResults(Map<ProposalPayload, List<VoteResultWithStake>> map,
                                       DaoParamService daoParamService,
                                       WritableBsqBlockChain writableBsqBlockChain,
                                       ReadableBsqBlockChain readableBsqBlockChain) {
        map.forEach((proposalPayload, voteResultsWithStake) -> {
            VoteResultPerProposal voteResultPerProposal = getDetailResult(voteResultsWithStake);
            long totalStake = voteResultPerProposal.getStakeOfAcceptedVotes() + voteResultPerProposal.getStakeOfRejectedVotes();
            long quorum = getQuorum(daoParamService, proposalPayload);
            if (totalStake >= quorum) {
                // we use multiplied of 1000 as we use a long for requiredVoteThreshold and that got added precision, so
                // 50% is 50.00. As we use 100% for 1 we get another multiplied by 100, resulting in 10 000.
                long reachedThreshold = voteResultPerProposal.getStakeOfAcceptedVotes() * 10_000 / totalStake;
                // E.g. returns 5000 for 50% or 0.5 from the division of acceptedVotes/totalStake
                long requiredVoteThreshold = getThreshold(daoParamService, proposalPayload);
                log.info("reachedThreshold {} %", reachedThreshold / 100D);
                log.info("requiredVoteThreshold {} %", requiredVoteThreshold / 100D);
                if (reachedThreshold >= requiredVoteThreshold) {
                    processAcceptedCompletedVoteResult(proposalPayload, writableBsqBlockChain, readableBsqBlockChain);
                } else {
                    log.warn("We did not reach the quorum. reachedThreshold={} %, requiredVoteThreshold={} %", reachedThreshold / 100D, requiredVoteThreshold / 100D);
                }
            } else {
                log.warn("We did not reach the quorum. totalStake={}, quorum={}", totalStake, quorum);
            }
        });
    }

    private long getQuorum(DaoParamService daoParamService, ProposalPayload proposalPayload) {
        DaoParam daoParam;
        if (proposalPayload instanceof CompensationRequestPayload)
            daoParam = DaoParam.QUORUM_COMP_REQUEST;
        else if (proposalPayload instanceof GenericProposalPayload)
            daoParam = DaoParam.QUORUM_PROPOSAL;
        else if (proposalPayload instanceof ChangeParamProposalPayload)
            daoParam = DaoParam.QUORUM_CHANGE_PARAM;
        else if (proposalPayload instanceof RemoveAssetProposalPayload)
            daoParam = DaoParam.QUORUM_REMOVE_ASSET;
        else
            throw new RuntimeException("proposalPayload type is not reflected in DaoParam");

        return daoParamService.getDaoParamValue(daoParam, readableBsqBlockChain.getChainHeadHeight());
    }

    private long getThreshold(DaoParamService daoParamService, ProposalPayload proposalPayload) {
        DaoParam daoParam;
        if (proposalPayload instanceof CompensationRequestPayload)
            daoParam = DaoParam.THRESHOLD_COMP_REQUEST;
        else if (proposalPayload instanceof GenericProposalPayload)
            daoParam = DaoParam.THRESHOLD_PROPOSAL;
        else if (proposalPayload instanceof ChangeParamProposalPayload)
            daoParam = DaoParam.THRESHOLD_CHANGE_PARAM;
        else if (proposalPayload instanceof RemoveAssetProposalPayload)
            daoParam = DaoParam.THRESHOLD_REMOVE_ASSET;
        else
            throw new RuntimeException("proposalPayload type is not reflected in DaoParam");

        return daoParamService.getDaoParamValue(daoParam, readableBsqBlockChain.getChainHeadHeight());
    }

    private VoteResultPerProposal getDetailResult(List<VoteResultWithStake> voteResultsWithStake) {
        long stakeOfAcceptedVotes = 0;
        long stakeOfRejectedVotes = 0;
        for (VoteResultWithStake voteResultWithStake : voteResultsWithStake) {
            long stake = voteResultWithStake.getStake();
            VoteResult voteResult = voteResultWithStake.getVoteResult();
            if (voteResult != null) {
                if (voteResult instanceof BooleanVoteResult) {
                    BooleanVoteResult result = (BooleanVoteResult) voteResult;
                    if (result.isAccepted()) {
                        stakeOfAcceptedVotes += stake;
                    } else {
                        stakeOfRejectedVotes += stake;
                    }
                } else if (voteResult instanceof LongVoteResult) {
                    //TODO impl
                }
            } else {
                log.debug("Voter ignored proposal");
            }
        }
        return new VoteResultPerProposal(stakeOfAcceptedVotes, stakeOfRejectedVotes);
    }


    private void processAcceptedCompletedVoteResult(ProposalPayload proposalPayload,
                                                    WritableBsqBlockChain writableBsqBlockChain,
                                                    ReadableBsqBlockChain readableBsqBlockChain) {
        if (proposalPayload instanceof CompensationRequestPayload) {
            handleCompensationRequestPayloadResult(proposalPayload, writableBsqBlockChain, readableBsqBlockChain);
        } else if (proposalPayload instanceof GenericProposalPayload) {
            //TODO impl
        } else if (proposalPayload instanceof ChangeParamProposalPayload) {
            //TODO impl
        } else if (proposalPayload instanceof RemoveAssetProposalPayload) {
            //TODO impl
        }
    }

    private void handleCompensationRequestPayloadResult(ProposalPayload proposalPayload,
                                                        WritableBsqBlockChain writableBsqBlockChain,
                                                        ReadableBsqBlockChain readableBsqBlockChain) {
        Map<String, TxOutput> txOutputsByTxIdMap = new HashMap<>();
        final Set<TxOutput> compReqIssuanceTxOutputs = readableBsqBlockChain.getCompReqIssuanceTxOutputs();
        compReqIssuanceTxOutputs.stream()
                .filter(txOutput -> !txOutput.isVerified()) // our candidate is not yet verified
                /*.filter(txOutput -> txOutput.isUnspent())*/ // TODO set unspent and keep track of it in parser
                .forEach(txOutput -> txOutputsByTxIdMap.put(txOutput.getTxId(), txOutput));

        final String txId = proposalPayload.getTxId();
        if (txOutputsByTxIdMap.containsKey(txId)) {
            final TxOutput txOutput = txOutputsByTxIdMap.get(txId);
            writableBsqBlockChain.issueBsq(txOutput);
            log.info("################################################################################");
            log.info("## We issued new BSQ to txId {} for proposalPayload with UID {}", txId, proposalPayload.getUid());
            log.info("## txOutput {}, proposalPayload {}", txOutput, proposalPayload);
            log.info("################################################################################");
        } else {
            //TODO throw exception?
            log.error("txOutput not found in txOutputsByTxIdMap. That should never happen.");
        }
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
        private final List<String> txIds;

        HashWithTxIdList(byte[] hashOfProposalList, List<String> txIds) {
            this.hashOfProposalList = hashOfProposalList;
            this.txIds = txIds;
        }
    }

    @Value
    private static class VoteResultPerProposal {
        private final long stakeOfAcceptedVotes;
        private final long stakeOfRejectedVotes;

        VoteResultPerProposal(long stakeOfAcceptedVotes, long stakeOfRejectedVotes) {
            this.stakeOfAcceptedVotes = stakeOfAcceptedVotes;
            this.stakeOfRejectedVotes = stakeOfRejectedVotes;
        }
    }

    @Value
    private static class VoteResultWithStake {
        @Nullable
        private final VoteResult voteResult;
        private final long stake;

        VoteResultWithStake(@Nullable VoteResult voteResult, long stake) {
            this.voteResult = voteResult;
            this.stake = stake;
        }
    }
}
