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

package bisq.core.dao.vote.proposal.compensation;

import bisq.core.app.BisqEnvironment;
import bisq.core.dao.vote.proposal.ProposalPayload;
import bisq.core.dao.vote.proposal.ProposalType;
import bisq.core.dao.vote.proposal.ValidationException;

import bisq.network.p2p.NodeAddress;

import bisq.common.app.Version;
import bisq.common.crypto.Sig;

import io.bisq.generated.protobuffer.PB;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;

import org.springframework.util.CollectionUtils;

import java.security.PublicKey;

import java.util.Date;
import java.util.Map;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.commons.lang3.Validate.notEmpty;

/**
 * Payload sent over wire as well as it gets persisted, containing all base data for a compensation request
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public final class CompensationRequestPayload extends ProposalPayload {
    private final long requestedBsq;
    private final String bsqAddress;

    CompensationRequestPayload(String uid,
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
                Sig.getPublicKeyBytes(ownerPubKey),
                Version.COMPENSATION_REQUEST_VERSION,
                creationDate.getTime(),
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
                                       byte[] ownerPubKeyEncoded,
                                       byte version,
                                       long creationDate,
                                       String txId,
                                       @Nullable Map<String, String> extraDataMap) {
        super(uid,
                name,
                title,
                description,
                link,
                nodeAddress,
                ownerPubKeyEncoded,
                version,
                creationDate,
                txId,
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
                proto.getOwnerPubKeyEncoded().toByteArray(),
                (byte) proto.getVersion(),
                proto.getCreationDate(),
                proto.getTxId(),
                CollectionUtils.isEmpty(proto.getExtraDataMap()) ? null : proto.getExtraDataMap());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void validate() throws ValidationException {
        super.validate();
        try {
            notEmpty(bsqAddress, "bsqAddress must not be empty");
            checkArgument(bsqAddress.substring(0, 1).equals("B"), "bsqAddress must start with B");
            getAddress(); // throws AddressFormatException if wring address
            //TODO add more checks
        } catch (Throwable throwable) {
            throw new ValidationException(throwable);
        }

        final Coin minRequestAmount = CompensationRequestConsensus.getMinCompensationRequestAmount();
        if (getRequestedBsq().compareTo(minRequestAmount) < 0)
            throw new ValidationException("requestedBsq is smaller than minRequestAmount", getRequestedBsq(), minRequestAmount);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public ProposalType getType() {
        return ProposalType.COMPENSATION_REQUEST;
    }

    public Coin getRequestedBsq() {
        return Coin.valueOf(requestedBsq);
    }

    public Address getAddress() throws AddressFormatException {
        // Remove leading 'B'
        String underlyingBtcAddress = bsqAddress.substring(1, bsqAddress.length());
        return Address.fromBase58(BisqEnvironment.getParameters(), underlyingBtcAddress);
    }

}
