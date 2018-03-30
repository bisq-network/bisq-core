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

package bisq.core.dao.vote.issuance.consensus;

import bisq.core.dao.blockchain.ReadableBsqBlockChain;
import bisq.core.dao.blockchain.WritableBsqBlockChain;
import bisq.core.dao.blockchain.vo.TxOutput;
import bisq.core.dao.vote.issuance.IssuanceService;
import bisq.core.dao.vote.proposal.ProposalPayload;

import bisq.common.crypto.CryptoException;
import bisq.common.crypto.Encryption;

import javax.crypto.SecretKey;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public class IssuanceConsensus {

    //TODO set higher value
    private static final int QUORUM = 100; // 1 BSQ

    // Hash of the list of Blind votes is 20 bytes after version and type bytes
    public static byte[] getBlindVoteListHash(byte[] opReturnData) {
        return Arrays.copyOfRange(opReturnData, 2, 22);
    }

    public static byte[] decryptProposalList(byte[] encryptedProposalList, SecretKey secretKey) throws CryptoException {
        return Encryption.decrypt(encryptedProposalList, secretKey);
    }

    @Nullable
    public static byte[] getMajorityHash(List<IssuanceService.HashWithTxIdList> list) {
        list.sort(Comparator.comparingInt(o -> o.getTxIds().size()));
        return !list.isEmpty() ? list.get(0).getHashOfProposalList() : null;
    }

    // Key is stored after version and type bytes and list of Blind votes. It has 32 bytes
    public static SecretKey getSecretKey(byte[] opReturnData) {
        byte[] secretKeyAsBytes = Arrays.copyOfRange(opReturnData, 22, 54);
        return Encryption.getSecretKeyFromBytes(secretKeyAsBytes);
    }

    public static void applyVoteResult(Map<ProposalPayload, Long> stakeByProposalMap,
                                       ReadableBsqBlockChain readableBsqBlockChain,
                                       WritableBsqBlockChain writableBsqBlockChain) {
        Map<String, TxOutput> txOutputsByTxIdMap = new HashMap<>();

        final Set<TxOutput> compReqIssuanceTxOutputs = readableBsqBlockChain.getCompReqIssuanceTxOutputs();
        compReqIssuanceTxOutputs.stream()
                .filter(txOutput -> !txOutput.isVerified()) // our candidate is not yet verified
                /*.filter(txOutput -> txOutput.isUnspent())*/ // TODO set unspent and keep track of it in parser
                .forEach(txOutput -> txOutputsByTxIdMap.put(txOutput.getTxId(), txOutput));

        stakeByProposalMap.forEach((proposalPayload, value) -> {
            long stakeResult = value;
            if (stakeResult >= QUORUM) {
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
            } else if (stakeResult > 0) {
                log.warn("We did not reach the quorum. stake={}, quorum={}", stakeResult, QUORUM);
            } else if (stakeResult == 0) {
                log.warn("StakeResult was 0. stake={}, quorum={}", stakeResult, QUORUM);
            } else {
                log.warn("We got a negative vote result. stake={}, quorum={}", stakeResult, QUORUM);
            }
        });
    }
}
