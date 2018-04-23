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

package bisq.core.dao.proposal;

import bisq.network.p2p.storage.payload.CapabilityRequiringPayload;
import bisq.network.p2p.storage.payload.LazyProcessedPayload;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.app.Capabilities;
import bisq.common.crypto.Sig;
import bisq.common.proto.persistable.PersistablePayload;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.ByteString;

import org.springframework.util.CollectionUtils;

import java.security.PublicKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Proposal is sent over wire as well as it gets persisted.
 * <p>
 * We persist all Proposal data in the PersistedEntryMap.
 * Data size on disk for one item is: about 743 bytes (443 bytes is for ownerPubKeyEncoded)
 * As Proposals gets persisted in the Blocks of the State as well we could consider pruning of old data.
 */
//TODO separate value object with p2p network data
@Immutable
@Slf4j
@Getter
@EqualsAndHashCode
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class ProposalPayload implements LazyProcessedPayload, ProtectedStoragePayload, PersistablePayload,
        CapabilityRequiringPayload {

    protected final Proposal proposal;
    protected final byte[] ownerPubKeyEncoded;

    // Should be only used in emergency case if we need to add data but do not want to break backward compatibility
    // at the P2P network storage checks. The hash of the object will be used to verify if the data is valid. Any new
    // field in a class would break that hash and therefore break the storage mechanism.
    @Nullable
    protected final Map<String, String> extraDataMap;

    // Used just for caching. Don't persist.
    private final transient PublicKey ownerPubKey;

    public ProposalPayload(Proposal proposal,
                           PublicKey ownerPublicKey) {
        this(proposal, Sig.getPublicKeyBytes(ownerPublicKey), null);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected ProposalPayload(Proposal proposal,
                              byte[] ownerPubPubKeyEncoded,
                              @Nullable Map<String, String> extraDataMap) {
        this.proposal = proposal;
        this.ownerPubKeyEncoded = ownerPubPubKeyEncoded;
        this.extraDataMap = extraDataMap;

        ownerPubKey = Sig.getPublicKeyFromBytes(ownerPubKeyEncoded);
    }

    public PB.ProposalPayload.Builder getProposalPayloadBuilder() {
        final PB.ProposalPayload.Builder builder = PB.ProposalPayload.newBuilder()
                .setProposal(proposal.getProposalBuilder())
                .setOwnerPubKeyEncoded(ByteString.copyFrom(ownerPubKeyEncoded));
        Optional.ofNullable(extraDataMap).ifPresent(builder::putAllExtraData);
        return builder;
    }

    @Override
    public PB.StoragePayload toProtoMessage() {
        return PB.StoragePayload.newBuilder().setProposalPayload(getProposalPayloadBuilder()).build();
    }

    public static ProposalPayload fromProto(PB.ProposalPayload proto) {
        return new ProposalPayload(Proposal.fromProto(proto.getProposal()),
                proto.getOwnerPubKeyEncoded().toByteArray(),
                CollectionUtils.isEmpty(proto.getExtraDataMap()) ? null : proto.getExtraDataMap());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public PublicKey getOwnerPubKey() {
        return ownerPubKey;
    }

    // Pre 0.6 version don't know the new message type and throw an error which leads to disconnecting the peer.
    @Override
    public List<Integer> getRequiredCapabilities() {
        return new ArrayList<>(Collections.singletonList(
                Capabilities.Capability.PROPOSAL.ordinal()
        ));
    }
}
