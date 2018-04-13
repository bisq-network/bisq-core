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

import com.google.common.collect.ImmutableList;

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

    /*public static Block clone(Block txBlock) {
        final ImmutableList<Tx> txs = ImmutableList.copyOf(txBlock.getTxs().stream()
                .map(tx -> Tx.clone(tx))
                .collect(Collectors.toList()));
        return new Block(txBlock.getHeight(),
                txBlock.getTime(),
                txBlock.getHash(),
                txBlock.getPreviousBlockHash(),
                txs);
    }*/

    // We use the TxBlock as delegate
    @Delegate(excludes = Block.ExcludesDelegateMethods.class)
    private final TxBlock txBlock;

    private interface ExcludesDelegateMethods {
        PB.BsqBlock toProtoMessage();
    }

    // The state change events for that block containing any non-blockchain data which can
    // trigger a state change in the Bisq DAO.
    private final ImmutableList<StateChangeEvent> stateChangeEvents;


    public Block(TxBlock txBlock, ImmutableList<StateChangeEvent> stateChangeEvents) {
        this.txBlock = txBlock;
        this.stateChangeEvents = stateChangeEvents;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PB.BsqBlock toProtoMessage() {
        return null;
       /* return PB.BsqBlock.newBuilder()
                .setHeight(height)
                .setTime(time)
                .setHash(hash)
                .setPreviousBlockHash(previousBlockHash)
                .addAllTxs(txs.stream()
                        .map(Tx::toProtoMessage)
                        .collect(Collectors.toList()))
                .build();*/
    }

    public static Block fromProto(PB.BsqBlock proto) {
        return null;
       /* return new Block(proto.getHeight(),
                proto.getTime(),
                proto.getHash(),
                proto.getPreviousBlockHash(),
                proto.getTxsList().isEmpty() ?
                        ImmutableList.copyOf(new ArrayList<>()) :
                        ImmutableList.copyOf(proto.getTxsList().stream()
                                .map(Tx::fromProto)
                                .collect(Collectors.toList())));*/
    }
}
