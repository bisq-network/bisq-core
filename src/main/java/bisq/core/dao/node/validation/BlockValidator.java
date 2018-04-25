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

package bisq.core.dao.node.validation;

import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.Block;

import javax.inject.Inject;

import java.util.LinkedList;

import lombok.extern.slf4j.Slf4j;

/**
 * Checks if a block is valid and if so adds it to the StateService.
 */
@Slf4j
public class BlockValidator {

    private final StateService stateService;

    @Inject
    public BlockValidator(StateService stateService) {
        this.stateService = stateService;
    }

    public boolean validate(Block block) throws BlockNotConnectingException {
        LinkedList<Block> blocks = stateService.getBlocks();
        if (!blocks.contains(block)) {
            if (isBlockConnecting(block, blocks)) {
                return true;
            } else {
                log.warn("addBlock called with a not connecting block:\n" +
                                "height()={}, hash()={}, head.height()={}, head.hash()={}",
                        block.getHeight(), block.getHash(), blocks.getLast().getHeight(), blocks.getLast().getHash());
                throw new BlockNotConnectingException(block);
            }
        } else {
            log.warn("We got that block already. Ignore the call.");
            return false;
        }
    }

    private boolean isBlockConnecting(Block block, LinkedList<Block> blocks) {
        // Case 1: blocks is empty
        // Case 2: blocks not empty. Last block must match new blocks getPreviousBlockHash and
        // height of last block +1 must be new blocks height
        return blocks.isEmpty() ||
                (blocks.getLast().getHash().equals(block.getPreviousBlockHash()) &&
                        blocks.getLast().getHeight() + 1 == block.getHeight());
    }
}
