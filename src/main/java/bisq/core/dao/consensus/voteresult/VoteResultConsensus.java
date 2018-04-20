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

package bisq.core.dao.consensus.voteresult;

import bisq.common.crypto.CryptoException;
import bisq.common.crypto.Encryption;
import bisq.common.util.Utilities;

import javax.crypto.SecretKey;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public class VoteResultConsensus {
    // Hash of the list of Blind votes is 20 bytes after version and type bytes
    public static byte[] getHashOfBlindVoteList(byte[] opReturnData) {
        return Arrays.copyOfRange(opReturnData, 2, 22);
    }

    public static byte[] decryptProposalList(byte[] encryptedProposalList, SecretKey secretKey) throws CryptoException {
        return Encryption.decrypt(encryptedProposalList, secretKey);
    }

    //TODO add tests
    // We compare first by stake and in case we have multiple entries with same stake we use the
    // hex encoded hashOfProposalList for comparision
    @Nullable
    public static byte[] getMajorityHash(List<VoteResultService.HashWithStake> hashWithStakeList) {
        hashWithStakeList.sort(Comparator.comparingLong(VoteResultService.HashWithStake::getStake).reversed()
                .thenComparing(o -> Utilities.encodeToHex(o.getHashOfProposalList())));

        return hashWithStakeList.isEmpty() ? null : hashWithStakeList.get(0).getHashOfProposalList();
    }

    // Key is stored after version and type bytes and list of Blind votes. It has 16 bytes
    public static SecretKey getSecretKey(byte[] opReturnData) {
        byte[] secretKeyAsBytes = Arrays.copyOfRange(opReturnData, 22, 38);
        return Encryption.getSecretKeyFromBytes(secretKeyAsBytes);
    }
}
