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

package bisq.core.dao.proposal.compensation;

import bisq.core.dao.proposal.ProposalPayload;
import bisq.core.dao.proposal.ProposalType;

import bisq.network.p2p.NodeAddress;

import bisq.common.app.Version;

import io.bisq.generated.protobuffer.PB;

import org.springframework.util.CollectionUtils;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Utils;

import java.security.PublicKey;

import java.util.Date;
import java.util.Map;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * Payload sent over wire as well as it gets persisted, containing all base data for a compensation request
 */
@Slf4j
@Data
public final class CompensationRequestPayload extends ProposalPayload {
    private final long requestedBsq;
    private final String bsqAddress;

    public CompensationRequestPayload(String uid,
                                      String name,
                                      String title,
                                      String description,
                                      String link,
                                      Coin requestedBsq,
                                      String bsqAddress,
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
        this.requestedBsq = requestedBsq.value;
        this.bsqAddress = bsqAddress;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private CompensationRequestPayload(String uid,
                                       String name,
                                       String title,
                                       String description,
                                       String link,
                                       String bsqAddress,
                                       long requestedBsq,
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

        this.requestedBsq = requestedBsq;
        this.bsqAddress = bsqAddress;
    }

    @Override
    public PB.ProposalPayload.Builder getPayloadBuilder() {
        final PB.CompensationRequestPayload.Builder compensationRequestPayloadBuilder = PB.CompensationRequestPayload.newBuilder()
                .setBsqAddress(bsqAddress)
                .setRequestedBsq(requestedBsq);
        return super.getPayloadBuilder().setCompensationRequestPayload(compensationRequestPayloadBuilder);
    }

    public static CompensationRequestPayload fromProto(PB.ProposalPayload proto) {
        final PB.CompensationRequestPayload compensationRequestPayload = proto.getCompensationRequestPayload();
        return new CompensationRequestPayload(proto.getUid(),
                proto.getName(),
                proto.getTitle(),
                proto.getDescription(),
                proto.getLink(),
                compensationRequestPayload.getBsqAddress(),
                compensationRequestPayload.getRequestedBsq(),
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Coin getRequestedBsq() {
        return Coin.valueOf(requestedBsq);
    }
}
