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

package bisq.core.dao.blockchain.vo;

import bisq.common.proto.ProtoUtil;

import io.bisq.generated.protobuffer.PB;

public enum TxOutputType {
    UNDEFINED,
    BSQ_OUTPUT,
    BTC_OUTPUT,
    COMP_REQ_OP_RETURN_OUTPUT,
    ISSUANCE_CANDIDATE_OUTPUT,
    VOTE_STAKE_OUTPUT,
    VOTE_OP_RETURN_OUTPUT,
    VOTE_REVEAL_OP_RETURN_OUTPUT,
    BOND_LOCK,
    BOND_UNLOCK;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static TxOutputType fromProto(PB.TxOutputType txOutputType) {
        return ProtoUtil.enumFromProto(TxOutputType.class, txOutputType.name());
    }

    public PB.TxOutputType toProtoMessage() {
        return PB.TxOutputType.valueOf(name());
    }
}
