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

package bisq.core.dao.state;

import bisq.core.dao.period.Cycle;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.blockchain.TxOutputType;
import bisq.core.dao.state.blockchain.TxType;

import bisq.common.ThreadAwareListener;

public interface StateChangeListener extends ThreadAwareListener {
    void onAddBlock(Block block);

    void onPutTxType(String txId, TxType txType);

    void onPutBurntFee(String txId, long burnedFee);

    void onAddUnspentTxOutput(TxOutput txOutput);

    void onRemoveUnspentTxOutput(TxOutput txOutput);

    void onPutIssuanceBlockHeight(TxOutput txOutput, int chainHeight);

    void onPutSpentInfo(TxOutput txOutput, int blockHeight, String txId, int inputIndex);

    void onPutTxOutputType(TxOutput txOutput, TxOutputType txOutputType);

    void onAddCycle(Cycle cycle);
}
