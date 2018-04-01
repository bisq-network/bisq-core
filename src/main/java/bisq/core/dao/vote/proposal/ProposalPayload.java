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

import bisq.core.dao.vote.proposal.compensation.CompensationRequestPayload;
import bisq.core.dao.vote.proposal.generic.GenericProposalPayload;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.storage.payload.CapabilityRequiringPayload;
import bisq.network.p2p.storage.payload.LazyProcessedPayload;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.app.Capabilities;
import bisq.common.crypto.Sig;
import bisq.common.proto.ProtobufferException;
import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.util.JsonExclude;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.ByteString;

import java.security.PublicKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static org.apache.commons.lang3.Validate.notEmpty;

/**
 * Payload is sent over wire as well as it gets persisted.
 * <p>
 * We persist all ProposalPayload data in PersistableNetworkPayloadMap.
 * Data size on disk for one item is: 1184 bytes (884 bytes is ownerPubPubKeyAsHex, 442 bytes is in byte array form ->
 * TODO change data type)
 * As there are not 1000s of proposals we consider that acceptable.
 */
@Slf4j
@Data
public abstract class ProposalPayload implements LazyProcessedPayload, ProtectedStoragePayload, PersistablePayload,
        CapabilityRequiringPayload {

    protected final String uid;
    protected final String name;
    protected final String title;
    protected final String description;
    protected final String link;
    protected final String nodeAddress;
    protected byte[] ownerPubKeyEncoded;
    @Nullable
    @JsonExclude
    protected String txId;

    protected final byte version;
    protected final long creationDate;

    // Should be only used in emergency case if we need to add data but do not want to break backward compatibility
    // at the P2P network storage checks. The hash of the object will be used to verify if the data is valid. Any new
    // field in a class would break that hash and therefore break the storage mechanism.
    @Nullable
    protected Map<String, String> extraDataMap;

    // Used just for caching
    @JsonExclude
    @Nullable
    private transient PublicKey ownerPubKey;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected ProposalPayload(String uid,
                              String name,
                              String title,
                              String description,
                              String link,
                              String nodeAddress,
                              byte[] ownerPubPubKeyEncoded,
                              byte version,
                              long creationDate,
                              @Nullable String txId,
                              @Nullable Map<String, String> extraDataMap) {
        this.uid = uid;
        this.name = name;
        this.title = title;
        this.description = description;
        this.link = link;
        this.nodeAddress = nodeAddress;
        this.ownerPubKeyEncoded = ownerPubPubKeyEncoded;
        this.version = version;
        this.creationDate = creationDate;
        this.txId = txId;
        this.extraDataMap = extraDataMap;
    }

    public PB.ProposalPayload.Builder getPayloadBuilder() {
        final PB.ProposalPayload.Builder builder = PB.ProposalPayload.newBuilder()
                .setUid(uid)
                .setName(name)
                .setTitle(title)
                .setDescription(description)
                .setLink(link)
                .setNodeAddress(nodeAddress)
                .setOwnerPubKeyEncoded(ByteString.copyFrom(ownerPubKeyEncoded))
                .setVersion(version)
                .setCreationDate(creationDate);
        Optional.ofNullable(txId).ifPresent(builder::setTxId);
        Optional.ofNullable(extraDataMap).ifPresent(builder::putAllExtraData);
        return builder;
    }

    @Override
    public PB.StoragePayload toProtoMessage() {
        return PB.StoragePayload.newBuilder().setProposalPayload(getPayloadBuilder()).build();
    }

    //TODO add other proposal types
    public static ProposalPayload fromProto(PB.ProposalPayload proto) {
        switch (proto.getMessageCase()) {
            case COMPENSATION_REQUEST_PAYLOAD:
                return CompensationRequestPayload.fromProto(proto);
            case GENERIC_PROPOSAL_PAYLOAD:
                return GenericProposalPayload.fromProto(proto);
            default:
                throw new ProtobufferException("Unknown message case: " + proto.getMessageCase());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public PublicKey getOwnerPubKey() {
        if (ownerPubKey == null)
            ownerPubKey = Sig.getPublicKeyFromBytes(ownerPubKeyEncoded);

        return ownerPubKey;
    }

    // Pre 0.6 version don't know the new message type and throw an error which leads to disconnecting the peer.
    @Override
    public List<Integer> getRequiredCapabilities() {
        return new ArrayList<>(Collections.singletonList(
                Capabilities.Capability.COMP_REQUEST.ordinal()
        ));
    }

    public void validate() throws ValidationException {
        try {
            notEmpty(name, "name must not be empty");
            notEmpty(title, "title must not be empty");
            notEmpty(description, "description must not be empty");
            notEmpty(link, "link must not be empty");
            notEmpty(nodeAddress, "nodeAddress must not be empty");

            //TODO add more checks
        } catch (Throwable throwable) {
            throw new ValidationException(throwable);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Date getCreationDate() {
        return new Date(creationDate);
    }

    public NodeAddress getNodeAddress() {
        return new NodeAddress(nodeAddress);
    }

    public String getShortId() {
        return uid.substring(0, 8);
    }

    public abstract ProposalType getType();
}
