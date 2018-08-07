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

package bisq.core.dao.node.parser.exceptions;

import bisq.core.dao.state.blockchain.RawTx;
import bisq.core.dao.state.blockchain.TempTx;
import bisq.core.dao.state.blockchain.TempTxOutput;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.blockchain.TxOutputType;
import bisq.core.dao.state.blockchain.TxType;

import org.bitcoinj.core.Coin;

import java.util.Optional;

/**
 * Verifies if a given transaction is a BSQ genesis transaction.
 */
public class GenesisTxParser {
    public static Optional<Tx> findGenesisTx(String genesisTxId, int genesisBlockHeight, Coin genesisTotalSupply, RawTx rawTx) {
        boolean isGenesis = rawTx.getBlockHeight() == genesisBlockHeight &&
                rawTx.getId().equals(genesisTxId);
        if (!isGenesis)
            return Optional.empty();

        TempTx tempTx = TempTx.fromRawTx(rawTx);
        tempTx.setTxType(TxType.GENESIS);
        long availableInputValue = genesisTotalSupply.getValue();
        for (int i = 0; i < tempTx.getTempTxOutputs().size(); ++i) {
            TempTxOutput txOutput = tempTx.getTempTxOutputs().get(i);
            long value = txOutput.getValue();
            boolean isValid = value <= availableInputValue;
            if (!isValid)
                throw new InvalidGenesisTxException("Genesis tx is isValid. " +
                        "availableInputValue=" + availableInputValue + "; txOutput=" + txOutput.toString());

            availableInputValue -= value;
            txOutput.setTxOutputType(TxOutputType.GENESIS_OUTPUT);
        }

        return Optional.of(Tx.fromTempTx(tempTx));
    }
}
