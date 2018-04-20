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

package bisq.core.dao.presentation.state;

import bisq.core.dao.DaoOptionKeys;
import bisq.core.dao.consensus.period.Cycle;
import bisq.core.dao.consensus.state.BaseStateService;
import bisq.core.dao.consensus.state.Block;
import bisq.core.dao.consensus.state.State;
import bisq.core.dao.consensus.state.StateChangeListener;
import bisq.core.dao.consensus.state.StateService;
import bisq.core.dao.consensus.state.blockchain.SpentInfo;
import bisq.core.dao.consensus.state.blockchain.TxBlock;
import bisq.core.dao.consensus.state.blockchain.TxOutput;
import bisq.core.dao.consensus.state.blockchain.TxOutputType;
import bisq.core.dao.consensus.state.blockchain.TxType;
import bisq.core.dao.presentation.PresentationService;

import org.bitcoinj.core.Coin;

import javax.inject.Inject;
import javax.inject.Named;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;


/**
 *
 * Read access to state from the user thread.
 * We get the listener called on the user thread and keep an independent copy of the state.
 * Updates in state are reflected here with the delay caused by the thread mapping.
 *
 * This class must not be used for consensus critical aspects but only for presentation purposes.
 */
@Slf4j
public class StateServiceFacade extends BaseStateService implements PresentationService {
    private final State userThreadState;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public StateServiceFacade(State state,
                              StateService stateService,
                              @Named(DaoOptionKeys.GENESIS_TX_ID) String genesisTxId,
                              @Named(DaoOptionKeys.GENESIS_BLOCK_HEIGHT) int genesisBlockHeight) {
        super();

        userThreadState = new State(genesisTxId, genesisBlockHeight);

        // We get the updates from the parser thread based state where on write access our listener gets executed on
        // the user thread.
        state.addStateChangeListener(new StateChangeListener() {
            @Override
            public void onAddBlock(Block block) {
                userThreadState.addBlock(block);

                blockListeners.forEach(l -> l.onBlockAdded(block));
            }

            @Override
            public void onPutTxType(String txId, TxType txType) {
                userThreadState.putTxType(txId, txType);
            }

            @Override
            public void onPutBurntFee(String txId, long burnedFee) {
                userThreadState.putBurntFee(txId, burnedFee);
            }

            @Override
            public void onAddUnspentTxOutput(TxOutput txOutput) {
                userThreadState.addUnspentTxOutput(txOutput);
            }

            @Override
            public void onRemoveUnspentTxOutput(TxOutput txOutput) {
                userThreadState.removeUnspentTxOutput(txOutput);
            }

            @Override
            public void onPutIssuanceBlockHeight(TxOutput txOutput, int chainHeight) {
                userThreadState.putIssuanceBlockHeight(txOutput, chainHeight);
            }

            @Override
            public void onPutSpentInfo(TxOutput txOutput, int blockHeight, String txId, int inputIndex) {
                userThreadState.putSpentInfo(txOutput, blockHeight, txId, inputIndex);
            }

            @Override
            public void onPutTxOutputType(TxOutput txOutput, TxOutputType txOutputType) {
                userThreadState.putTxOutputType(txOutput, txOutputType);
            }

            @Override
            public void onAddCycle(Cycle cycle) {
                userThreadState.addCycle(cycle);
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Read access
    ///////////////////////////////////////////////////////////////////////////////////////////

    public List<TxBlock> getClonedBlocksFrom(int fromBlockHeight) {
        final LinkedList<Block> clonedBlocks = new LinkedList<>(getBlocks());
        return getTxBlocks(clonedBlocks).stream()
                .filter(block -> block.getHeight() >= fromBlockHeight)
                .collect(Collectors.toList());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Genesis
    public String getGenesisTxId() {
        return userThreadState.getGenesisTxId();
    }

    public int getGenesisBlockHeight() {
        return userThreadState.getGenesisBlockHeight();
    }

    public Coin getGenesisTotalSupply() {
        return userThreadState.getGenesisTotalSupply();
    }

    // Block
    @Override
    public LinkedList<Block> getBlocks() {
        return userThreadState.getBlocks();
    }

    // Tx
    @Override
    public Map<String, TxType> getTxTypeMap() {
        return userThreadState.getTxTypeMap();
    }

    @Override
    public Map<String, Long> getBurntFeeMap() {
        return userThreadState.getBurntFeeMap();
    }

    @Override
    public Map<String, Integer> getIssuanceBlockHeightMap() {
        return userThreadState.getIssuanceBlockHeightMap();
    }

    // TxOutput
    @Override
    public Map<TxOutput.Key, TxOutput> getUnspentTxOutputMap() {
        return userThreadState.getUnspentTxOutputMap();
    }

    @Override
    public Map<TxOutput.Key, SpentInfo> getTxOutputSpentInfoMap() {
        return userThreadState.getSpentInfoMap();
    }

    @Override
    public Map<TxOutput.Key, TxOutputType> getTxOutputTypeMap() {
        return userThreadState.getTxOutputTypeMap();
    }

    // Cycle
    @Override
    public List<Cycle> getCycles() {
        return userThreadState.getCycles();
    }
}

