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
import bisq.core.dao.node.consensus.BsqBlockController;
import bisq.core.dao.node.consensus.BsqTxController;
import bisq.core.dao.node.consensus.GenesisTxController;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.blockchain.TxBlock;

import javax.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

/**
 * Parser for lite nodes. Iterates blocks to find BSQ relevant transactions.
 * <p>
 * We are in threaded context. Don't mix up with UserThread.
 */
@Slf4j
public class LiteNodeParser extends BsqParser {

    @Inject
    public LiteNodeParser(BsqBlockController bsqBlockController,
                          GenesisTxController genesisTxController,
                          BsqTxController bsqTxController,
                          StateService stateService) {
        super(bsqBlockController, genesisTxController, bsqTxController, stateService);
    }

    void parseBsqBlocks(List<TxBlock> txBlocks,
                        Consumer<TxBlock> newBlockHandler)
            throws BlockNotConnectingException {
        for (TxBlock txBlock : txBlocks) {
            parseBsqBlock(txBlock);
            newBlockHandler.accept(txBlock);
        }
    }

    void parseBsqBlock(TxBlock txBlock) throws BlockNotConnectingException {
        int blockHeight = txBlock.getHeight();
        log.debug("Parse block at height={} ", blockHeight);
        List<Tx> txList = new ArrayList<>(txBlock.getTxs());
        List<Tx> bsqTxsInBlock = new ArrayList<>();

        stateService.startParsingBlock(txBlock.getHeight());

        txBlock.getTxs().forEach(tx -> checkForGenesisTx(blockHeight, bsqTxsInBlock, tx));
        recursiveFindBsqTxs(bsqTxsInBlock, txList, blockHeight, 0, 5300);

        bsqBlockController.addBlockIfValid(txBlock);
    }
}
