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

package bisq.core.dao.vote.proposal;

import bisq.core.dao.blockchain.ReadableBsqBlockChain;
import bisq.core.dao.consensus.OpReturnType;
import bisq.core.dao.param.DaoParam;
import bisq.core.dao.param.DaoParamService;

import bisq.common.app.Version;
import bisq.common.crypto.Hash;

import org.bitcoinj.core.Coin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProposalConsensus {
    public static Coin getFee(DaoParamService daoParamService, ReadableBsqBlockChain readableBsqBlockChain) {
        return Coin.valueOf(daoParamService.getDaoParamValue(DaoParam.PROPOSAL_FEE,
                readableBsqBlockChain.getChainHeadHeight()));
    }

    public static byte[] getHashOfPayload(ProposalPayload payload) {
        final byte[] bytes = payload.toProtoMessage().toByteArray();
        return Hash.getSha256Ripemd160hash(bytes);
    }

    public static byte[] getOpReturnData(byte[] hashOfPayload) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            outputStream.write(OpReturnType.PROPOSAL.getType());
            outputStream.write(Version.PROPOSAL);
            outputStream.write(hashOfPayload);
            return outputStream.toByteArray();
        } catch (IOException e) {
            // Not expected to happen ever
            e.printStackTrace();
            log.error(e.toString());
            return new byte[0];
        }
    }

    public static int getMaxLengthDescriptionText() {
        return 100;
    }

    public static boolean isDescriptionSizeValid(String description) {
        return description.length() <= getMaxLengthDescriptionText();
    }
}
