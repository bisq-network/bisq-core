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

package bisq.core.dao.vote.proposal.param;

import bisq.core.dao.vote.proposal.ProposalPayload;
import bisq.core.dao.vote.proposal.ProposalType;

import bisq.common.app.Version;
import bisq.common.crypto.Sig;

import io.bisq.generated.protobuffer.PB;

import java.security.PublicKey;

import java.util.Date;
import java.util.Map;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * Payload for generic proposals.
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Value
public final class ChangeParamProposalPayload extends ProposalPayload {

    public ChangeParamProposalPayload(String uid,
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

    private ChangeParamProposalPayload(String uid,
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
    public PB.ProposalPayload.Builder getPayloadBuilder() {
        //TODO impl
        return null;
    }

    public static ChangeParamProposalPayload fromProto(PB.ProposalPayload proto) {
        //TODO impl
        return null;
    }

    @Override
    public ProposalType getType() {
        return ProposalType.CHANGE_PARAM;
    }
}
