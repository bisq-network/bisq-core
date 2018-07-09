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

package bisq.core.dao.voting.proposal.param;

import bisq.core.dao.state.blockchain.TxOutputType;
import bisq.core.dao.state.blockchain.TxType;
import bisq.core.dao.voting.proposal.Proposal;
import bisq.core.dao.voting.proposal.ProposalType;

import bisq.common.app.Version;

import io.bisq.generated.protobuffer.PB;

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
public final class ParamProposal extends Proposal {

    private final Param param;
    private final long paramValue;

    public ParamProposal(String name,
                         String title,
                         String description,
                         String link,
                         Param param,
                         long paramValue) {
        this(UUID.randomUUID().toString(),
                name,
                title,
                description,
                link,
                param,
                paramValue,
                Version.COMPENSATION_REQUEST_VERSION,
                new Date().getTime(),
                "");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private ParamProposal(String uid,
                          String name,
                          String title,
                          String description,
                          String link,
                          Param param,
                          long paramValue,
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

        this.param = param;
        this.paramValue = paramValue;
    }

    @Override
    public PB.Proposal.Builder getProposalBuilder() {
        //TODO
        final PB.CompensationProposal.Builder compensationRequestProposalBuilder = PB.CompensationProposal.newBuilder()/*
                .setBsqAddress(bsqAddress)
                .setRequestedBsq(requestedBsq)*/;
        return super.getProposalBuilder().setCompensationProposal(compensationRequestProposalBuilder);
    }

    public static ParamProposal fromProto(PB.Proposal proto) {
        final PB.CompensationProposal compensationRequestProposa = proto.getCompensationProposal();
        return new ParamProposal(proto.getUid(),
                proto.getName(),
                proto.getTitle(),
                proto.getDescription(),
                proto.getLink(),
                null, //TODO
                0,//TODO
                (byte) proto.getVersion(),
                proto.getCreationDate(),
                proto.getTxId());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public ProposalType getType() {
        return ProposalType.CHANGE_PARAM;
    }

    @Override
    public Param getQuorumParam() {
        return Param.QUORUM_CHANGE_PARAM;
    }

    @Override
    public Param getThresholdParam() {
        return Param.THRESHOLD_CHANGE_PARAM;
    }

    public TxType getTxType() {
        return TxType.COMPENSATION_REQUEST;
    }

    public TxOutputType getTxOutputType() {
        return TxOutputType.COMP_REQ_OP_RETURN_OUTPUT;
    }

    @Override
    public Proposal cloneWithTxId(String txId) {
        return new ParamProposal(getUid(),
                getName(),
                getTitle(),
                getDescription(),
                getLink(),
                getParam(),
                getParamValue(),
                getVersion(),
                getCreationDate().getTime(),
                txId);
    }

    @Override
    public Proposal cloneWithoutTxId() {
        return new ParamProposal(getUid(),
                getName(),
                getTitle(),
                getDescription(),
                getLink(),
                getParam(),
                getParamValue(),
                getVersion(),
                getCreationDate().getTime(),
                "");
    }

    @Override
    public String toString() {
        return "ParamProposal{" +
                "\n     param=" + param +
                ",\n     paramValue=" + paramValue +
                "\n} " + super.toString();
    }
}
