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

package bisq.core.dao.consensus.proposal.compensation;

import bisq.core.app.BisqEnvironment;
import bisq.core.dao.consensus.proposal.Proposal;
import bisq.core.dao.consensus.proposal.ProposalType;
import bisq.core.dao.consensus.proposal.param.Param;
import bisq.core.dao.consensus.state.blockchain.TxOutputType;
import bisq.core.dao.consensus.state.blockchain.TxType;

import bisq.common.app.Version;

import io.bisq.generated.protobuffer.PB;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Coin;

import java.util.Date;
import java.util.UUID;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.concurrent.Immutable;

@Immutable
@Slf4j
@EqualsAndHashCode(callSuper = true)
@Value
public final class CompensationProposal extends Proposal {

    private final long requestedBsq;
    private final String bsqAddress;

    public CompensationProposal(String name,
                                String title,
                                String description,
                                String link,
                                Coin requestedBsq,
                                String bsqAddress) {
        super(UUID.randomUUID().toString(),
                name,
                title,
                description,
                link,
                Version.COMPENSATION_REQUEST_VERSION,
                new Date().getTime(),
                "");
        this.requestedBsq = requestedBsq.value;
        this.bsqAddress = bsqAddress;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public CompensationProposal(String uid,
                                String name,
                                String title,
                                String description,
                                String link,
                                String bsqAddress,
                                long requestedBsq,
                                byte version,
                                long creationDate,
                                String txId) {
        super(uid,
                name,
                title,
                description,
                link,
                version,
                creationDate,
                txId);

        this.requestedBsq = requestedBsq;
        this.bsqAddress = bsqAddress;
    }

    @Override
    public PB.Proposal.Builder getProposalBuilder() {
        final PB.CompensationProposal.Builder compensationRequestProposalBuilder = PB.CompensationProposal.newBuilder()
                .setBsqAddress(bsqAddress)
                .setRequestedBsq(requestedBsq);
        return super.getProposalBuilder().setCompensationProposal(compensationRequestProposalBuilder);
    }

    public static CompensationProposal fromProto(PB.Proposal proto) {
        final PB.CompensationProposal compensationRequestProposa = proto.getCompensationProposal();
        return new CompensationProposal(proto.getUid(),
                proto.getName(),
                proto.getTitle(),
                proto.getDescription(),
                proto.getLink(),
                compensationRequestProposa.getBsqAddress(),
                compensationRequestProposa.getRequestedBsq(),
                (byte) proto.getVersion(),
                proto.getCreationDate(),
                proto.getTxId());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public ProposalType getType() {
        return ProposalType.COMPENSATION_REQUEST;
    }

    @Override
    public Param getQuorumParam() {
        return Param.QUORUM_COMP_REQUEST;
    }

    @Override
    public Param getThresholdParam() {
        return Param.THRESHOLD_COMP_REQUEST;
    }

    public Coin getRequestedBsq() {
        return Coin.valueOf(requestedBsq);
    }

    public Address getAddress() throws AddressFormatException {
        // Remove leading 'B'
        String underlyingBtcAddress = bsqAddress.substring(1, bsqAddress.length());
        return Address.fromBase58(BisqEnvironment.getParameters(), underlyingBtcAddress);
    }

    public TxType getTxType() {
        return TxType.COMPENSATION_REQUEST;
    }

    public TxOutputType getTxOutputType() {
        return TxOutputType.COMP_REQ_OP_RETURN_OUTPUT;
    }

    @Override
    public Proposal cloneWithTxId(String txId) {
        return new CompensationProposal(getUid(),
                getName(),
                getTitle(),
                getDescription(),
                getLink(),
                getBsqAddress(),
                getRequestedBsq().value,
                getVersion(),
                getCreationDate().getTime(),
                txId);
    }

    @Override
    public Proposal cloneWithoutTxId() {
        return new CompensationProposal(getUid(),
                getName(),
                getTitle(),
                getDescription(),
                getLink(),
                getBsqAddress(),
                getRequestedBsq().value,
                getVersion(),
                getCreationDate().getTime(),
                "");
    }

    @Override
    public String toString() {
        return "CompensationProposal{" +
                "\n     requestedBsq=" + requestedBsq +
                ",\n     bsqAddress='" + bsqAddress + '\'' +
                "\n} " + super.toString();
    }
}
