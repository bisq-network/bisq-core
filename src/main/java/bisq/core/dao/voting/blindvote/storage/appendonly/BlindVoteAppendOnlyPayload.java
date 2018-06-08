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

package bisq.core.dao.voting.blindvote.storage.appendonly;

import bisq.core.dao.voting.ballot.vote.VoteConsensusCritical;
import bisq.core.dao.voting.blindvote.BlindVote;

import bisq.network.p2p.storage.payload.CapabilityRequiringPayload;
import bisq.network.p2p.storage.payload.DateTolerantPayload;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;

import bisq.common.app.Capabilities;
import bisq.common.crypto.Hash;
import bisq.common.proto.persistable.PersistableEnvelope;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.ByteString;

import org.apache.commons.lang3.ArrayUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Wrapper for proposal to be stored in the append-only BlindVoteAppendOnlyStore storage.
 *
 * Data size: 185 bytes
 */
@Immutable
@Slf4j
@Getter
@EqualsAndHashCode
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class BlindVoteAppendOnlyPayload implements PersistableNetworkPayload, PersistableEnvelope, DateTolerantPayload,
        CapabilityRequiringPayload, VoteConsensusCritical {
    private static final long TOLERANCE = TimeUnit.HOURS.toMillis(5); // +/- 5 hours

    private BlindVote blindVote;
    private final long date;            // 8 byte
    protected final byte[] hash;        // 20 byte

    public BlindVoteAppendOnlyPayload(BlindVote blindVote) {
        this(blindVote,
                new Date().getTime(),
                null);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private BlindVoteAppendOnlyPayload(BlindVote blindVote, long date, @Nullable byte[] hash) {
        this.blindVote = blindVote;
        this.date = date;

        if (hash == null) {
            // We combine hash of payload + blockHash to get hash used as storage key.
            this.hash = Hash.getRipemd160hash(ArrayUtils.addAll(blindVote.toProtoMessage().toByteArray()));
        } else {
            this.hash = hash;
        }
    }

    private PB.BlindVoteAppendOnlyPayload.Builder getBlindVoteBuilder() {
        return PB.BlindVoteAppendOnlyPayload.newBuilder()
                .setBlindVote(blindVote.toProtoMessage())
                .setDate(date)
                .setHash(ByteString.copyFrom(hash));
    }

    @Override
    public PB.PersistableNetworkPayload toProtoMessage() {
        return PB.PersistableNetworkPayload.newBuilder().setBlindVoteAppendOnlyPayload(getBlindVoteBuilder()).build();
    }

    public static BlindVoteAppendOnlyPayload fromProto(PB.BlindVoteAppendOnlyPayload proto) {
        return new BlindVoteAppendOnlyPayload(BlindVote.fromProto(proto.getBlindVote()),
                proto.getDate(),
                proto.getHash().toByteArray());
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DateTolerantPayload
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public boolean isDateInTolerance() {
        // We don't allow older or newer then 5 hours.
        // Preventing forward dating is also important to protect against a sophisticated attack
        return Math.abs(new Date().getTime() - date) <= TOLERANCE;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // CapabilityRequiringPayload
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public List<Integer> getRequiredCapabilities() {
        return new ArrayList<>(Collections.singletonList(
                Capabilities.Capability.BLIND_VOTE.ordinal()
        ));
    }
}
