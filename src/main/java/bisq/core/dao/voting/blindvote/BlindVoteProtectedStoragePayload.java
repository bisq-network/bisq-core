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

import bisq.network.p2p.storage.payload.CapabilityRequiringPayload;
import bisq.network.p2p.storage.payload.LazyProcessedPayload;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.app.Capabilities;
import bisq.common.crypto.Sig;
import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.util.JsonExclude;
import bisq.common.util.Utilities;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.ByteString;

import org.springframework.util.CollectionUtils;

import java.security.PublicKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Wrapper for blindVote sent over wire.
 */
@Immutable
@Slf4j
@Value
public final class BlindVoteProtectedStoragePayload implements LazyProcessedPayload, ProtectedStoragePayload, PersistablePayload,
        CapabilityRequiringPayload {

    private final BlindVote blindVote;
    private final byte[] ownerPubKeyEncoded;

    // Used just for caching, not included in PB.
    @JsonExclude
    private final transient PublicKey ownerPubKey;

    // Should be only used in emergency case if we need to add data but do not want to break backward compatibility
    // at the P2P network storage checks. The hash of the object will be used to verify if the data is valid. Any new
    // field in a class would break that hash and therefore break the storage mechanism.
    @Nullable
    private Map<String, String> extraDataMap;


    public BlindVoteProtectedStoragePayload(BlindVote blindVote,
                                            PublicKey ownerPubKey) {
        this(blindVote, Sig.getPublicKeyBytes(ownerPubKey), null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private BlindVoteProtectedStoragePayload(BlindVote blindVote,
                                             byte[] ownerPubKeyEncoded,
                                             @Nullable Map<String, String> extraDataMap) {
        this.blindVote = blindVote;
        this.ownerPubKeyEncoded = ownerPubKeyEncoded;
        this.extraDataMap = extraDataMap;

        ownerPubKey = Sig.getPublicKeyFromBytes(ownerPubKeyEncoded);
    }

    // Used for sending over the network
    @Override
    public PB.StoragePayload toProtoMessage() {
        final PB.BlindVoteProtectedStoragePayload.Builder builder = getBuilder();
        return PB.StoragePayload.newBuilder().setBlindVoteProtectedStoragePayload(builder).build();
    }

    @NotNull
    public PB.BlindVoteProtectedStoragePayload.Builder getBuilder() {
        final PB.BlindVoteProtectedStoragePayload.Builder builder = PB.BlindVoteProtectedStoragePayload.newBuilder()
                .setBlindVote(blindVote.getBuilder())
                .setOwnerPubKeyAsEncoded(ByteString.copyFrom(ownerPubKeyEncoded));
        Optional.ofNullable(getExtraDataMap()).ifPresent(builder::putAllExtraData);
        return builder;
    }

    public static BlindVoteProtectedStoragePayload fromProto(PB.BlindVoteProtectedStoragePayload proto) {
        return new BlindVoteProtectedStoragePayload(BlindVote.fromProto(proto.getBlindVote()),
                proto.getOwnerPubKeyAsEncoded().toByteArray(),
                CollectionUtils.isEmpty(proto.getExtraDataMap()) ? null : proto.getExtraDataMap());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public PublicKey getOwnerPubKey() {
        return ownerPubKey;
    }

    @Override
    @Nullable
    public Map<String, String> getExtraDataMap() {
        return extraDataMap;
    }

    // Pre 0.7 version don't know the new message type and throw an error which leads to disconnecting the peer.
    @Override
    public List<Integer> getRequiredCapabilities() {
        return new ArrayList<>(Collections.singletonList(
                Capabilities.Capability.BLIND_VOTE.ordinal()
        ));
    }

    public String toString() {
        return "BlindVoteProtectedStoragePayload{" +
                ",\n     blindVote='" + blindVote + '\'' +
                ",\n     ownerPubKeyEncoded=" + Utilities.bytesAsHexString(ownerPubKeyEncoded) +
                ",\n     extraDataMap=" + extraDataMap +
                "\n}";
    }
}
