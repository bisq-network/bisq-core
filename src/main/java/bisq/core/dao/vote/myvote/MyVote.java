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

package bisq.core.dao.vote.myvote;

import bisq.core.dao.vote.blindvote.BlindVote;
import bisq.core.dao.vote.proposal.ProposalList;

import bisq.common.crypto.Encryption;
import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.util.JsonExclude;
import bisq.common.util.Utilities;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.ByteString;

import javax.crypto.SecretKey;

import java.util.Date;
import java.util.Optional;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@EqualsAndHashCode
@Slf4j
@Data
public class MyVote implements PersistablePayload {
    // TODO do we need to store proposalList - it could be created by decrypting blindVote.encryptedProposalList
    // with secretKey
    private final ProposalList proposalList;
    private final byte[] secretKeyEncoded;
    private final BlindVote blindVote;
    private final long date;
    @Nullable
    private String revealTxId;

    // Used just for caching
    @JsonExclude
    @Nullable
    private transient SecretKey secretKey;

    public MyVote(ProposalList proposalList,
                  byte[] secretKeyEncoded,
                  BlindVote blindVote) {
        this(proposalList,
                secretKeyEncoded,
                blindVote,
                new Date().getTime(),
                null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private MyVote(ProposalList proposalList,
                   byte[] secretKeyEncoded,
                   BlindVote blindVote,
                   long date,
                   @Nullable String revealTxId) {
        this.proposalList = proposalList;
        this.secretKeyEncoded = secretKeyEncoded;
        this.blindVote = blindVote;
        this.date = date;
        this.revealTxId = revealTxId;
    }

    @Override
    public PB.MyVote toProtoMessage() {
        final PB.MyVote.Builder builder = PB.MyVote.newBuilder()
                .setBlindVote(blindVote.getBuilder())
                .setProposalList(proposalList.getBuilder())
                .setSecretKeyEncoded(ByteString.copyFrom(secretKeyEncoded))
                .setDate(date);
        Optional.ofNullable(revealTxId).ifPresent(builder::setRevealTxId);
        return builder.build();
    }

    public static MyVote fromProto(PB.MyVote proto) {
        return new MyVote(ProposalList.fromProto(proto.getProposalList()),
                proto.getSecretKeyEncoded().toByteArray(),
                BlindVote.fromProto(proto.getBlindVote()),
                proto.getDate(),
                proto.getRevealTxId().isEmpty() ? null : proto.getRevealTxId());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SecretKey getSecretKey() {
        if (secretKey == null)
            secretKey = Encryption.getSecretKeyFromBytes(secretKeyEncoded);
        return secretKey;
    }

    public String getTxId() {
        return blindVote.getTxId();
    }


    @Override
    public String toString() {
        return "MyVote{" +
                "\n     proposalList=" + proposalList +
                ",\n     secretKeyEncoded=" + Utilities.bytesAsHexString(secretKeyEncoded) +
                ",\n     blindVote=" + blindVote +
                ",\n     date=" + date +
                ",\n     revealTxId='" + revealTxId + '\'' +
                ",\n     secretKey=" + secretKey +
                "\n}";
    }
}
