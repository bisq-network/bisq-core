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
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.Block;
import bisq.core.dao.state.blockchain.RawBlock;
import bisq.core.dao.state.blockchain.RawTx;
import bisq.core.dao.state.blockchain.Tx;

import javax.inject.Inject;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;

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
                          StateService stateService) {
        super(blockValidator, genesisTxValidator, txValidator, stateService);
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
        stateService.setNewBlockHeight(blockHeight);

        long startTs = System.currentTimeMillis();
        List<Tx> bsqTxs = getBsqTxsFromBlock(rawBlock);

        final Block block = new Block(blockHeight,
                rawBlock.getTime(),
                rawBlock.getHash(),
                rawBlock.getPreviousBlockHash(),
                ImmutableList.copyOf(bsqTxs));

        // TODO needed?
        if (blockValidator.isBlockNotAlreadyAdded(rawBlock))
            stateService.addNewBlock(block);

        log.debug("parseBlock took {} ms at blockHeight {}; bsqTxs.size={}",
                System.currentTimeMillis() - startTs, blockHeight, bsqTxs.size());

        //TODO do we want to return the block in case we had it already?
        return block;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private List<Tx> getBsqTxsFromBlock(RawBlock rawBlock) {
        int blockHeight = rawBlock.getHeight();
        log.debug("Parse block at height={} ", blockHeight);

        // We use a list as we want to maintain sorting of tx intra-block dependency
        List<Tx> bsqTxsInBlock = new ArrayList<>();

        // We check first for genesis tx
        for (RawTx rawTx : rawBlock.getRawTxs()) {
            checkForGenesisTx(blockHeight, bsqTxsInBlock, rawTx);
        }

        // Worst case is that all txs in a block are depending on another, so only one get resolved at each iteration.
        // Min tx size is 189 bytes (normally about 240 bytes), 1 MB can contain max. about 5300 txs (usually 2000).
        // Realistically we don't expect more then a few recursive calls.
        // There are some blocks with testing such dependency chains like block 130768 where at each iteration only
        // one get resolved.
        // Lately there is a patter with 24 iterations observed
        long startTs = System.currentTimeMillis();
        recursiveFindBsqTxs(bsqTxsInBlock, rawBlock.getRawTxs(), blockHeight, 0, 5300);
        log.debug("recursiveFindBsqTxs took {} ms",
                rawBlock.getRawTxs().size(), System.currentTimeMillis() - startTs);
        return bsqTxsInBlock;
    }
}
