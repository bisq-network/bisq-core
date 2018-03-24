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

package bisq.core.dao.issuance.consensus;

import bisq.core.dao.blockchain.ReadableBsqBlockChain;
import bisq.core.dao.blockchain.WritableBsqBlockChain;
import bisq.core.dao.blockchain.vo.TxOutput;
import bisq.core.dao.proposal.Proposal;

import bisq.common.crypto.Encryption;
import bisq.common.crypto.Hash;

import javax.crypto.SecretKey;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IssuanceConsensus {

    //TODO set higher value
    private static final int QUORUM = 1000; // 10 BSQ

    public static SecretKey getSecretKey(byte[] opReturnData) {
        byte[] secretKeyAsBytes = Arrays.copyOfRange(opReturnData, 22, 54);
        return Encryption.getSecretKeyFromBytes(secretKeyAsBytes);
    }

    public static byte[] getVoteListHash(byte[] opReturnData) {
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
        readableBsqBlockChain.getCompReqIssuanceTxOutputs()
                .forEach(txOutput -> txOutputsByTxIdMap.put(txOutput.getTxId(), txOutput));

        stakeByProposalMap.entrySet().stream()
                .forEach(entry -> {
                    Proposal proposal = entry.getKey();
                    int stakeResult = entry.getValue();
                    if (stakeResult >= QUORUM) {
                        TxOutput txOutput = txOutputsByTxIdMap.get(proposal.getTxId());
                        writableBsqBlockChain.issueBsq(txOutput);
                    } else {
                        log.warn("We got a successful vote result but did not reach the quorum. stake={}, quorum={}",
                                stakeResult, QUORUM);
                    }
                });


    }
}
