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

package bisq.core.dao.node.validation;

import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.blockchain.TxOutputType;

import javax.inject.Inject;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Iterates all outputs for verification for BSQ outputs and sets the txType.
 */
@Slf4j
public class TxOutputsIterator {

    private final TxOutputValidator txOutputValidator;
    private final StateService stateService;

    @Inject
    public TxOutputsIterator(TxOutputValidator txOutputValidator, StateService stateService) {
        this.txOutputValidator = txOutputValidator;
        this.stateService = stateService;
    }

    void processOpReturnCandidate(Tx tx, TxState txState) {
        final List<TxOutput> outputs = tx.getOutputs();

        // We start with last output as that might be an OP_RETURN output and gives us the specific tx type, so it is
        // easier and cleaner at parsing the other outputs to detect which kind of tx we deal with.
        // Setting the opReturn type here does not mean it will be a valid BSQ tx as the checks are only partial and
        // BSQ inputs are not verified yet.
        // We keep the temporary opReturn type in the txState object.
        checkArgument(!outputs.isEmpty(), "outputs must not be empty");
        int lastIndex = outputs.size() - 1;
        txOutputValidator.processOpReturnCandidate(outputs.get(lastIndex), txState);
    }

    void iterate(Tx tx, int blockHeight, TxState txState) {
        // We use order of output index. An output is a BSQ utxo as long there is enough input value
        final List<TxOutput> outputs = tx.getOutputs();
        // We iterate all outputs including the opReturn to do a full validation including the BSQ fee
        for (int index = 0; index < outputs.size(); index++) {
            txOutputValidator.processTxOutput(tx, outputs.get(index), index, blockHeight, txState);
        }
    }

    int getNumOpReturnOutputs(Tx tx) {
        return (int) tx.getOutputs().stream().filter(txOutputValidator::isOpReturnOutput).count();
    }

    boolean isAnyTxOutputTypeUndefined(Tx tx) {
        return tx.getOutputs().stream().anyMatch(txOutput -> TxOutputType.UNDEFINED == stateService.getTxOutputType(txOutput));
    }
}
