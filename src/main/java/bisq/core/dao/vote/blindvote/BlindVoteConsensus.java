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

package bisq.core.dao.vote.blindvote;

import bisq.core.dao.blockchain.ReadableBsqBlockChain;
import bisq.core.dao.consensus.OpReturnType;
import bisq.core.dao.param.DaoParam;
import bisq.core.dao.param.DaoParamService;
import bisq.core.dao.vote.proposal.Proposal;
import bisq.core.dao.vote.proposal.ProposalList;

import bisq.common.app.Version;
import bisq.common.crypto.CryptoException;
import bisq.common.crypto.Encryption;
import bisq.common.crypto.Hash;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Coin;

import javax.crypto.SecretKey;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.util.Comparator;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * All consensus critical aspects are handled here.
 */
@Slf4j
public class BlindVoteConsensus {
    public static void sortProposalList(List<Proposal> proposals) {
        proposals.sort(Comparator.comparing(Proposal::getTxId));
    }

    // 128 bit AES key is good enough for our use case
    public static SecretKey getSecretKey() {
        return Encryption.generateSecretKey(128);
    }

    // TODO add test
    public static byte[] getEncryptedProposalList(ProposalList proposalList, SecretKey secretKey) throws CryptoException {
        final byte[] payload = proposalList.toProtoMessage().toByteArray();
        return Encryption.encrypt(payload, secretKey);

           /*  byte[] decryptedProposalList = Encryption.decrypt(encryptedProposalList, secretKey);
        try {
            PB.PersistableEnvelope proto = PB.PersistableEnvelope.parseFrom(decryptedProposalList);
            PersistableEnvelope decrypted = ProposalList.fromProto(proto.getProposalList());
            log.error(decrypted.toString());
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }*/
    }

    public static byte[] getOpReturnData(byte[] encryptedProposalList) throws IOException {
        log.info("encryptedProposalList " + Utilities.bytesAsHexString(encryptedProposalList));
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            outputStream.write(OpReturnType.BLIND_VOTE.getType());
            outputStream.write(Version.BLIND_VOTE_VERSION);
            final byte[] hash = Hash.getSha256Ripemd160hash(encryptedProposalList);
            log.info("Sha256Ripemd160 hash of encryptedProposalList " + Utilities.bytesAsHexString(hash));
            outputStream.write(hash);
            return outputStream.toByteArray();
        } catch (IOException e) {
            // Not expected to happen ever
            e.printStackTrace();
            log.error(e.toString());
            throw e;
        }
    }

    public static Coin getFee(DaoParamService daoParamService, ReadableBsqBlockChain readableBsqBlockChain) {
        return Coin.valueOf(daoParamService.getDaoParamValue(DaoParam.BLIND_VOTE_FEE,
                readableBsqBlockChain.getChainHeadHeight()));
    }

    public static void sortedBlindVoteList(List<BlindVote> list) {
        list.sort(Comparator.comparing(BlindVote::getTxId));
    }
}
