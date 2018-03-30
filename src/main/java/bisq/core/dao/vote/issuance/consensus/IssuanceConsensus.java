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
import bisq.core.dao.vote.proposal.Proposal;

import bisq.common.crypto.Encryption;
import bisq.common.crypto.Hash;

import javax.crypto.SecretKey;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IssuanceConsensus {

    //TODO set higher value
    private static final int QUORUM = 1000; // 10 BSQ

    public static SecretKey getSecretKey(byte[] opReturnData) {
        byte[] secretKeyAsBytes = Arrays.copyOfRange(opReturnData, 22, 54);
        return Encryption.getSecretKeyFromBytes(secretKeyAsBytes);
    }

    // Hash of the list of Blind votes is 20 bytes after version and type bytes
    public static byte[] getBlindVoteListHash(byte[] opReturnData) {
        return Arrays.copyOfRange(opReturnData, 2, 22);
    }

    //TODO share with vote consensus
    public static byte[] getHashOfEncryptedProposalList(byte[] encryptedProposalList) {
        return Hash.getSha256Ripemd160hash(encryptedProposalList);
    }

    public static void applyVoteResult(Map<Proposal, Integer> stakeByProposalMap,
                                       ReadableBsqBlockChain readableBsqBlockChain,
                                       WritableBsqBlockChain writableBsqBlockChain) {
        Map<String, TxOutput> txOutputsByTxIdMap = new HashMap<>();
        final Set<TxOutput> compReqIssuanceTxOutputs = readableBsqBlockChain.getCompReqIssuanceTxOutputs();
        compReqIssuanceTxOutputs.stream()
                .filter(txOutput -> !txOutput.isVerified()) // our candidate is not yet verified and not set
                /*.filter(txOutput -> txOutput.isUnspent())*/ // TODO set unspent and keep track of it in parser
                .forEach(txOutput -> txOutputsByTxIdMap.put(txOutput.getTxId(), txOutput));

        stakeByProposalMap.forEach((proposal, value) -> {
            int stakeResult = value;
            if (stakeResult >= QUORUM) {
                final String txId = proposal.getTxId();
                if (txOutputsByTxIdMap.containsKey(txId)) {
                    final TxOutput txOutput = txOutputsByTxIdMap.get(txId);
                    writableBsqBlockChain.issueBsq(txOutput);
                    log.info("We issued new BSQ to txOutput {} for proposal {}", txOutput, proposal);
                }
            } else {
                log.warn("We got a successful vote result but did not reach the quorum. stake={}, quorum={}",
                        stakeResult, QUORUM);
            }
        });
    }
}
