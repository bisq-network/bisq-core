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

package bisq.core.dao.voting.proposal.storage.appendonly;

import bisq.core.dao.voting.ballot.vote.VoteConsensusCritical;
import bisq.core.dao.voting.proposal.Proposal;

import bisq.network.p2p.storage.payload.CapabilityRequiringPayload;
import bisq.network.p2p.storage.payload.DateTolerantPayload;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;

import bisq.common.app.Capabilities;
import bisq.common.crypto.Hash;
import bisq.common.proto.persistable.PersistableEnvelope;
import bisq.common.util.Utilities;

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
 * Wrapper for proposal to be stored in the append-only ProposalStore storage.
 * Data size: about 312 bytes
 */
@Immutable
@Slf4j
@Getter
@EqualsAndHashCode
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
//TODO add CapabilityRequiringPayload
public class ProposalPayload implements PersistableNetworkPayload, PersistableEnvelope, DateTolerantPayload,
        CapabilityRequiringPayload, VoteConsensusCritical {
    private static final long TOLERANCE = TimeUnit.HOURS.toMillis(5); // +/- 5 hours

    private Proposal proposal;
    private final long date;            // 8 byte
    private final byte[] blockHash;     // 32 byte hash
    protected final byte[] hash;        // 20 byte

    public ProposalPayload(Proposal proposal, String blockHash) {
        this(proposal,
                new Date().getTime(),
                Utilities.decodeFromHex(blockHash),
                null);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private ProposalPayload(Proposal proposal, long date, byte[] blockHash, @Nullable byte[] hash) {
        this.proposal = proposal;
        this.date = date;
        this.blockHash = blockHash;

        if (hash == null) {
            // We combine hash of payload + blockHash to get hash used as storage key.
            this.hash = Hash.getRipemd160hash(ArrayUtils.addAll(proposal.toProtoMessage().toByteArray(), blockHash));
        } else {
            this.hash = hash;
        }
    }

    private PB.ProposalPayload.Builder getProposalBuilder() {
        return PB.ProposalPayload.newBuilder()
                .setProposal(proposal.toProtoMessage())
                .setDate(date)
                .setBlockHash(ByteString.copyFrom(blockHash))
                .setHash(ByteString.copyFrom(hash));
    }

    @Override
    public PB.PersistableNetworkPayload toProtoMessage() {
        return PB.PersistableNetworkPayload.newBuilder()
                .setProposalPayload(getProposalBuilder())
                .build();
    }

    public static ProposalPayload fromProto(PB.ProposalPayload proto) {
        return new ProposalPayload(Proposal.fromProto(proto.getProposal()),
                proto.getDate(),
                proto.getBlockHash().toByteArray(),
                proto.getHash().toByteArray());
    }

    public PB.ProposalPayload toProtoProposalPayload() {
        return getProposalBuilder().build();
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
        // We don't allow older or newer then 1 day.
        // Preventing forward dating is also important to protect against a sophisticated attack
        return Math.abs(new Date().getTime() - date) <= TOLERANCE;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // CapabilityRequiringPayload
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public List<Integer> getRequiredCapabilities() {
        return new ArrayList<>(Collections.singletonList(
                Capabilities.Capability.PROPOSAL.ordinal()
        ));
    }

    @Override
    public String toString() {
        return "ProposalPayload{" +
                "\n     proposal=" + proposal +
                ",\n     date=" + date +
                ",\n     blockHash=" + Utilities.bytesAsHexString(blockHash) +
                ",\n     hash=" + Utilities.bytesAsHexString(hash) +
                "\n}";
    }
}
