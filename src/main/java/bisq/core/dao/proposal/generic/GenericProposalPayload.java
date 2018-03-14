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

package bisq.core.dao.proposal.generic;

import bisq.core.dao.proposal.ProposalPayload;
import bisq.core.dao.proposal.ProposalType;

import bisq.network.p2p.NodeAddress;

import bisq.common.app.Version;

import io.bisq.generated.protobuffer.PB;

import org.springframework.util.CollectionUtils;

import org.bitcoinj.core.Utils;

import java.security.PublicKey;

import java.util.Date;
import java.util.Map;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * Payload for generic proposals.
 */
@Slf4j
@Data
public final class GenericProposalPayload extends ProposalPayload {

    public GenericProposalPayload(String uid,
                                  String name,
                                  String title,
                                  String description,
                                  String link,
                                  NodeAddress nodeAddress,
                                  PublicKey ownerPubKey,
                                  Date creationDate) {
        super(uid,
                name,
                title,
                description,
                link,
                nodeAddress.getFullAddress(),
                Utils.HEX.encode(ownerPubKey.getEncoded()),
                Version.COMPENSATION_REQUEST_VERSION,
                creationDate.getTime(),
                null,
                null,
                null,
                null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private GenericProposalPayload(String uid,
                                   String name,
                                   String title,
                                   String description,
                                   String link,
                                   String nodeAddress,
                                   String ownerPubPubKeyAsHex,
                                   byte version,
                                   long creationDate,
                                   String signature,
                                   String txId,
                                   @Nullable byte[] hash,
                                   @Nullable Map<String, String> extraDataMap) {
        super(uid,
                name,
                title,
                description,
                link,
                nodeAddress,
                ownerPubPubKeyAsHex,
                version,
                creationDate,
                signature,
                txId,
                hash,
                extraDataMap);
    }

    @Override
    public PB.ProposalPayload.Builder getPayloadBuilder() {
        return super.getPayloadBuilder().setGenericProposalPayload(PB.GenericProposalPayload.newBuilder());
    }

    public static GenericProposalPayload fromProto(PB.ProposalPayload proto) {
        return new GenericProposalPayload(proto.getUid(),
                proto.getName(),
                proto.getTitle(),
                proto.getDescription(),
                proto.getLink(),
                proto.getNodeAddress(),
                proto.getOwnerPubKeyAsHex(),
                (byte) proto.getVersion(),
                proto.getCreationDate(),
                proto.getSignature(),
                proto.getTxId(),
                proto.getHash().isEmpty() ? null : proto.getHash().toByteArray(),
                CollectionUtils.isEmpty(proto.getExtraDataMap()) ? null : proto.getExtraDataMap());
    }

    @Override
    public ProposalType getType() {
        return ProposalType.COMPENSATION_REQUEST;
    }

}
