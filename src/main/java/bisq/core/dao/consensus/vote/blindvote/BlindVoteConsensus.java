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

package bisq.core.dao.consensus.vote.blindvote;

import bisq.core.dao.consensus.OpReturnType;
import bisq.core.dao.consensus.vote.proposal.Ballot;
import bisq.core.dao.consensus.vote.proposal.BallotList;
import bisq.core.dao.consensus.vote.proposal.param.ChangeParamService;
import bisq.core.dao.consensus.vote.proposal.param.Param;

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
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * All consensus critical aspects are handled here.
 */
@Slf4j
public class BlindVoteConsensus {
    // Sorted by TxId
    public static void sortProposalList(List<Ballot> ballots) {
        ballots.sort(Comparator.comparing(Ballot::getTxId));
        log.info("Sorted proposalList for blind vote: " + ballots.stream()
                .map(Ballot::getUid)
                .collect(Collectors.toList()));
    }

    // 128 bit AES key is good enough for our use case
    public static SecretKey getSecretKey() {
        return Encryption.generateSecretKey(128);
    }

    public static byte[] getEncryptedBallotList(BallotList ballotList, SecretKey secretKey) throws CryptoException {
        final byte[] payload = ballotList.toProtoMessage().toByteArray();
        final byte[] encryptedProposalList = Encryption.encrypt(payload, secretKey);
        log.info("encryptedProposalList: " + Utilities.bytesAsHexString(encryptedProposalList));
        return encryptedProposalList;

           /*  byte[] decryptedProposalList = Encryption.decrypt(encryptedProposalList, secretKey);
        try {
            PB.PersistableEnvelope proto = PB.PersistableEnvelope.parseFrom(decryptedProposalList);
            PersistableEnvelope decrypted = BallotList.fromProto(proto.getBallotList());
            log.error(decrypted.toString());
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }*/
    }

    public static byte[] getHashOfEncryptedProposalList(byte[] encryptedProposalList) {
        return Hash.getSha256Ripemd160hash(encryptedProposalList);
    }

    public static byte[] getOpReturnData(byte[] hash) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            outputStream.write(OpReturnType.BLIND_VOTE.getType());
            outputStream.write(Version.BLIND_VOTE_VERSION);
            outputStream.write(hash);
            final byte[] bytes = outputStream.toByteArray();
            log.info("OpReturnData: " + Utilities.bytesAsHexString(bytes));
            return bytes;
        } catch (IOException e) {
            // Not expected to happen ever
            e.printStackTrace();
            log.error(e.toString());
            throw e;
        }
    }


    public static Coin getFee(ChangeParamService changeParamService, int chainHeadHeight) {
        final Coin fee = Coin.valueOf(changeParamService.getDaoParamValue(Param.BLIND_VOTE_FEE, chainHeadHeight));
        log.info("Fee for blind vote: " + fee);
        return fee;
    }

    public static void sortBlindVoteList(List<BlindVote> list) {
        list.sort(Comparator.comparing(BlindVote::getTxId));
        log.info("Sorted blindVote txId list: " + list.stream()
                .map(BlindVote::getTxId)
                .collect(Collectors.toList()));
    }
}
