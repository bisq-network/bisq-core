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

package bisq.core.dao.consensus.blindvote;

import bisq.core.dao.consensus.state.events.StateChangeData;

import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.util.Utilities;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.ByteString;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import javax.annotation.concurrent.Immutable;

@Immutable
@Slf4j
@Value
public final class BlindVote implements PersistablePayload, StateChangeData {

    public static BlindVote clone(BlindVote blindVote) {
        return new BlindVote(blindVote.encryptedBallotList,
                blindVote.getTxId(),
                blindVote.getStake());
    }

    private final byte[] encryptedBallotList;
    private final String txId;
    // Stake is revealed in the BSQ tx anyway as output value so no reason to encrypt it here.
    private final long stake;

    public BlindVote(byte[] encryptedBallotList,
                     String txId,
                     long stake) {
        this.encryptedBallotList = encryptedBallotList;
        this.txId = txId;
        this.stake = stake;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Used for sending over the network
    @Override
    public PB.BlindVote toProtoMessage() {
        return getBuilder().build();
    }

    @NotNull
    public PB.BlindVote.Builder getBuilder() {
        return PB.BlindVote.newBuilder()
                .setEncryptedBallotList(ByteString.copyFrom(encryptedBallotList))
                .setTxId(txId)
                .setStake(stake);
    }

    public static BlindVote fromProto(PB.BlindVote proto) {
        return new BlindVote(proto.getEncryptedBallotList().toByteArray(),
                proto.getTxId(),
                proto.getStake());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public String toString() {
        return "BlindVotePayload{" +
                "\n     encryptedProposalList=" + Utilities.bytesAsHexString(encryptedBallotList) +
                ",\n     txId='" + txId + '\'' +
                ",\n     stake=" + stake +
                "\n}";
    }
}
