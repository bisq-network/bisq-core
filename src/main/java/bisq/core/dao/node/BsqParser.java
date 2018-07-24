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
import bisq.core.dao.state.BsqState;
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
    //public static StringBuilder sb1 = new StringBuilder();
    //public static StringBuilder sb2 = new StringBuilder();

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

    protected void maybeAddGenesisTx(RawBlock rawBlock, int blockHeight, Block block) {
        // We don't use streams here as we want to break as soon we found the genesis
        for (RawTx rawTx : rawBlock.getRawTxs()) {
            Optional<Tx> optionalTx = GenesisTxValidator.getGenesisTx(BsqState.getDefaultGenesisTxId(),
                    BsqState.getDefaultGenesisBlockHeight(),
                    rawTx,
                    blockHeight);
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
        // sb1.append("\nblock ").append(block.getHeight()).append("\n");
        //sb1.append(Joiner.on("\n").join(rawTxList.stream().map(RawTx::getId).collect(Collectors.toList())));

        rawTxList.forEach(rawTx -> txValidator.getBsqTx(rawTx).ifPresent(tx -> block.getTxs().add(tx)));
    }

    /*protected void recursiveFindBsqTxs1(Block block,
                                        List<RawTx> rawTxList,
                                        int recursionCounter,
                                        int maxRecursions) {
        if (recursionCounter == 0) {
            sb2.append("\nblock ").append(block.getHeight()).append("\n");
        }
        // The set of txIds of txs which are used for inputs of another tx in same block
        Set<String> intraBlockSpendingTxIdSet = getIntraBlockSpendingTxIdSet(rawTxList);

        List<RawTx> txsWithoutInputsFromSameBlock = new ArrayList<>();
        List<RawTx> txsWithInputsFromSameBlock = new ArrayList<>();

        // First we find the txs which have no intra-block inputs
        outerLoop:
        for (RawTx rawTx : rawTxList) {
            for (TxInput input : rawTx.getTxInputs()) {
                if (intraBlockSpendingTxIdSet.contains(input.getConnectedTxOutputTxId())) {
                    // We have an input from one of the intra-block-transactions, so we cannot process that tx now.
                    // We add the tx for later parsing to the txsWithInputsFromSameBlock and move to the next tx.
                    txsWithInputsFromSameBlock.add(rawTx);
                    continue outerLoop;
                }
            }
            // If we have not found any tx input pointing to anther tx in the same block we add it to our
            // txsWithoutInputsFromSameBlock.
            txsWithoutInputsFromSameBlock.add(rawTx);
        }


        sb2.append(Joiner.on("\n").join(txsWithoutInputsFromSameBlock.stream().map(RawTx::getId).collect(Collectors.toList()))).append(" #" + recursionCounter + "\n");
       *//* txsWithoutInputsFromSameBlock.forEach(rawTx -> {
                    txValidator.getBsqTx(rawTx).ifPresent(tx -> block.getTxs().add(tx));
                }
        );*//*

        if (!txsWithInputsFromSameBlock.isEmpty()) {
            if (recursionCounter < maxRecursions) {
                recursiveFindBsqTxs1(block, txsWithInputsFromSameBlock,
                        ++recursionCounter, maxRecursions);
            }
        } else {
            log.debug("We have no more txsWithInputsFromSameBlock.");
        }
    }

    // TODO check with mainnet and testnet data if the sorting is required. txs should be sorted already by dependency
    // Performance-wise the recursion does not hurt (e.g. 5-20 ms).
    // The RPC requestTransaction is the bottleneck.
    // There are some blocks with testing the dependency chains like block 130768 where at each iteration only
    // one get resolved.
    protected void recursiveFindBsqTxs(Block block,
                                       List<RawTx> rawTxList,
                                       int recursionCounter,
                                       int maxRecursions) {
        if (recursionCounter > 20)
            log.error("recursiveFindBsqTxs: recursionCounter={}, height={}, txs={}", recursionCounter, block.getHeight(), block.getTxs().size());
        // The set of txIds of txs which are used for inputs of another tx in same block
        Set<String> intraBlockSpendingTxIdSet = getIntraBlockSpendingTxIdSet(rawTxList);

        List<RawTx> txsWithoutInputsFromSameBlock = new ArrayList<>();
        List<RawTx> txsWithInputsFromSameBlock = new ArrayList<>();

        // First we find the txs which have no intra-block inputs
        outerLoop:
        for (RawTx rawTx : rawTxList) {
            for (TxInput input : rawTx.getTxInputs()) {
                if (intraBlockSpendingTxIdSet.contains(input.getConnectedTxOutputTxId())) {
                    // We have an input from one of the intra-block-transactions, so we cannot process that tx now.
                    // We add the tx for later parsing to the txsWithInputsFromSameBlock and move to the next tx.
                    txsWithInputsFromSameBlock.add(rawTx);
                    continue outerLoop;
                }
            }
            // If we have not found any tx input pointing to anther tx in the same block we add it to our
            // txsWithoutInputsFromSameBlock.
            txsWithoutInputsFromSameBlock.add(rawTx);
        }
        checkArgument(txsWithInputsFromSameBlock.size() + txsWithoutInputsFromSameBlock.size() == rawTxList.size(),
                "txsWithInputsFromSameBlock.size + txsWithoutInputsFromSameBlock.size != transactions.size");

        // Usual values is up to 25
        // There are some blocks where it seems developers have tested graphs of many depending txs, but even
        // those don't exceed 200 recursions and are mostly old blocks from 2012 when fees have been low ;-).
        // TODO check strategy btc core uses (sorting the dependency graph would be an optimisation)
        // Seems btc core delivers tx list sorted by dependency graph. -> TODO verify and test
        if (recursionCounter > 1000) {
            log.warn("Unusual high recursive calls at resolveConnectedTxs. recursionCounter=" + recursionCounter);
            log.warn("blockHeight=" + rawTxList.get(0).getBlockHeight());
            log.warn("txsWithoutInputsFromSameBlock.size " + txsWithoutInputsFromSameBlock.size());
            log.warn("txsWithInputsFromSameBlock.size " + txsWithInputsFromSameBlock.size());
            //  log.warn("txsWithInputsFromSameBlock " + txsWithInputsFromSameBlock.stream().map(e->e.getId()).collect(Collectors.toList()));
        }

        // we check if we have any valid BSQ from that tx set
        txsWithoutInputsFromSameBlock.forEach(rawTx ->
                txValidator.getBsqTx(rawTx).ifPresent(tx -> block.getTxs().add(tx))
        );

        log.debug("Parsing of all txsWithoutInputsFromSameBlock is done.");

        // we check if we have any valid BSQ utxo from that tx set
        // We might have InputsFromSameBlock which are BTC only but not BSQ, so we cannot
        // optimize here and need to iterate further.
        if (!txsWithInputsFromSameBlock.isEmpty()) {
            if (recursionCounter < maxRecursions) {
                recursiveFindBsqTxs(block, txsWithInputsFromSameBlock,
                        ++recursionCounter, maxRecursions);
            } else {
                final String msg = "We exceeded our max. recursions for resolveConnectedTxs.\n" +
                        "txsWithInputsFromSameBlock=" + txsWithInputsFromSameBlock.toString() + "\n" +
                        "txsWithoutInputsFromSameBlock=" + txsWithoutInputsFromSameBlock;
                DevEnv.logErrorAndThrowIfDevMode(msg);
            }
        } else {
            log.debug("We have no more txsWithInputsFromSameBlock.");
        }
    }

    private Set<String> getIntraBlockSpendingTxIdSet(List<RawTx> rawTxList) {
        Set<String> txIdSet = rawTxList.stream().map(RawTx::getId).collect(Collectors.toSet());
        Set<String> intraBlockSpendingTxIdSet = new HashSet<>();
        rawTxList.forEach(tx -> tx.getTxInputs().stream()
                .filter(input -> txIdSet.contains(input.getConnectedTxOutputTxId()))
                .forEach(input -> intraBlockSpendingTxIdSet.add(input.getConnectedTxOutputTxId())));
        return intraBlockSpendingTxIdSet;
    }*/
}
