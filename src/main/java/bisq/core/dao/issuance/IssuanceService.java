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

package bisq.core.dao.issuance;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.dao.DaoPeriodService;
import bisq.core.dao.blockchain.ReadableBsqBlockChain;
import bisq.core.dao.blockchain.WritableBsqBlockChain;
import bisq.core.dao.blockchain.vo.Tx;
import bisq.core.dao.blockchain.vo.TxOutput;
import bisq.core.dao.blockchain.vo.TxOutputType;
import bisq.core.dao.blockchain.vo.TxType;
import bisq.core.dao.issuance.consensus.IssuanceConsensus;
import bisq.core.dao.node.BsqNode;
import bisq.core.dao.node.BsqNodeProvider;
import bisq.core.dao.proposal.Proposal;
import bisq.core.dao.proposal.ProposalCollectionsService;
import bisq.core.dao.proposal.ProposalList;
import bisq.core.dao.vote.BlindVote;
import bisq.core.dao.vote.BlindVoteList;
import bisq.core.dao.vote.BlindVoteService;
import bisq.core.dao.vote.BooleanVoteResult;
import bisq.core.dao.vote.RevealedVote;
import bisq.core.dao.vote.VoteRevealService;
import bisq.core.dao.vote.consensus.VoteRevealConsensus;

import bisq.common.crypto.CryptoException;
import bisq.common.crypto.Encryption;
import bisq.common.util.Tuple2;
import bisq.common.util.Utilities;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.InvalidProtocolBufferException;

import javax.inject.Inject;

