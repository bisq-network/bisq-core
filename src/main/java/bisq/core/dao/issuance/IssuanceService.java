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
import bisq.core.dao.blockchain.vo.TxOutput;
import bisq.core.dao.issuance.consensus.IssuanceConsensus;
import bisq.core.dao.node.BsqNode;
import bisq.core.dao.node.BsqNodeProvider;
import bisq.core.dao.proposal.Proposal;
import bisq.core.dao.proposal.ProposalCollectionsService;
import bisq.core.dao.proposal.ProposalList;
import bisq.core.dao.vote.BlindVote;
import bisq.core.dao.vote.BooleanVoteResult;
import bisq.core.dao.vote.RevealedVote;
import bisq.core.dao.vote.VoteService;

import bisq.common.crypto.CryptoException;
import bisq.common.crypto.Encryption;
import bisq.common.util.Tuple2;

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
import java.util.Set;
import java.util.stream.Collectors;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IssuanceService implements BsqNode.BsqBlockChainListener {

    private ProposalCollectionsService proposalCollectionsService;
    private VoteService voteService;
    private ReadableBsqBlockChain readableBsqBlockChain;
    private WritableBsqBlockChain writableBsqBlockChain;
    private DaoPeriodService daoPeriodService;
    private BsqWalletService bsqWalletService;

    @Inject
    public IssuanceService(ProposalCollectionsService proposalCollectionsService,
                           BsqNodeProvider bsqNodeProvider,
                           VoteService voteService,
                           ReadableBsqBlockChain readableBsqBlockChain,
                           WritableBsqBlockChain writableBsqBlockChain,
                           DaoPeriodService daoPeriodService,
                           BsqWalletService bsqWalletService) {
        this.proposalCollectionsService = proposalCollectionsService;
        this.voteService = voteService;
        this.readableBsqBlockChain = readableBsqBlockChain;
        this.writableBsqBlockChain = writableBsqBlockChain;
        this.daoPeriodService = daoPeriodService;
        this.bsqWalletService = bsqWalletService;

        bsqNodeProvider.getBsqNode().addBsqBlockChainListener(this);
    }

    public void onAllServicesInitialized() {
    }


    public void shutDown() {
    }


    private void applyVoteResult() {
        // proposalCollectionsService.getAllProposals().stream().filter(proposal -> daoPeriodService.)
        //voteService.getBlindVoteList();

        Map<String, byte[]> opReturnHashesByTxIdMap = new HashMap<>();
        readableBsqBlockChain.getVoteRevealTxOutputs().stream()
                .filter(txOutput -> daoPeriodService.isTxInCurrentCycle(txOutput.getId()))
                .forEach(txOutput -> opReturnHashesByTxIdMap.put(txOutput.getTxId(), txOutput.getOpReturnData()));
        Map<byte[], List<String>> txIdListByVoteListHashMap = getTxIdListByVoteListHashMap(opReturnHashesByTxIdMap);
        if (!txIdListByVoteListHashMap.isEmpty()) {
            Map<String, SecretKey> secretKeysByTxIdMap = getSecretsKeyByTxIdMap(opReturnHashesByTxIdMap);
            Set<BlindVoteWithRevealTxId> blindVoteWithRevealTxIdSet = getBlindVoteWithRevealTxIdSet();
            Set<RevealedVote> revealedVotes = getRevealedVotes(secretKeysByTxIdMap, blindVoteWithRevealTxIdSet);
            byte[] majorityVoteListHashByTxIdMap = getMajorityVoteListHashByTxIdMap(txIdListByVoteListHashMap);
            Set<RevealedVote> revealedVotesMatchingMajority = getRevealedVotesMatchingMajority
                    (majorityVoteListHashByTxIdMap, revealedVotes);
            Map<Proposal, Integer> stakeByProposalMap = getResultStakeByProposalMap(revealedVotesMatchingMajority);
            IssuanceConsensus.applyVoteResult(stakeByProposalMap, readableBsqBlockChain, writableBsqBlockChain);
        }
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

    private Set<RevealedVote> getRevealedVotesMatchingMajority(byte[] majorityVoteListHash, Set<RevealedVote> revealedVotes) {
        return revealedVotes.stream()
                .filter(revealedVote -> {
                    final byte[] hash = IssuanceConsensus.getHashOfEncryptedProposalList(
                            revealedVote.getBlindVote().getEncryptedProposalList());
                    return Arrays.equals(majorityVoteListHash, hash);
                })
                .collect(Collectors.toSet());
    }

    private Set<RevealedVote> getRevealedVotes(Map<String, SecretKey> secretKeysByTxIdMap,
                                               Set<BlindVoteWithRevealTxId> blindVotesWithStakeOutputTxIdTuples) {
        return blindVotesWithStakeOutputTxIdTuples.stream()
                .map(blindVoteWithRevealTxId -> {
                    try {
                        //TODO clone blindVote from blindVoteWithRevealTxId.getBlindVote()
                        final BlindVote blindVote = blindVoteWithRevealTxId.getBlindVote();
                        final byte[] encryptedProposalList = blindVote.getEncryptedProposalList();
                        final SecretKey secretKey = secretKeysByTxIdMap.get(blindVoteWithRevealTxId.getTxId());
                        final byte[] decrypted = Encryption.decrypt(encryptedProposalList, secretKey);

                        //TODO
                        ProposalList proposalList1 = ProposalList.fromProto(PB.ProposalList.parseFrom(decrypted));
                        final PB.PersistableEnvelope envelope = PB.PersistableEnvelope.parseFrom(decrypted);
                        ProposalList proposalList = ProposalList.fromProto(envelope.getProposalList());
                        log.error("proposalList1 " + proposalList1);
                        log.error("envelope " + envelope);
                        log.error("proposalList " + proposalList);
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

    private Set<BlindVoteWithRevealTxId> getBlindVoteWithRevealTxIdSet() {
        return voteService.getBlindVoteList().stream()
                .filter(blindVote -> daoPeriodService.isTxInCurrentCycle(blindVote.getTxId()))
                .map(blindVote -> readableBsqBlockChain.getOptionalTx(blindVote.getTxId())
                        .flatMap(tx -> tx.getTxOutput(0))
                        .map(TxOutput::getTxId)
                        .map(txId -> new BlindVoteWithRevealTxId(blindVote, txId))
                        .orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }


    private Map<String, SecretKey> getSecretsKeyByTxIdMap(Map<String, byte[]> opReturnMap) {
        Map<String, SecretKey> map = new HashMap<>();
        opReturnMap.forEach((key, value) -> map.put(key, IssuanceConsensus.getSecretKey(value)));
        return map;
    }

    private Map<byte[], List<String>> getTxIdListByVoteListHashMap(Map<String, byte[]> opReturnMap) {
        Map<byte[], List<String>> map = new HashMap<>();
        opReturnMap.forEach((txId, data) -> {
            final byte[] hash = IssuanceConsensus.getVoteListHash(data);
            map.computeIfAbsent(hash, v -> new ArrayList<>());
            map.get(hash).add(txId);
        });

        return map;
    }

    //TODO test
    private byte[] getMajorityVoteListHashByTxIdMap(Map<byte[], List<String>> txIdListByVoteListHashMap) {
        List<Tuple2<byte[], List<String>>> list = new ArrayList<>();
        txIdListByVoteListHashMap.forEach((key, value) -> list.add(new Tuple2<>(key, value)));
        list.sort(Comparator.comparingInt(o -> o.second.size()));
        return list.get(0).first;
    }


    @Override
    public void onBsqBlockChainChanged() {
        final int height = readableBsqBlockChain.getChainHeadHeight();
        final int absoluteStartBlockOfPhase = daoPeriodService.getAbsoluteStartBlockOfPhase(height, DaoPeriodService.Phase.PROPOSAL);
        if (height == absoluteStartBlockOfPhase) {
            applyVoteResult();
        }
    }

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
