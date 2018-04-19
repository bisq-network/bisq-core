/*
 * This file is part of Bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.consensus.node.consensus;

import bisq.core.dao.consensus.node.blockchain.exceptions.BlockNotConnectingException;
import bisq.core.dao.consensus.state.StateService;
import bisq.core.dao.consensus.state.blockchain.TxBlock;

import javax.inject.Inject;

import java.util.LinkedList;

import lombok.extern.slf4j.Slf4j;

/**
 * Checks if a block is valid and if so adds it to the StateService.
 */
@Slf4j
public class BsqBlockController {

    private final StateService stateService;

    @Inject
    public BsqBlockController(StateService stateService) {
        this.stateService = stateService;
    }

    public void addBlockIfValid(TxBlock txBlock) throws BlockNotConnectingException {
        LinkedList<TxBlock> txBlocks = stateService.getTxBlocks();
        if (!txBlocks.contains(txBlock)) {
            if (isBlockConnecting(txBlock, txBlocks)) {
                stateService.applyTxBlock(txBlock);
            } else {
                log.warn("addBlock called with a not connecting block:\n" +
                                "height()={}, hash()={}, head.height()={}, head.hash()={}",
                        txBlock.getHeight(), txBlock.getHash(), txBlocks.getLast().getHeight(), txBlocks.getLast().getHash());
                throw new BlockNotConnectingException(txBlock);
            }
        } else {
            log.warn("We got that block already. Ignore the call.");
        }
    }

    private boolean isBlockConnecting(TxBlock txBlock, LinkedList<TxBlock> txBlocks) {
        // Case 1: txBlocks is empty
        // Case 2: txBlocks not empty. Last block must match new blocks getPreviousBlockHash and
        // height of last block +1 must be new blocks height
        return txBlocks.isEmpty() ||
                (txBlocks.getLast().getHash().equals(txBlock.getPreviousBlockHash()) &&
                        txBlocks.getLast().getHeight() + 1 == txBlock.getHeight());
    }
}
