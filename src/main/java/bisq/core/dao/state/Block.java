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

package bisq.core.dao.state;

import bisq.core.dao.state.blockchain.TxBlock;
import bisq.core.dao.state.events.StateChangeEvent;

import bisq.common.proto.persistable.PersistablePayload;

import io.bisq.generated.protobuffer.PB;

import com.google.common.collect.ImmutableSet;

import lombok.Value;
import lombok.experimental.Delegate;

import javax.annotation.concurrent.Immutable;

/**
 * The base unit collecting data which leads to a state change.
 * Consists of the TxBlock for blockchain related change events (transactions) and the stateChangeEvents
 * for non-blockchain related events like AddProposalPayloadEvents or ChangeParamEvents.
 */
@Immutable
@Value
public class Block implements PersistablePayload {

    // We use the TxBlock as delegate
    @Delegate(excludes = Block.ExcludesDelegateMethods.class)
    private final TxBlock txBlock;

    private interface ExcludesDelegateMethods {
        PB.Block toProtoMessage();
    }

    //TODO stateChangeEvents not set in PB yet
    // The state change events for that block containing any non-blockchain data which can
    // trigger a state change in the Bisq DAO.
    private final ImmutableSet<StateChangeEvent> stateChangeEvents;


    public Block(TxBlock txBlock, ImmutableSet<StateChangeEvent> stateChangeEvents) {
        this.txBlock = txBlock;
        this.stateChangeEvents = stateChangeEvents;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public PB.Block toProtoMessage() {
        return PB.Block.newBuilder()
                .setTxBlock(txBlock.toProtoMessage())
                .build();
    }


    public static Block fromProto(PB.Block proto) {
        //TODO stateChangeEvents not set yet
        return new Block(TxBlock.fromProto(proto.getTxBlock()),
                null);
    }

    public static Block clone(Block block) {
        return Block.fromProto(block.toProtoMessage());
    }
}
