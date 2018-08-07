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

package bisq.core.dao.governance.proposal.role;

import bisq.core.dao.governance.proposal.Proposal;
import bisq.core.dao.governance.proposal.ProposalType;
import bisq.core.dao.governance.role.BondedRole;
import bisq.core.dao.state.blockchain.TxOutputType;
import bisq.core.dao.state.blockchain.TxType;
import bisq.core.dao.state.governance.Param;

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
public final class BondedRoleProposal extends Proposal {

    private final BondedRole bondedRole;

    public BondedRoleProposal(BondedRole bondedRole) {
        this(UUID.randomUUID().toString(),
                bondedRole.getName(),
                bondedRole.getLink(),
                bondedRole,
                Version.PROPOSAL,
                new Date().getTime(),
                "");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private BondedRoleProposal(String uid,
                               String name,
                               String link,
                               BondedRole bondedRole,
                               byte version,
                               long creationDate,
                               String txId) {
        super(uid,
                name,
                link,
                version,
                creationDate,
                txId);

        this.bondedRole = bondedRole;
    }

    @Override
    public PB.Proposal.Builder getProposalBuilder() {
        final PB.BondedRoleProposal.Builder builder = PB.BondedRoleProposal.newBuilder()
                .setBondedRole(bondedRole.toProtoMessage());
        return super.getProposalBuilder().setBondedRoleProposal(builder);
    }

    public static BondedRoleProposal fromProto(PB.Proposal proto) {
        final PB.BondedRoleProposal proposalProto = proto.getBondedRoleProposal();
        return new BondedRoleProposal(proto.getUid(),
                proto.getName(),
                proto.getLink(),
                BondedRole.fromProto(proposalProto.getBondedRole()),
                (byte) proto.getVersion(),
                proto.getCreationDate(),
                proto.getTxId());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public ProposalType getType() {
        return ProposalType.BONDED_ROLE;
    }

    @Override
    public Param getQuorumParam() {
        return Param.QUORUM_PROPOSAL;
    }

    @Override
    public Param getThresholdParam() {
        return Param.THRESHOLD_PROPOSAL;
    }

    public TxType getTxType() {
        return TxType.PROPOSAL;
    }

    public TxOutputType getTxOutputType() {
        return TxOutputType.PROPOSAL_OP_RETURN_OUTPUT;
    }

    @Override
    public Proposal cloneWithTxId(String txId) {
        return new BondedRoleProposal(getUid(),
                getName(),
                getLink(),
                getBondedRole(),
                getVersion(),
                getCreationDate().getTime(),
                txId);
    }

    @Override
    public Proposal cloneWithoutTxId() {
        return new BondedRoleProposal(getUid(),
                getName(),
                getLink(),
                getBondedRole(),
                getVersion(),
                getCreationDate().getTime(),
                "");
    }

    @Override
    public String toString() {
        return "BondedRoleProposal{" +
                "\n     bondedRole=" + bondedRole +
                "\n} " + super.toString();
    }
}
