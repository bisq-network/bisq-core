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

import bisq.core.dao.vote.VoteConsensusCritical;

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

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

@EqualsAndHashCode
@Slf4j
@Data
public class BlindVote implements LazyProcessedPayload, ProtectedStoragePayload, PersistablePayload,
        CapabilityRequiringPayload, VoteConsensusCritical {

    public static BlindVote clone(BlindVote blindVote) {
        return new BlindVote(blindVote.encryptedProposalList,
                blindVote.getTxId(),
                blindVote.getStake(),
                blindVote.getOwnerPubKeyEncoded(),
                blindVote.extraDataMap);
    }

    private final byte[] encryptedProposalList;
    private final String txId;
    // Stake is revealed in the BSQ tx anyway as output value so no reason to encrypt it here.
    private long stake;
    private final byte[] ownerPubKeyEncoded;

    // Used just for caching
    @JsonExclude
    @Nullable
    private transient PublicKey ownerPubKey;

    // Should be only used in emergency case if we need to add data but do not want to break backward compatibility
    // at the P2P network storage checks. The hash of the object will be used to verify if the data is valid. Any new
    // field in a class would break that hash and therefore break the storage mechanism.
    @Nullable
    private Map<String, String> extraDataMap;


    BlindVote(byte[] encryptedProposalList,
              String txId,
              long stake,
              PublicKey ownerPubKey) {
        this(encryptedProposalList, txId, stake, Sig.getPublicKeyBytes(ownerPubKey), null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private BlindVote(byte[] encryptedProposalList,
                      String txId,
                      long stake,
                      byte[] ownerPubKeyEncoded,
                      @Nullable Map<String, String> extraDataMap) {
        this.encryptedProposalList = encryptedProposalList;
        this.txId = txId;
        this.stake = stake;
        this.ownerPubKeyEncoded = ownerPubKeyEncoded;
        this.extraDataMap = extraDataMap;
    }

    // Used for sending over the network
    @Override
    public PB.StoragePayload toProtoMessage() {
        final PB.BlindVote.Builder builder = getBuilder();
        return PB.StoragePayload.newBuilder().setBlindVote(builder).build();
    }

    // Used for local persistence
    public PB.BlindVote toBlindVote() {
        return getBuilder().build();
    }

    @NotNull
    public PB.BlindVote.Builder getBuilder() {
        final PB.BlindVote.Builder builder = PB.BlindVote.newBuilder()
                .setEncryptedProposalList(ByteString.copyFrom(encryptedProposalList))
                .setTxId(txId)
                .setStake(stake)
                .setOwnerPubKeyAsEncoded(ByteString.copyFrom(ownerPubKeyEncoded));
        Optional.ofNullable(getExtraDataMap()).ifPresent(builder::putAllExtraData);
        return builder;
    }

    public static BlindVote fromProto(PB.BlindVote proto) {
        return new BlindVote(proto.getEncryptedProposalList().toByteArray(),
                proto.getTxId(),
                proto.getStake(),
                proto.getOwnerPubKeyAsEncoded().toByteArray(),
                CollectionUtils.isEmpty(proto.getExtraDataMap()) ? null : proto.getExtraDataMap());
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

    @Nullable
    @Override
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


    @Override
    public String toString() {
        return "BlindVote{" +
                "\n     encryptedProposalList=" + Utilities.bytesAsHexString(encryptedProposalList) +
                ",\n     txId='" + txId + '\'' +
                ",\n     stake=" + stake +
                ",\n     ownerPubKeyEncoded=" + Utilities.bytesAsHexString(ownerPubKeyEncoded) +
                ",\n     ownerPubKey=" + ownerPubKey +
                ",\n     extraDataMap=" + extraDataMap +
                "\n}";
    }
}
