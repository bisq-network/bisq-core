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

package bisq.core.dao.node.lite;

import bisq.core.dao.node.BsqParser;
import bisq.core.dao.node.blockchain.exceptions.BlockNotConnectingException;
import bisq.core.dao.node.consensus.BlockController;
import bisq.core.dao.node.consensus.GenesisTxController;
import bisq.core.dao.node.consensus.TxController;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.Block;
import bisq.core.dao.state.blockchain.Tx;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * Parser for lite nodes. Iterates blocks to find BSQ relevant transactions.
 * <p>
 * We are in threaded context. Don't mix up with UserThread.
 */
@Slf4j
public class LiteNodeParser extends BsqParser {

    @Inject
    public LiteNodeParser(BlockController blockController,
                          GenesisTxController genesisTxController,
                          TxController txController,
                          StateService stateService) {
        super(blockController, genesisTxController, txController, stateService);
    }

    void parseBlock(Block block) throws BlockNotConnectingException {
        int blockHeight = block.getHeight();
        log.debug("Parse block at height={} ", blockHeight);
        List<Tx> txList = new ArrayList<>(block.getTxs());
        List<Tx> bsqTxsInBlock = new ArrayList<>();

        stateService.setNewBlockHeight(block.getHeight());

        block.getTxs().forEach(tx -> checkForGenesisTx(blockHeight, bsqTxsInBlock, tx));
        recursiveFindBsqTxs(bsqTxsInBlock, txList, blockHeight, 0, 5300);

        if (blockController.isBlockValid(block))
            stateService.parseBlockComplete(block);
    }
}
