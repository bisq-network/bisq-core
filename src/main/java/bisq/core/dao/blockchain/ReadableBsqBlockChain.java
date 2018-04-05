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

package bisq.core.dao.blockchain;

import bisq.core.dao.blockchain.vo.BsqBlock;
import bisq.core.dao.blockchain.vo.Tx;
import bisq.core.dao.blockchain.vo.TxOutput;
import bisq.core.dao.blockchain.vo.TxType;
import bisq.core.dao.blockchain.vo.util.TxIdIndexTuple;

import org.bitcoinj.core.Coin;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface ReadableBsqBlockChain {
    // listeners

    void addListener(BsqBlockChain.Listener listener);

    void removeListener(BsqBlockChain.Listener listener);

    void addIssuanceListener(BsqBlockChain.IssuanceListener listener);

    void removeIssuanceListener(BsqBlockChain.IssuanceListener listener);


    int getChainHeadHeight();

    boolean containsBsqBlock(BsqBlock bsqBlock);

    List<BsqBlock> getClonedBlocksFrom(int fromBlockHeight);

    Map<String, Tx> getTxMap();

    Tx getGenesisTx();

    Optional<Tx> getTx(String txId);

    Set<Tx> getTransactions();

    Set<Tx> getFeeTransactions();

    boolean hasTxBurntFee(String txId);

    String getGenesisTxId();

    int getGenesisBlockHeight();

    boolean containsTx(String txId);

    Set<TxOutput> getVoteRevealTxOutputs();

    Set<TxOutput> getCompReqIssuanceTxOutputs();

    Optional<TxOutput> getUnspentAndMatureTxOutput(TxIdIndexTuple txIdIndexTuple);

    Optional<TxOutput> getUnspentAndMatureTxOutput(String txId, int index);

    boolean isTxOutputSpendable(String txId, int index);

    Set<TxOutput> getUnspentTxOutputs();

    Set<TxOutput> getBlindVoteStakeTxOutputs();

    Set<TxOutput> getLockedInBondsOutputs();

    Set<TxOutput> getSpentTxOutputs();

    Optional<TxType> getTxType(String txId);

    long getBlockTime(int height);

    Coin getTotalBurntFee();

    Coin getIssuedAmountAtGenesis();

    LinkedList<BsqBlock> getBsqBlocks();

    BsqBlockChain getClone();

    BsqBlockChain getClone(BsqBlockChain bsqBlockChain);
}
