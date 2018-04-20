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

package bisq.core.dao.consensus.proposal.param;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

//TODO impl
@Slf4j
@Value
public final class ChangeParamProposal /*extends Proposal*/ {
/*
    public ChangeParamProposal(String uid,
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

    private ChangeParamProposal(String uid,
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
        //TODO impl
        return null;
    }

    public static ChangeParamProposal fromProto(PB.Proposal proto) {
        //TODO impl
        return null;
    }

    @Override
    public Proposal cloneWithoutTxId() {
        //TODO impl
        return null;
    }

    @Override
    public Proposal cloneWithTxId(String txId) {
        //TODO impl
        return null;
    }

    @Override
    public ProposalType getType() {
        return ProposalType.CHANGE_PARAM;
    }

    @Override
    public Param getQuorumDaoParam() {
        return Param.QUORUM_CHANGE_PARAM;
    }

    @Override
    public Param getThresholdDaoParam() {
        return Param.THRESHOLD_CHANGE_PARAM;
    }*/
}
