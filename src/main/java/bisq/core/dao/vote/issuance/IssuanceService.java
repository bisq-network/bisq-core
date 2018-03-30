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

package bisq.core.dao.vote.issuance;

import bisq.core.dao.blockchain.BsqBlockChain;
import bisq.core.dao.blockchain.ReadableBsqBlockChain;
import bisq.core.dao.blockchain.WritableBsqBlockChain;
import bisq.core.dao.blockchain.vo.Tx;
import bisq.core.dao.blockchain.vo.TxOutput;
import bisq.core.dao.blockchain.vo.TxOutputType;
import bisq.core.dao.blockchain.vo.TxType;
import bisq.core.dao.vote.BooleanVoteResult;
import bisq.core.dao.vote.DaoPeriodService;
import bisq.core.dao.vote.blindvote.BlindVote;
import bisq.core.dao.vote.blindvote.BlindVoteList;
import bisq.core.dao.vote.blindvote.BlindVoteService;
import bisq.core.dao.vote.issuance.consensus.IssuanceConsensus;
import bisq.core.dao.vote.proposal.ProposalList;
import bisq.core.dao.vote.proposal.ProposalPayload;
import bisq.core.dao.vote.votereveal.RevealedVote;
import bisq.core.dao.vote.votereveal.consensus.VoteRevealConsensus;

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

@Slf4j
public class IssuanceService {

    private final BlindVoteService blindVoteService;
    private final ReadableBsqBlockChain readableBsqBlockChain;
    private final WritableBsqBlockChain writableBsqBlockChain;
    private final DaoPeriodService daoPeriodService;
    @Getter
    private final ObservableList<IssuanceException> issuanceExceptions = FXCollections.observableArrayList();
    private BsqBlockChain.Listener bsqBlockChainListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public IssuanceService(BlindVoteService blindVoteService,
                           ReadableBsqBlockChain readableBsqBlockChain,
                           WritableBsqBlockChain writableBsqBlockChain,
                           DaoPeriodService daoPeriodService) {
        this.blindVoteService = blindVoteService;
        this.readableBsqBlockChain = readableBsqBlockChain;
        this.writableBsqBlockChain = writableBsqBlockChain;
        this.daoPeriodService = daoPeriodService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        daoPeriodService.getPhaseProperty().addListener((observable, oldValue, newValue) -> {
            onPhaseChanged(newValue);
        });
        onPhaseChanged(daoPeriodService.getPhaseProperty().get());
    }

    public void shutDown() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onPhaseChanged(DaoPeriodService.Phase phase) {
        if (phase == DaoPeriodService.Phase.ISSUANCE) {
            // A phase change is triggered by a new block but we need to wait for the parser to complete
            //TODO use handler only triggered at end of parsing. -> Refactor bsqBlockChain and BsqNode handlers
            bsqBlockChainListener = bsqBlock -> applyVoteResult();
            readableBsqBlockChain.addListener(bsqBlockChainListener);
            applyVoteResult();
        } else {
            // If we are not in the issuance phase we are not interested in the events.
            if (bsqBlockChainListener != null)
                readableBsqBlockChain.removeListener(bsqBlockChainListener);
            bsqBlockChainListener = null;
        }
    }

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
                    Map<ProposalPayload, Long> stakeByProposalPayloadMap = getResultStakeByProposalPayloadMap(revealedVotes);
                    IssuanceConsensus.applyVoteResult(stakeByProposalPayloadMap, readableBsqBlockChain, writableBsqBlockChain);
                } else {
                    log.warn("Our list of received blind votes do not match the list from the majority of voters.");
                    // TODO request missing blind votes
                }
            } else {
                //TODO throw exception as it is likely not a valid option
                log.warn("majorityVoteListHash is null");
            }

        } else {
            log.debug("There have not been any votes in that cycle.");
        }
    }

    private Map<String, byte[]> getOpReturnByTxIdMap() {
        Map<String, byte[]> opReturnHashesByTxIdMap = new HashMap<>();
        // We want all voteRevealTxOutputs which are in current cycle we are processing.
        readableBsqBlockChain.getVoteRevealTxOutputs().stream()
                .filter(txOutput -> daoPeriodService.isTxInCurrentCycle(txOutput.getTxId()))
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
                .filter(blindVote -> daoPeriodService.isTxInCurrentCycle(blindVote.getTxId()))
                .map(blindVote -> {
                    return readableBsqBlockChain.getTx(blindVote.getTxId())
                            .filter(blindVoteTx -> blindVoteTx.getTxType() == TxType.BLIND_VOTE) // double check if type is matching
                            .map(blindVoteTx -> blindVoteTx.getTxOutput(0)) // stake need to be output 0
                            .filter(Optional::isPresent)
                            .map(Optional::get)
                            .filter(stakeTxOutput -> stakeTxOutput.getTxOutputType() == TxOutputType.BLIND_VOTE_STAKE_OUTPUT) // double check if type is matching
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
                    final SecretKey secretKey = secretKeysByTxIdMap.get(blindVoteWithRevealTxId.getRevealTxId());
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
        final BlindVoteList blindVoteList = new BlindVoteList(blindVoteService.getBlindVoteListForCurrentCycle());
        byte[] hashOfBlindVoteList = VoteRevealConsensus.getHashOfBlindVoteList(blindVoteList);
        log.info("majorityVoteListHash " + Utilities.bytesAsHexString(majorityVoteListHash));
        log.info("Sha256Ripemd160 hash of my blindVoteList " + Utilities.bytesAsHexString(hashOfBlindVoteList));
        return Arrays.equals(majorityVoteListHash, hashOfBlindVoteList);
    }

    private Map<ProposalPayload, Long> getResultStakeByProposalPayloadMap(Set<RevealedVote> revealedVotes) {
        Map<ProposalPayload, Long> stakeByProposalMap = new HashMap<>();
        revealedVotes.forEach(revealedVote -> revealedVote.getProposalList().getList()
                .forEach(proposal -> {
                    if (proposal.getVoteResult() instanceof BooleanVoteResult) {
                        BooleanVoteResult result = (BooleanVoteResult) proposal.getVoteResult();
                        final ProposalPayload proposalPayload = proposal.getProposalPayload();
                        stakeByProposalMap.putIfAbsent(proposalPayload, 0L);
                        long aggregatedStake = stakeByProposalMap.get(proposalPayload);

                        // If result is null voter ignored proposal and we don't change
                        // the aggregatedStake
                        if (result != null) {
                            final long stake = revealedVote.getStake();
                            if (result.isAccepted()) {
                                aggregatedStake += stake;
                                log.debug("Voter accepted that proposal with stake {}. proposal={}", stake, proposal);
                            } else {
                                aggregatedStake -= stake;
                                log.debug("Voter rejected that proposal with stake {}. proposal={}", stake, proposal);
                            }
                        } else {
                            log.debug("Voter ignored that proposal. proposal={}", proposal);
                        }

                        stakeByProposalMap.put(proposalPayload, aggregatedStake);
                    } else {
                        //TODO IntegerVoteResult not impl yet
                    }
                }));
        return stakeByProposalMap;
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
}
