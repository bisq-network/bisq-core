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

package bisq.core.dao.voting.proposal.burnbond;

import bisq.core.dao.state.blockchain.TxOutputType;
import bisq.core.dao.state.blockchain.TxType;
import bisq.core.dao.state.ext.Param;
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
public final class BurnBondProposal extends Proposal {

    private final String bondId;

    public BurnBondProposal(String name,
                            String title,
                            String description,
                            String link,
                            String bondId) {
        this(UUID.randomUUID().toString(),
                name,
                title,
                description,
                link,
                bondId,
                Version.PROPOSAL,
                new Date().getTime(),
                "");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private BurnBondProposal(String uid,
                             String name,
                             String title,
                             String description,
                             String link,
                             String bondId,
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

        this.bondId = bondId;
    }

    @Override
    public PB.Proposal.Builder getProposalBuilder() {
        final PB.BurnBondProposal.Builder builder = PB.BurnBondProposal.newBuilder()
                .setBondId(bondId);
        return super.getProposalBuilder().setBurnBondProposal(builder);
    }

    public static BurnBondProposal fromProto(PB.Proposal proto) {
        final PB.BurnBondProposal proposalProto = proto.getBurnBondProposal();
        return new BurnBondProposal(proto.getUid(),
                proto.getName(),
                proto.getTitle(),
                proto.getDescription(),
                proto.getLink(),
                proposalProto.getBondId(),
                (byte) proto.getVersion(),
                proto.getCreationDate(),
                proto.getTxId());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public ProposalType getType() {
        return ProposalType.BURN_BOND;
    }

    @Override
    public Param getQuorumParam() {
        return Param.QUORUM_BURN_BOND;
    }

    @Override
    public Param getThresholdParam() {
        return Param.THRESHOLD_BURN_BOND;
    }

    public TxType getTxType() {
        return TxType.PROPOSAL;
    }

    public TxOutputType getTxOutputType() {
        return TxOutputType.BURN_BOND_OP_RETURN_OUTPUT;
    }

    @Override
    public Proposal cloneWithTxId(String txId) {
        return new BurnBondProposal(getUid(),
                getName(),
                getTitle(),
                getDescription(),
                getLink(),
                getBondId(),
                getVersion(),
                getCreationDate().getTime(),
                txId);
    }

    @Override
    public Proposal cloneWithoutTxId() {
        return new BurnBondProposal(getUid(),
                getName(),
                getTitle(),
                getDescription(),
                getLink(),
                getBondId(),
                getVersion(),
                getCreationDate().getTime(),
                "");
    }

    @Override
    public String toString() {
        return "BurnBondProposal{" +
                "\n     bondId=" + bondId +
                "\n} " + super.toString();
    }
}
