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
     * @param btcBlock  Contains all transactions of a BTC block
     * @return bsqBlock: Is a clone of btcBlock but filtered so it contains only bsq transactions
     * @throws BlockNotConnectingException If new block does not connect to previous block
     */
    Block parseBlock(Block btcBlock) throws BlockNotConnectingException {
        final int blockHeight = btcBlock.getHeight();
        stateService.setNewBlockHeight(blockHeight);

        long startTs = System.currentTimeMillis();
        List<Tx> bsqTxs = getBsqTxsFromBlock(btcBlock);

        final Block bsqBlock = new Block(blockHeight,
                btcBlock.getTime(),
                btcBlock.getHash(),
                btcBlock.getPreviousBlockHash(),
                ImmutableList.copyOf(bsqTxs));

        if (blockValidator.validate(bsqBlock))
            stateService.addNewBlock(bsqBlock);

        log.debug("parseBlock took {} ms at blockHeight {}; bsqTxs.size={}",
                System.currentTimeMillis() - startTs, blockHeight, bsqTxs.size());
        return bsqBlock;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private List<Tx> getBsqTxsFromBlock(Block block) {
        int blockHeight = block.getHeight();
        log.debug("Parse block at height={} ", blockHeight);

        // We use a list as we want to maintain sorting of tx intra-block dependency
        List<Tx> bsqTxsInBlock = new ArrayList<>();

        // We check first for genesis tx
        for (Tx tx : block.getTxs()) {
            checkForGenesisTx(blockHeight, bsqTxsInBlock, tx);
        }

        // Worst case is that all txs in a block are depending on another, so only one get resolved at each iteration.
        // Min tx size is 189 bytes (normally about 240 bytes), 1 MB can contain max. about 5300 txs (usually 2000).
        // Realistically we don't expect more then a few recursive calls.
        // There are some blocks with testing such dependency chains like block 130768 where at each iteration only
        // one get resolved.
        // Lately there is a patter with 24 iterations observed
        long startTs = System.currentTimeMillis();
        recursiveFindBsqTxs(bsqTxsInBlock, block.getTxs(), blockHeight, 0, 5300);
        log.debug("recursiveFindBsqTxs took {} ms",
                block.getTxs().size(), System.currentTimeMillis() - startTs);
        return bsqTxsInBlock;
    }
}
