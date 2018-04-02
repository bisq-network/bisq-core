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

package bisq.core.dao.node.consensus;

import bisq.core.dao.blockchain.vo.Tx;
import bisq.core.dao.blockchain.vo.TxOutput;
import bisq.core.dao.blockchain.vo.TxOutputType;

import javax.inject.Inject;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Iterates all outputs for verification for BSQ outputs and sets the txType.
 */
@Slf4j
public class TxOutputsController {

    private final TxOutputController txOutputController;

    @Inject
    public TxOutputsController(TxOutputController txOutputController) {
        this.txOutputController = txOutputController;
    }

    void processOpReturnCandidate(Tx tx, Model model) {
        // We use order of output index. An output is a BSQ utxo as long there is enough input value
        final List<TxOutput> outputs = tx.getOutputs();

        // We start with last output as that might be an OP_RETURN output and gives us the specific tx type, so it is
        // easier and cleaner at parsing the other outputs to detect which kind of tx we deal with.
        // Setting the opReturn type here does not mean it will be a valid BSQ tx as the checks are only partial and
        // BSQ inputs are not verified yet.
        // We keep the temporary opReturn type in the model object.
        checkArgument(!outputs.isEmpty(), "outputs must not be empty");
        int lastIndex = outputs.size() - 1;
        txOutputController.processOpReturnCandidate(outputs.get(lastIndex), model);
    }

    void iterateOutputs(Tx tx, int blockHeight, Model model) {
        // We use order of output index. An output is a BSQ utxo as long there is enough input value
        final List<TxOutput> outputs = tx.getOutputs();
        // We iterate all outputs including the opReturn to do a full validation including the BSQ fee
        for (int index = 0; index < outputs.size(); index++) {
            txOutputController.processTxOutput(tx, outputs.get(index), index, blockHeight, model);
        }

        // If we have an issuanceCandidate and the type was not applied in the opReturnController we set
        // it now to an BTC_OUTPUT.
        if (model.getIssuanceCandidate() != null &&
                model.getIssuanceCandidate().getTxOutputType() == TxOutputType.UNDEFINED)
            model.getIssuanceCandidate().setTxOutputType(TxOutputType.BTC_OUTPUT);
    }

    boolean isAnyTxOutputTypeUndefined(Tx tx) {
        return tx.getOutputs().stream().anyMatch(txOutput -> TxOutputType.UNDEFINED == txOutput.getTxOutputType());
    }
}
