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

package bisq.core.dao.node.full;

import bisq.core.dao.node.BsqParser;
import bisq.core.dao.node.validation.BlockNotConnectingException;
import bisq.core.dao.node.validation.BlockValidator;
import bisq.core.dao.node.validation.GenesisTxValidator;
import bisq.core.dao.node.validation.TxValidator;
import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.state.blockchain.Block;
import bisq.core.dao.state.blockchain.RawBlock;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

/**
 * Parser for full nodes. Request blockchain data via rpc from Bitcoin Core and iterates blocks to find BSQ relevant transactions.
 * <p>
 * We are in threaded context. Don't mix up with UserThread.
 */
@Slf4j
public class FullNodeParser extends BsqParser {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public FullNodeParser(BlockValidator blockValidator,
                          GenesisTxValidator genesisTxValidator,
                          TxValidator txValidator,
                          BsqStateService bsqStateService) {
        super(blockValidator, genesisTxValidator, txValidator, bsqStateService);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Package scope
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     *
     * @param rawBlock  Contains all transactions of a BTC block
     * @return bsqBlock: Is a clone of btcBlock but filtered so it contains only bsq transactions
     * @throws BlockNotConnectingException If new block does not connect to previous block
     */
    Block parseBlock(RawBlock rawBlock) throws BlockNotConnectingException {
        blockValidator.validate(rawBlock);

        final int blockHeight = rawBlock.getHeight();
        bsqStateService.onNewBlockHeight(blockHeight);

        final Block block = new Block(blockHeight,
                rawBlock.getTime(),
                rawBlock.getHash(),
                rawBlock.getPreviousBlockHash());

        // TODO needed?
        if (!blockValidator.isBlockAlreadyAdded(rawBlock))
            bsqStateService.onNewBlockWithEmptyTxs(block);

        maybeAddGenesisTx(rawBlock, blockHeight, block);

        // Worst case is that all txs in a block are depending on another, so only one get resolved at each iteration.
        // Min tx size is 189 bytes (normally about 240 bytes), 1 MB can contain max. about 5300 txs (usually 2000).
        // Realistically we don't expect more then a few recursive calls.
        // There are some blocks with testing such dependency chains like block 130768 where at each iteration only
        // one get resolved.
        // Lately there is a patter with 24 iterations observed
        long startTs = System.currentTimeMillis();
        // recursiveFindBsqTxs(block, rawBlock.getRawTxs(), 0, 10000);
        // recursiveFindBsqTxs1(block, rawBlock.getRawTxs(), 0, 10000);
        parseBsqTxs(block, rawBlock.getRawTxs());

        log.debug("parseBsqTxs took {} ms",
                rawBlock.getRawTxs().size(), System.currentTimeMillis() - startTs);

        bsqStateService.onParseBlockComplete(block);

        //log.error("COMPLETED: sb1={}\nsb2={}", BsqParser.sb1.toString(), BsqParser.sb2.toString());
        //log.error("equals? " + BsqParser.sb1.toString().equals(BsqParser.sb2.toString()));
        //Utilities.copyToClipboard(BsqParser.sb1.toString() + "\n\n\n" + BsqParser.sb2.toString());

        return block;
    }
}
