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

package bisq.core.dao.consensus.vote.proposal.generic;

import bisq.core.dao.consensus.vote.proposal.Proposal;
import bisq.core.dao.consensus.vote.proposal.ProposalType;
import bisq.core.dao.consensus.vote.proposal.param.Param;

import bisq.common.app.Version;
import bisq.common.crypto.Sig;

import io.bisq.generated.protobuffer.PB;

import org.springframework.util.CollectionUtils;

import java.security.PublicKey;

import java.util.Date;
import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

//TODO separate value object with p2p network data
@Immutable
@Slf4j
@EqualsAndHashCode(callSuper = true)
@Value
public final class GenericProposal extends Proposal {

    public GenericProposal(String uid,
                           String name,
                           String title,
                           String description,
                           String link,
                           PublicKey ownerPubKey,
                           Date creationDate) {
        super(uid,
                name,
                title,
                description,
                link,
                Sig.getPublicKeyBytes(ownerPubKey),
                Version.PROPOSAL,
                creationDate.getTime(),
                null,
                null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private GenericProposal(String uid,
                            String name,
                            String title,
                            String description,
                            String link,
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
                ownerPubKeyEncoded,
                version,
                creationDate,
                txId,
                extraDataMap);
    }

    @Override
    public PB.Proposal.Builder getProposalBuilder() {
        return super.getProposalBuilder().setGenericProposal(PB.GenericProposal.newBuilder());
    }

    public static GenericProposal fromProto(PB.Proposal proto) {
        return new GenericProposal(proto.getUid(),
                proto.getName(),
                proto.getTitle(),
                proto.getDescription(),
                proto.getLink(),
                proto.getOwnerPubKeyEncoded().toByteArray(),
                (byte) proto.getVersion(),
                proto.getCreationDate(),
                proto.getTxId(),
                CollectionUtils.isEmpty(proto.getExtraDataMap()) ? null : proto.getExtraDataMap());
    }

    @Override
    public ProposalType getType() {
        return ProposalType.GENERIC;
    }

    @Override
    public Param getQuorumDaoParam() {
        return Param.QUORUM_PROPOSAL;
    }

    @Override
    public Param getThresholdDaoParam() {
        return Param.THRESHOLD_PROPOSAL;
    }

    @Override
    public Proposal cloneWithTxId(String txId) {
        return new GenericProposal(getUid(),
                getName(),
                getTitle(),
                getDescription(),
                getLink(),
                getOwnerPubKeyEncoded(),
                getVersion(),
                getCreationDate().getTime(),
                txId,
                getExtraDataMap());
    }

    @Override
    public Proposal cloneWithoutTxId() {
        return new GenericProposal(getUid(),
                getName(),
                getTitle(),
                getDescription(),
                getLink(),
                getOwnerPubKeyEncoded(),
                getVersion(),
                getCreationDate().getTime(),
                null,
                getExtraDataMap());
    }
}
