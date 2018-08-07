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

package bisq.core.dao.governance.proposal;

import bisq.core.dao.governance.ballot.vote.VoteConsensusCritical;
import bisq.core.dao.governance.proposal.compensation.CompensationProposal;
import bisq.core.dao.governance.proposal.confiscatebond.ConfiscateBondProposal;
import bisq.core.dao.governance.proposal.param.ChangeParamProposal;
import bisq.core.dao.governance.proposal.role.BondedRoleProposal;
import bisq.core.dao.state.blockchain.TxOutputType;
import bisq.core.dao.state.blockchain.TxType;
import bisq.core.dao.state.governance.Param;

import bisq.common.proto.ProtobufferRuntimeException;
import bisq.common.proto.persistable.PersistablePayload;

import io.bisq.generated.protobuffer.PB;

import java.util.Date;
import java.util.Optional;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Base class for proposals.
 */
@Immutable
@Slf4j
@Getter
@EqualsAndHashCode
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public abstract class Proposal implements PersistablePayload, VoteConsensusCritical {
    protected final String uid;
    protected final String name;
    protected final String link;
    protected final byte version;
    protected final long creationDate;
    protected final String txId;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected Proposal(String uid,
                       String name,
                       String link,
                       byte version,
                       long creationDate,
                       @Nullable String txId) {
        this.uid = uid;
        this.name = name;
        this.link = link;
        this.version = version;
        this.creationDate = creationDate;
        this.txId = txId;
    }

    public PB.Proposal.Builder getProposalBuilder() {
        final PB.Proposal.Builder builder = PB.Proposal.newBuilder()
                .setUid(uid)
                .setName(name)
                .setLink(link)
                .setVersion(version)
                .setCreationDate(creationDate);
        Optional.ofNullable(txId).ifPresent(builder::setTxId);
        return builder;
    }

    @Override
    public PB.Proposal toProtoMessage() {
        return getProposalBuilder().build();
    }

    //TODO add other proposal types
    public static Proposal fromProto(PB.Proposal proto) {
        switch (proto.getMessageCase()) {
            case COMPENSATION_PROPOSAL:
                return CompensationProposal.fromProto(proto);
            case GENERIC_PROPOSAL:
                throw new ProtobufferRuntimeException("Not implemented yet: " + proto.getMessageCase());
            case CHANGE_PARAM_PROPOSAL:
                return ChangeParamProposal.fromProto(proto);
            case REMOVE_ALTCOIN_PROPOSAL:
                throw new ProtobufferRuntimeException("Not implemented yet: " + proto.getMessageCase());
            case CONFISCATE_BOND_PROPOSAL:
                return ConfiscateBondProposal.fromProto(proto);
            case BONDED_ROLE_PROPOSAL:
                return BondedRoleProposal.fromProto(proto);
            case MESSAGE_NOT_SET:
            default:
                throw new ProtobufferRuntimeException("Unknown message case: " + proto.getMessageCase());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    abstract public Proposal cloneWithoutTxId();

    abstract public Proposal cloneWithTxId(String txId);

    public Date getCreationDate() {
        return new Date(creationDate);
    }

    public String getShortId() {
        return uid.length() > 7 ? uid.substring(0, 8) : uid;
    }

    public abstract ProposalType getType();

    public TxType getTxType() {
        return TxType.PROPOSAL;
    }

    public TxOutputType getTxOutputType() {
        return TxOutputType.PROPOSAL_OP_RETURN_OUTPUT;
    }

    public abstract Param getQuorumParam();

    public abstract Param getThresholdParam();

    @Override
    public String toString() {
        return "Proposal{" +
                "\n     uid='" + uid + '\'' +
                ",\n     name='" + name + '\'' +
                ",\n     link='" + link + '\'' +
                ",\n     txId='" + txId + '\'' +
                ",\n     version=" + version +
                ",\n     creationDate=" + new Date(creationDate) +
                "\n}";
    }
}
