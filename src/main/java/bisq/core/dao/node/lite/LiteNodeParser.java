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
import bisq.core.dao.node.validation.BlockNotConnectingException;
import bisq.core.dao.node.validation.BlockValidator;
import bisq.core.dao.node.validation.GenesisTxValidator;
import bisq.core.dao.node.validation.InvalidBlockException;
import bisq.core.dao.node.validation.TxValidator;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.Block;
import bisq.core.dao.state.blockchain.RawBlock;

import javax.inject.Inject;

import java.util.ArrayList;

import lombok.extern.slf4j.Slf4j;

/**
 * Parser for lite nodes. Iterates blocks to find BSQ relevant transactions.
 * <p>
 * We are in threaded context. Don't mix up with UserThread.
 */
@Slf4j
public class LiteNodeParser extends BsqParser {

    @Inject
    public LiteNodeParser(BlockValidator blockValidator,
                          GenesisTxValidator genesisTxValidator,
                          TxValidator txValidator,
                          StateService stateService) {
        super(blockValidator, genesisTxValidator, txValidator, stateService);
    }

    // The block we received from the seed node is already filtered for valid bsq txs but we want to verify ourselves
    // again. So we run the parsing with that filtered tx list and add a new block with our own verified tx list as
    // well we write the mutual state during parsing to the state model.
    void parseBlock(RawBlock rawBlock) throws BlockNotConnectingException, InvalidBlockException {
        int blockHeight = rawBlock.getHeight();
        log.debug("Parse block at height={} ", blockHeight);
        blockValidator.validate(rawBlock);
        stateService.setNewBlockHeight(rawBlock.getHeight());

        final Block block = new Block(blockHeight,
                rawBlock.getTime(),
                rawBlock.getHash(),
                rawBlock.getPreviousBlockHash(),
                new ArrayList<>());

        maybeAddGenesisTx(rawBlock, blockHeight, block);

        // recursiveFindBsqTxs(block, rawBlock.getRawTxs(), 0, 10000);
        parseBsqTxs(block, rawBlock.getRawTxs());

        stateService.addNewBlock(block);
    }
}
