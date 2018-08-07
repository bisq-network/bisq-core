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

package bisq.core.dao.governance.proposal.confiscatebond;

import bisq.core.dao.governance.proposal.Proposal;
import bisq.core.dao.governance.proposal.ProposalType;
import bisq.core.dao.state.blockchain.TxOutputType;
import bisq.core.dao.state.blockchain.TxType;
import bisq.core.dao.state.ext.Param;

import bisq.common.app.Version;
import bisq.common.util.Utilities;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.ByteString;

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
public final class ConfiscateBondProposal extends Proposal {

    private final byte[] hash;

    public ConfiscateBondProposal(String name,
                                  String link,
                                  byte[] hash) {
        this(UUID.randomUUID().toString(),
                name,
                link,
                hash,
                Version.PROPOSAL,
                new Date().getTime(),
                "");
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private ConfiscateBondProposal(String uid,
                                   String name,
                                   String link,
                                   byte[] hash,
                                   byte version,
                                   long creationDate,
                                   String txId) {
        super(uid,
                name,
                link,
                version,
                creationDate,
                txId);

        this.hash = hash;
    }

    @Override
    public PB.Proposal.Builder getProposalBuilder() {
        final PB.ConfiscateBondProposal.Builder builder = PB.ConfiscateBondProposal.newBuilder()
                .setHash(ByteString.copyFrom(hash));
        return super.getProposalBuilder().setConfiscateBondProposal(builder);
    }

    public static ConfiscateBondProposal fromProto(PB.Proposal proto) {
        final PB.ConfiscateBondProposal proposalProto = proto.getConfiscateBondProposal();
        return new ConfiscateBondProposal(proto.getUid(),
                proto.getName(),
                proto.getLink(),
                proposalProto.getHash().toByteArray(),
                (byte) proto.getVersion(),
                proto.getCreationDate(),
                proto.getTxId());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public ProposalType getType() {
        return ProposalType.CONFISCATE_BOND;
    }

    @Override
    public Param getQuorumParam() {
        return Param.QUORUM_CONFISCATION;
    }

    @Override
    public Param getThresholdParam() {
        return Param.THRESHOLD_CONFISCATION;
    }

    public TxType getTxType() {
        return TxType.PROPOSAL;
    }

    public TxOutputType getTxOutputType() {
        return TxOutputType.CONFISCATE_BOND_OP_RETURN_OUTPUT;
    }

    @Override
    public Proposal cloneWithTxId(String txId) {
        return new ConfiscateBondProposal(getUid(),
                getName(),
                getLink(),
                getHash(),
                getVersion(),
                getCreationDate().getTime(),
                txId);
    }

    @Override
    public Proposal cloneWithoutTxId() {
        return new ConfiscateBondProposal(getUid(),
                getName(),
                getLink(),
                getHash(),
                getVersion(),
                getCreationDate().getTime(),
                "");
    }

    @Override
    public String toString() {
        return "ConfiscateBondProposal{" +
                "\n     hash=" + Utilities.bytesAsHexString(hash) +
                "\n} " + super.toString();
    }
}
