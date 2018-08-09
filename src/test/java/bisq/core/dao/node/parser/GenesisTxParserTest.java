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

package bisq.core.dao.node.parser;

import bisq.core.dao.state.blockchain.RawTx;
import bisq.core.dao.state.blockchain.RawTxOutput;
import bisq.core.dao.state.blockchain.TempTx;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.blockchain.TxInput;
import bisq.core.dao.state.blockchain.TxOutputType;
import bisq.core.dao.state.blockchain.TxType;

import org.bitcoinj.core.Coin;

import com.google.common.collect.ImmutableList;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.junit.Assert;
import org.junit.Test;

public class GenesisTxParserTest {

    @Test
    public void testGetGenesisTx() {
        int blockHeight = 200;
        String blockHash = "abc123";
        Coin genesisTotalSupply = Coin.parseCoin("2.5");
        long time = new Date().getTime();
        final List<TxInput> inputs = Arrays.asList(
                new TxInput("tx0", 0, null),
                new TxInput("tx1", 1, null)
        );
        final List<RawTxOutput> outputs = Arrays.asList(new RawTxOutput(0, 101, null, null, null, null, blockHeight));
        RawTx rawTx = new RawTx(
            "tx2",
            blockHeight,
            blockHash,
            time,
            ImmutableList.copyOf(inputs),
            ImmutableList.copyOf(outputs)
        );

        String genesisTxId = "genesisTxId";
        int genesisBlockHeight = 150;

        // With mismatch in block height and tx id, we should not get genesis tx back.
        Optional<Tx> result = GenesisTxParser.findGenesisTx(genesisTxId, genesisBlockHeight, genesisTotalSupply, rawTx);
        Optional<Tx> want = Optional.empty();
        Assert.assertEquals(want, result);

        // With correct block height but mismatch in tx id, we should still not get genesis tx back.
        blockHeight = 150;
        rawTx = new RawTx(
            "tx2",
            blockHeight,
            blockHash,
            time,
            ImmutableList.copyOf(inputs),
            ImmutableList.copyOf(outputs)
        );
        result = GenesisTxParser.findGenesisTx(genesisTxId, genesisBlockHeight, genesisTotalSupply, rawTx);
        want = Optional.empty();
        Assert.assertEquals(want, result);

        // With correct tx id and block height, we should find our genesis tx with correct tx and output type.
        rawTx = new RawTx(
            genesisTxId,
            blockHeight,
            blockHash,
            time,
            ImmutableList.copyOf(inputs),
            ImmutableList.copyOf(outputs)
        );
        result = GenesisTxParser.findGenesisTx(genesisTxId, genesisBlockHeight, genesisTotalSupply, rawTx);

        TempTx tempTx = TempTx.fromRawTx(rawTx);
        tempTx.setTxType(TxType.GENESIS);
        for (int i = 0; i < tempTx.getTempTxOutputs().size(); ++i) {
            tempTx.getTempTxOutputs().get(i).setTxOutputType(TxOutputType.GENESIS_OUTPUT);
        }
        Tx tx = Tx.fromTempTx(tempTx);
        want = Optional.of(tx);

        Assert.assertEquals(want, result);
        // TODO(chirhonul): test that only outputs in tx summing exactly to genesisTotalSupply is accepted, and
        // that code under test raises RuntimeError otherwise.
    }
}
