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

package bisq.core.dao.node.full;

import bisq.core.dao.node.validation.BlockNotConnectingException;
import bisq.core.dao.node.validation.BlockValidator;
import bisq.core.dao.node.validation.GenesisTxValidator;
import bisq.core.dao.node.validation.TxValidator;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.blockchain.TxInput;
import bisq.core.dao.state.blockchain.TxOutput;

import bisq.common.proto.persistable.PersistenceProtoResolver;

import org.bitcoinj.core.Coin;

import com.neemre.btcdcli4j.core.BitcoindException;
import com.neemre.btcdcli4j.core.CommunicationException;
import com.neemre.btcdcli4j.core.domain.RawBlock;
import com.neemre.btcdcli4j.core.domain.RawTransaction;

import com.google.common.collect.ImmutableList;

import java.io.File;

import java.math.BigDecimal;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import mockit.Expectations;
import mockit.Injectable;
import mockit.Tested;
import mockit.integration.junit4.JMockit;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

// Intro to jmockit can be found at http://jmockit.github.io/tutorial/Mocking.html
@Ignore
@RunWith(JMockit.class)
public class FullNodeParserTest {
    // @Tested classes are instantiated automatically when needed in a test case,
    // using injection where possible, see http://jmockit.github.io/tutorial/Mocking.html#tested
    // To force instantiate earlier, use availableDuringSetup
    @Tested(fullyInitialized = true, availableDuringSetup = true)
    FullNodeParser fullNodeParser;

    @Tested(fullyInitialized = true, availableDuringSetup = true)
    StateService stateService;

    // @Injectable are mocked resources used to for injecting into @Tested classes
    // The naming of these resources doesn't matter, any resource that fits will be used for injection

    // Used by stateService
    @Injectable
    PersistenceProtoResolver persistenceProtoResolver;
    @Injectable
    File storageDir;
    @Injectable
    String genesisTxId = "genesisTxId";
    @Injectable
    int genesisBlockHeight = 200;

    // Used by fullNodeParser
    @Injectable
    RpcService rpcService;
    @Tested(fullyInitialized = true, availableDuringSetup = true)
    StateService writeModel;
    @Tested(fullyInitialized = true, availableDuringSetup = true)
    GenesisTxValidator genesisTxValidator;
    @Tested(fullyInitialized = true, availableDuringSetup = true)
    TxValidator txValidator;
    @Injectable
    BlockValidator blockValidator;

    @Test
    public void testIsBsqTx() {
        // Setup a basic transaction with two inputs
        int height = 200;
        String hash = "abc123";
        long time = new Date().getTime();
        final List<TxInput> inputs = asList(new TxInput("tx1", 0, null),
                new TxInput("tx1", 1, null));
        final List<TxOutput> outputs = asList(new TxOutput(0, 101, "tx1", null, null, null, height));
        Tx tx = new Tx("vo", height, hash, time,
                ImmutableList.copyOf(inputs),
                ImmutableList.copyOf(outputs));

        // Return one spendable txoutputs with value, for three test cases
        // 1) - null, 0     -> not BSQ transaction
        // 2) - 100, null   -> BSQ transaction
        // 3) - 0, 100      -> BSQ transaction
        new Expectations(stateService) {{
            // Expectations can be recorded on mocked instances, either with specific matching arguments or catch all
            // http://jmockit.github.io/tutorial/Mocking.html#results
            // Results are returned in the order they're recorded, so in this case for the first call to
            // getSpendableTxOutput("tx1", 0) the return value will be Optional.empty()
            // for the second call the return is Optional.of(new TxOutput(0,... and so on
            stateService.getUnspentAndMatureTxOutput(new TxOutput.Key("tx1", 0));
            result = Optional.empty();
            result = Optional.of(new TxOutput(0, 100, "txout1", null, null, null, height));
            result = Optional.of(new TxOutput(0, 0, "txout1", null, null, null, height));

            stateService.getUnspentAndMatureTxOutput(new TxOutput.Key("tx1", 1));
            result = Optional.of(new TxOutput(0, 0, "txout2", null, null, null, height));
            result = Optional.empty();
            result = Optional.of(new TxOutput(0, 100, "txout2", null, null, null, height));
        }};

        // First time there is no BSQ value to spend so it's not a bsq transaction
        assertFalse(txValidator.validate(height, tx));
        // Second time there is BSQ in the first txout
        assertTrue(txValidator.validate(height, tx));
        // Third time there is BSQ in the second txout
        assertTrue(txValidator.validate(height, tx));
    }

