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

package bisq.core.dao.node;

import bisq.core.dao.node.validation.BlockValidator;
import bisq.core.dao.node.validation.GenesisTxValidator;
import bisq.core.dao.node.validation.TxValidator;
import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.state.blockchain.Block;
import bisq.core.dao.state.blockchain.RawBlock;
import bisq.core.dao.state.blockchain.RawTx;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.blockchain.TxOutput;

import javax.inject.Inject;

import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.concurrent.Immutable;

/**
 * Base class for lite node parser and full node parser. Iterates blocks to find BSQ relevant transactions.
 * <p>
 * We are in threaded context. Don't mix up with UserThread.
 */
@Slf4j
@Immutable
public abstract class BsqParser {
    protected final BlockValidator blockValidator;
    private final TxValidator txValidator;
    protected final BsqStateService bsqStateService;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public BsqParser(BlockValidator blockValidator,
                     GenesisTxValidator genesisTxValidator,
                     TxValidator txValidator,
                     BsqStateService bsqStateService) {
        this.blockValidator = blockValidator;
        this.txValidator = txValidator;
        this.bsqStateService = bsqStateService;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////
    protected void maybeAddGenesisTx(RawBlock rawBlock, Block block) {
        // We don't use streams here as we want to break as soon we found the genesis
        for (RawTx rawTx : rawBlock.getRawTxs()) {
            Optional<Tx> optionalTx = GenesisTxValidator.getGenesisTx(
                    bsqStateService.getGenesisTxId(),
                    bsqStateService.getGenesisBlockHeight(),
                    bsqStateService.getGenesisTotalSupply(),
                    rawTx);
            if (optionalTx.isPresent()) {
                Tx genesisTx = optionalTx.get();
                block.getTxs().add(genesisTx);

                for (int i = 0; i < genesisTx.getTxOutputs().size(); ++i) {
                    TxOutput txOutput = genesisTx.getTxOutputs().get(i);
                    bsqStateService.addUnspentTxOutput(txOutput);
                }

                break;
            }
        }
    }

    protected void parseBsqTxs(Block block, List<RawTx> rawTxList) {
        rawTxList.forEach(rawTx -> txValidator.getBsqTx(rawTx).ifPresent(tx -> block.getTxs().add(tx)));
    }
}
