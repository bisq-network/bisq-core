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

import bisq.core.dao.blockchain.WritableBsqBlockChain;
import bisq.core.dao.blockchain.vo.Tx;
import bisq.core.dao.blockchain.vo.TxOutput;
import bisq.core.dao.blockchain.vo.TxOutputType;
import bisq.core.dao.consensus.OpReturnTypes;

import bisq.common.util.Utilities;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * Checks if an output is a BSQ output and apply state change.
 */

@Slf4j
public class TxOutputController {
    private final WritableBsqBlockChain writableBsqBlockChain;
    private final OpReturnController opReturnController;

    @Inject
    public TxOutputController(WritableBsqBlockChain writableBsqBlockChain, OpReturnController opReturnController) {
        this.writableBsqBlockChain = writableBsqBlockChain;
        this.opReturnController = opReturnController;
    }

    void verifyOpReturnCandidate(TxOutput txOutput, Model model) {
        if (opReturnController.verifyOpReturnCandidate(txOutput)) {
            opReturnController.setOpReturnTypeCandidate(txOutput, model);
        } else {
            log.warn("OP_RETURN data did not match our rules. txOutput={}",
                    Utilities.bytesAsHexString(txOutput.getOpReturnData()));
        }
    }

    void verify(Tx tx, TxOutput txOutput, int index, int blockHeight, Model model) {
        final long bsqInputBalanceValue = model.getAvailableInputValue();
        // We do not check for pubKeyScript.scriptType.NULL_DATA because that is only set if dumpBlockchainData is true
        if (txOutput.getOpReturnData() == null) {
            final long txOutputValue = txOutput.getValue();
            if (bsqInputBalanceValue > 0 && bsqInputBalanceValue >= txOutputValue) {
                handleBsqOutput(txOutput, index, model, txOutputValue);
            } else {
                handleBtcOutput(txOutput, index, model);
            }
        } else {
            // We got a OP_RETURN output.
            opReturnController.process(txOutput, tx, index, bsqInputBalanceValue, blockHeight, model);
        }
    }

    private void handleBsqOutput(TxOutput txOutput, int index, Model model, long txOutputValue) {
        // Update the input balance.
        model.subtractFromInputValue(txOutputValue);

        // At a blind vote tx we get the stake at output 0.
        if (index == 0 && model.getOpReturnTypeCandidate() == OpReturnTypes.BLIND_VOTE) {
            // First output might be vote stake output.
            model.setBlindVoteStakeOutput(txOutput);

            // We don't set the txOutputType yet as we have not fully validated the tx but keep the candidate
            // in the model.
            applyStateChangeForBsqOutput(txOutput, null);
        } else {
            applyStateChangeForBsqOutput(txOutput, TxOutputType.BSQ_OUTPUT);
        }

        model.setAnyBsqOutputFound(true);
    }

    private void handleBtcOutput(TxOutput txOutput, int index, Model model) {
        // We have BSQ left for burning
        if (model.isInputValuePositive()) {
            // At the second output we might have a compensation request output if the opReturn type matches.
            if (index == 1 && model.getOpReturnTypeCandidate() == OpReturnTypes.COMPENSATION_REQUEST) {
                // We don't set the txOutputType yet as we have not fully validated the tx but keep the candidate
                // in the model.
                model.setIssuanceCandidate(txOutput);
            }
        }

        applyStateChangeForBtcOutput(txOutput);
    }

    protected void applyStateChangeForBsqOutput(TxOutput txOutput, @Nullable TxOutputType txOutputType) {
        txOutput.setVerified(true);
        txOutput.setUnspent(true);
        if (txOutputType != null)
            txOutput.setTxOutputType(txOutputType);
        writableBsqBlockChain.addUnspentTxOutput(txOutput);
    }

    protected void applyStateChangeForBtcOutput(TxOutput txOutput) {
        txOutput.setTxOutputType(TxOutputType.BTC_OUTPUT);
    }
}
