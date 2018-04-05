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

import bisq.common.crypto.CryptoException;
import bisq.common.crypto.Encryption;

import javax.crypto.SecretKey;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public class IssuanceConsensus {
    // Value in satoshi
    public static final long DEFAULT_QUORUM = 100L; // 1 BSQ

    // We want to avoid type double and use a long instead as that is used for all the param fields.
    // 50 % is 5000 to provide precision of 2 (e.g. 50.00 %).
    public static final long DEFAULT_VOTE_THRESHOLD = 50L * 100;


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
}