import javax.crypto.SecretKey;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IssuanceService {

    private final BsqNode bsqNode;
    private final ProposalCollectionsService proposalCollectionsService;
    private final BlindVoteService blindVoteService;
    private final VoteRevealService voteRevealService;
    private final ReadableBsqBlockChain readableBsqBlockChain;
    private final WritableBsqBlockChain writableBsqBlockChain;
    private final DaoPeriodService daoPeriodService;
    private final BsqWalletService bsqWalletService;
    private BsqNode.BsqBlockChainListener bsqBlockChainListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public IssuanceService(ProposalCollectionsService proposalCollectionsService,
                           BsqNodeProvider bsqNodeProvider,
                           BlindVoteService blindVoteService,
                           VoteRevealService voteRevealService,
                           ReadableBsqBlockChain readableBsqBlockChain,
                           WritableBsqBlockChain writableBsqBlockChain,
                           DaoPeriodService daoPeriodService,
                           BsqWalletService bsqWalletService) {
        this.proposalCollectionsService = proposalCollectionsService;
        this.blindVoteService = blindVoteService;
        this.voteRevealService = voteRevealService;
        this.readableBsqBlockChain = readableBsqBlockChain;
        this.writableBsqBlockChain = writableBsqBlockChain;
        this.daoPeriodService = daoPeriodService;
        this.bsqWalletService = bsqWalletService;

        bsqNode = bsqNodeProvider.getBsqNode();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        bsqBlockChainListener = () -> {
            final int height = readableBsqBlockChain.getChainHeadHeight();
            final int absoluteStartBlockOfPhase = daoPeriodService.getAbsoluteStartBlockOfPhase(height, DaoPeriodService.Phase.ISSUANCE);
            if (height == absoluteStartBlockOfPhase) {
                applyVoteResult();
            }
        };
        bsqNode.addBsqBlockChainListener(bsqBlockChainListener);
    }

    public void shutDown() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void applyVoteResult() {
        // We make a map with txId of VoteReveal TxOutput as key and the opReturn data as value (containing secret key
        // and hash of proposal list)
        Map<String, byte[]> opReturnHashesByVoteRevealTxIdMap = getOpReturnHashesByTxIdMap();

        // From the opReturnHashesByVoteRevealTxIdMap we create a map with the hash of blind vote list as key and a list
        // of Tx Ids as value. That map is used for calculating the majority of the blind vote lists if there are
        // conflicting versions due the eventually consistency of the P2P network (it might be that some blind votes do
        // not arrive at all voters).
        Map<byte[], List<String>> txIdListMap = getTxIdListByVoteListHashMap(opReturnHashesByVoteRevealTxIdMap);
        if (!txIdListMap.isEmpty()) {
            // We make a map of the VoteReveal TxId as key and the secret key decoded from the opReturn data where the
            // voter has revealed the secret key.
            Map<String, SecretKey> secretKeysByTxIdMap = getSecretsKeyByTxIdMap(opReturnHashesByVoteRevealTxIdMap);

            // We make a set of BlindVoteWithRevealTxId objects. BlindVoteWithRevealTxId holds the blind vote with
            // the txId of the reveal Tx. The stake output of the blind vote tx is used as the input for the reveal tx,
            // this is used to connect those transactions.
            Set<BlindVoteWithRevealTxId> blindVoteWithRevealTxIdSet = getBlindVoteWithRevealTxIdSet();
            // We have now all data required to get the decrypted vote data so we can calculate the result
            Set<RevealedVote> revealedVotes = getRevealedVotes(secretKeysByTxIdMap, blindVoteWithRevealTxIdSet);
            byte[] majorityVoteListHash = getMajorityVoteListHashByTxIdMap(txIdListMap);
            if (isBlindVoteListMatchingMajority(majorityVoteListHash)) {
                Map<Proposal, Integer> stakeByProposalMap = getResultStakeByProposalMap(revealedVotes);
                IssuanceConsensus.applyVoteResult(stakeByProposalMap, readableBsqBlockChain, writableBsqBlockChain);
            } else {
                log.warn("Our list of received blind votes do not match the list from the majority of voters.");
                // TODO request missing blind votes
            }
        }
    }

    private Map<String, byte[]> getOpReturnHashesByTxIdMap() {
        Map<String, byte[]> opReturnHashesByTxIdMap = new HashMap<>();
        //TODO check not in current cycle but in the cycle of the tx
        readableBsqBlockChain.getVoteRevealTxOutputs().stream()
                .filter(txOutput -> daoPeriodService.isTxInCurrentCycle(txOutput.getTxId()))
                .forEach(txOutput -> opReturnHashesByTxIdMap.put(txOutput.getTxId(), txOutput.getOpReturnData()));
        return opReturnHashesByTxIdMap;
    }

    private Map<byte[], List<String>> getTxIdListByVoteListHashMap(Map<String, byte[]> opReturnMap) {
        Map<byte[], List<String>> map = new HashMap<>();
        opReturnMap.forEach((txId, data) -> {
            final byte[] hash = IssuanceConsensus.getBlindVoteListHash(data);
            map.computeIfAbsent(hash, v -> new ArrayList<>());
            map.get(hash).add(txId);
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

    private Set<RevealedVote> getRevealedVotes(Map<String, SecretKey> secretKeysByTxIdMap,
                                               Set<BlindVoteWithRevealTxId> blindVoteWithRevealTxIdSet) {
        return blindVoteWithRevealTxIdSet.stream()
                .map(blindVoteWithRevealTxId -> {
                    try {
                        // TODO check if cloning here is needed (we might want to keep the blindVote separated from the
                        // blindVoteList to the RevealedVote...)
                        final BlindVote blindVote = BlindVote.clone(blindVoteWithRevealTxId.getBlindVote());
                        final byte[] encryptedProposalList = blindVote.getEncryptedProposalList();
                        final SecretKey secretKey = secretKeysByTxIdMap.get(blindVoteWithRevealTxId.getTxId());
                        final byte[] decrypted = Encryption.decrypt(encryptedProposalList, secretKey);

                        //TODO move to ProposalList
                        final PB.PersistableEnvelope envelope = PB.PersistableEnvelope.parseFrom(decrypted);
                        ProposalList proposalList = ProposalList.fromProto(envelope.getProposalList());
                        return new RevealedVote(proposalList, blindVote);
                    } catch (CryptoException e) {
                        e.printStackTrace();
                        return null;
                    } catch (InvalidProtocolBufferException e) {
                        e.printStackTrace();
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    //TODO test
    private byte[] getMajorityVoteListHashByTxIdMap(Map<byte[], List<String>> txIdListMap) {
        List<Tuple2<byte[], List<String>>> list = new ArrayList<>();
        txIdListMap.forEach((key, value) -> list.add(new Tuple2<>(key, value)));
        list.sort(Comparator.comparingInt(o -> o.second.size()));
        return list.get(0).first;
    }

    private Map<Proposal, Integer> getResultStakeByProposalMap(Set<RevealedVote> revealedVotes) {
        // We cannot use Proposal as key as the different voteResults makes aggregation of proposals not possible
        Map<String, Integer> stakeByProposalTxIdMap = new HashMap<>();
        revealedVotes.forEach(revealedVote -> revealedVote.getProposalList().getList()
                .forEach(proposal -> {
                    if (proposal.getVoteResult() instanceof BooleanVoteResult) {
                        BooleanVoteResult result = (BooleanVoteResult) proposal.getVoteResult();
                        final String txId = proposal.getTxId();
                        stakeByProposalTxIdMap.putIfAbsent(txId, 0);
                        int aggregatedStake = stakeByProposalTxIdMap.get(txId);

                        // if result is null voter ignored proposal and we don't change
                        // the aggregatedStake
                        if (result != null) {
                            if (result.isAccepted())
                                aggregatedStake += revealedVote.getBlindVote().getStake();
                            else
                                aggregatedStake -= revealedVote.getBlindVote().getStake();
                        }

                        stakeByProposalTxIdMap.put(txId, aggregatedStake);
                    } else {
                        //TODO IntegerVoteResult not impl yet
                    }
                }));

        Map<Proposal, Integer> stakeByProposalMap = new HashMap<>();
        revealedVotes.forEach(revealedVote -> revealedVote.getProposalList().getList()
                .forEach(proposal -> stakeByProposalMap.put(proposal,
                        stakeByProposalTxIdMap.get(proposal.getTxId()))));
        return stakeByProposalMap;
    }

    private boolean isBlindVoteListMatchingMajority(byte[] majorityVoteListHash) {
        final BlindVoteList blindVoteList = new BlindVoteList(blindVoteService.getBlindVoteListForCurrentCycle());
        byte[] hashOfBlindVoteList = VoteRevealConsensus.getHashOfBlindVoteList(blindVoteList);
        log.info("majorityVoteListHash " + Utilities.bytesAsHexString(majorityVoteListHash));
        log.info("Sha256Ripemd160 hash of hashOfBlindVoteList " + Utilities.bytesAsHexString(hashOfBlindVoteList));
        return Arrays.equals(majorityVoteListHash, hashOfBlindVoteList);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Inner class
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Value
    private class BlindVoteWithRevealTxId {
        private final BlindVote blindVote;
        private final String txId;

        BlindVoteWithRevealTxId(BlindVote blindVote, String txId) {
            this.blindVote = blindVote;
            this.txId = txId;
        }
    }
}