    @Test
    public void testParseBlocks() throws BitcoindException, CommunicationException, BlockNotConnectingException, RpcException {
        // Setup blocks to test, starting before genesis
        // Only the transactions related to bsq are relevant, no checks are done on correctness of blocks or other txs
        // so hashes and most other data don't matter
        long time = new Date().getTime();
        int genesisHeight = 200;
        int startHeight = 199;
        int headHeight = 201;
        Coin issuance = Coin.parseCoin("2.5");
        RawTransaction genTx = new RawTransaction("gen block hash", 0, 0L, 0L, genesisTxId);

        // Blockhashes
        String bh199 = "blockhash199";
        String bh200 = "blockhash200";
        String bh201 = "blockhash201";

        // Block 199
        String cbId199 = "cbid199";
        RawTransaction tx199 = new RawTransaction(bh199, 0, 0L, 0L, cbId199);
        Tx cbTx199 = new Tx(cbId199, 199, bh199, time,
                ImmutableList.copyOf(new ArrayList<TxInput>()),
                ImmutableList.copyOf(asList(new TxOutput(0, 25, cbId199, null, null, null, 199))));
        RawBlock block199 = new RawBlock(bh199, 10, 10, 199, 2, "root", asList(tx199), time, Long.parseLong("1234"), "bits", BigDecimal.valueOf(1), "chainwork", "previousBlockHash", bh200);

        // Genesis Block
        String cbId200 = "cbid200";
        RawTransaction tx200 = new RawTransaction(bh200, 0, 0L, 0L, cbId200);
        Tx cbTx200 = new Tx(cbId200, 200, bh200, time,
                ImmutableList.copyOf(new ArrayList<TxInput>()),
                ImmutableList.copyOf(asList(new TxOutput(0, 25, cbId200, null, null, null, 200))));
        Tx genesisTx = new Tx(genesisTxId, 200, bh200, time,
                ImmutableList.copyOf(asList(new TxInput("someoldtx", 0, null))),
                ImmutableList.copyOf(asList(new TxOutput(0, issuance.getValue(), genesisTxId, null, null, null, 200))));
        RawBlock block200 = new RawBlock(bh200, 10, 10, 200, 2, "root", asList(tx200, genTx), time, Long.parseLong("1234"), "bits", BigDecimal.valueOf(1), "chainwork", bh199, bh201);

        // Block 201
        // Make a bsq transaction
        String cbId201 = "cbid201";
        String bsqTx1Id = "bsqtx1";
        RawTransaction tx201 = new RawTransaction(bh201, 0, 0L, 0L, cbId201);
        RawTransaction txbsqtx1 = new RawTransaction(bh201, 0, 0L, 0L, bsqTx1Id);
        long bsqTx1Value1 = Coin.parseCoin("2.4").getValue();
        long bsqTx1Value2 = Coin.parseCoin("0.04").getValue();
        Tx cbTx201 = new Tx(cbId201, 201, bh201, time,
                ImmutableList.copyOf(new ArrayList<TxInput>()),
                ImmutableList.copyOf(asList(new TxOutput(0, 25, cbId201, null, null, null, 201))));
        Tx bsqTx1 = new Tx(bsqTx1Id, 201, bh201, time,
                ImmutableList.copyOf(asList(new TxInput(genesisTxId, 0, null))),
                ImmutableList.copyOf(asList(new TxOutput(0, bsqTx1Value1, bsqTx1Id, null, null, null, 201),
                        new TxOutput(1, bsqTx1Value2, bsqTx1Id, null, null, null, 201))));
        RawBlock block201 = new RawBlock(bh201, 10, 10, 201, 2, "root", asList(tx201, txbsqtx1), time, Long.parseLong("1234"), "bits", BigDecimal.valueOf(1), "chainwork", bh200, "nextBlockHash");

        // TODO update test with new API
        /*
        new Expectations(rpcService) {{
            rpcService.requestBlock(199);
            result = block199;
            rpcService.requestBlock(200);
            result = block200;
            rpcService.requestBlock(201);
            result = block201;

            rpcService.requestTx(cbId199, 199);
            result = cbTx199;
            rpcService.requestTx(cbId200, genesisHeight);
            result = cbTx200;
            rpcService.requestTx(genesisTxId, genesisHeight);
            result = genesisTx;
            rpcService.requestTx(cbId201, 201);
            result = cbTx201;
            rpcService.requestTx(bsqTx1Id, 201);
            result = bsqTx1;
        }};

        // Running parseBlocks to build the bsq blockchain
        fullNodeParser.parseBlocks(startHeight, headHeight, block -> {
        });
*/

        // Verify that the genesis tx has been added to the bsq blockchain with the correct issuance amount
    /*    assertTrue(stateService.getGenesisTx().get() == genesisTx);
        assertTrue(stateService.getGenesisTotalSupply().getValue() == issuance.getValue());

        // And that other txs are not added
        assertFalse(stateService.containsTx(cbId199));
        assertFalse(stateService.containsTx(cbId200));
        assertFalse(stateService.containsTx(cbId201));

        // But bsq txs are added
        assertTrue(stateService.containsTx(bsqTx1Id));
        TxOutput bsqOut1 = stateService.getUnspentAndMatureTxOutput(bsqTx1Id, 0).get();
        assertTrue(stateService.isUnspent(bsqOut1));
        assertTrue(bsqOut1.getValue() == bsqTx1Value1);
        TxOutput bsqOut2 = stateService.getUnspentAndMatureTxOutput(bsqTx1Id, 1).get();
        assertTrue(stateService.isUnspent(bsqOut2));
        assertTrue(bsqOut2.getValue() == bsqTx1Value2);
        assertFalse(stateService.isTxOutputSpendable(genesisTxId, 0));
        assertTrue(stateService.isTxOutputSpendable(bsqTx1Id, 0));
        assertTrue(stateService.isTxOutputSpendable(bsqTx1Id, 1));*/

    }
}
