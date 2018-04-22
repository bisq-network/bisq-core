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

package bisq.core.dao.consensus.node.full;

import bisq.core.dao.consensus.node.BsqParser;
import bisq.core.dao.consensus.node.blockchain.exceptions.BlockNotConnectingException;
import bisq.core.dao.consensus.node.consensus.BsqBlockController;
import bisq.core.dao.consensus.node.consensus.BsqTxController;
import bisq.core.dao.consensus.node.consensus.GenesisTxController;
import bisq.core.dao.consensus.period.PeriodStateMutator;
import bisq.core.dao.consensus.state.StateService;
import bisq.core.dao.consensus.state.blockchain.Tx;
import bisq.core.dao.consensus.state.blockchain.TxBlock;

import com.neemre.btcdcli4j.core.domain.Block;

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
    public FullNodeParser(BsqBlockController bsqBlockController,
                          GenesisTxController genesisTxController,
                          BsqTxController bsqTxController,
                          StateService stateService,
                          PeriodStateMutator periodStateMutator) {
        super(bsqBlockController, genesisTxController, bsqTxController, stateService, periodStateMutator);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Package private
    ///////////////////////////////////////////////////////////////////////////////////////////

    TxBlock parseBlock(Block btcdBlock, List<Tx> txList) throws BlockNotConnectingException {
        periodStateMutator.onStartParsingNewBlock(btcdBlock.getHeight());

        long startTs = System.currentTimeMillis();
        List<Tx> bsqTxsInBlock = findBsqTxsInBlock(btcdBlock, txList);

        final TxBlock txBlock = new TxBlock(btcdBlock.getHeight(),
                btcdBlock.getTime(),
                btcdBlock.getHash(),
                btcdBlock.getPreviousBlockHash(),
                ImmutableList.copyOf(bsqTxsInBlock));

        bsqBlockController.addBlockIfValid(txBlock);

        log.debug("parseBlock took {} ms at blockHeight {}; bsqTxsInBlock.size={}",
                System.currentTimeMillis() - startTs, txBlock.getHeight(), bsqTxsInBlock.size());
        return txBlock;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private List<Tx> findBsqTxsInBlock(Block btcdBlock, List<Tx> txList) {
        int blockHeight = btcdBlock.getHeight();
        log.debug("Parse block at height={} ", blockHeight);

        // Check if the new block is the same chain we have built on.
        // We use a list as we want to maintain sorting of tx intra-block dependency
        List<Tx> bsqTxsInBlock = new ArrayList<>();
        // We add all transactions to the block
        long startTs = System.currentTimeMillis();

        // We check first for genesis tx
        for (Tx tx : txList) {
            checkForGenesisTx(blockHeight, bsqTxsInBlock, tx);
        }
        log.debug("Requesting {} transactions took {} ms",
                btcdBlock.getTx().size(), System.currentTimeMillis() - startTs);
        // Worst case is that all txs in a block are depending on another, so only one get resolved at each iteration.
        // Min tx size is 189 bytes (normally about 240 bytes), 1 MB can contain max. about 5300 txs (usually 2000).
        // Realistically we don't expect more then a few recursive calls.
        // There are some blocks with testing such dependency chains like block 130768 where at each iteration only
        // one get resolved.
        // Lately there is a patter with 24 iterations observed
        recursiveFindBsqTxs(bsqTxsInBlock, txList, blockHeight, 0, 5300);

        return bsqTxsInBlock;
    }
}
