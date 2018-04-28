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

package bisq.core.dao.voting.blindvote;

import bisq.core.dao.voting.ballot.vote.VoteConsensusCritical;

import bisq.network.p2p.storage.payload.PersistableNetworkPayload;

import bisq.common.crypto.Hash;
import bisq.common.proto.persistable.PersistableEnvelope;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.ByteString;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.concurrent.Immutable;

/**
 * Wrapper for proposal to be stored in the append-only BlindVoteAppendOnlyStore storage.
 */
@Immutable
@Slf4j
@Getter
@EqualsAndHashCode
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class BlindVoteAppendOnlyPayload implements PersistableNetworkPayload, PersistableEnvelope, VoteConsensusCritical {
    private BlindVote blindVote;
    protected final byte[] hash;

    public BlindVoteAppendOnlyPayload(BlindVote blindVote) {
        this(blindVote, Hash.getSha256Ripemd160hash(blindVote.toProtoMessage().toByteArray()));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private BlindVoteAppendOnlyPayload(BlindVote blindVote, byte[] hash) {
        this.blindVote = blindVote;
        this.hash = hash;
    }

    private PB.BlindVoteAppendOnlyPayload.Builder getBlindVoteBuilder() {
        return PB.BlindVoteAppendOnlyPayload.newBuilder()
                .setBlindVote(blindVote.toProtoMessage())
                .setHash(ByteString.copyFrom(hash));
    }

    @Override
    public PB.PersistableNetworkPayload toProtoMessage() {
        return PB.PersistableNetworkPayload.newBuilder().setBlindVoteAppendOnlyPayload(getBlindVoteBuilder()).build();
    }

    public static BlindVoteAppendOnlyPayload fromProto(PB.BlindVoteAppendOnlyPayload proto) {
        return new BlindVoteAppendOnlyPayload(BlindVote.fromProto(proto.getBlindVote()), proto.getHash().toByteArray());
    }

    public PB.BlindVoteAppendOnlyPayload toProtoBlindVotePayload() {
        return getBlindVoteBuilder().build();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistableNetworkPayload
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public boolean verifyHashSize() {
        return hash.length == 20;
    }

    @Override
    public byte[] getHash() {
        return hash;
    }
}
