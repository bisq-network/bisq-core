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
import bisq.core.dao.consensus.OpReturnType;

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

    void processOpReturnCandidate(TxOutput txOutput, Model model) {
        opReturnController.processOpReturnCandidate(txOutput, model);
    }

    void processTxOutput(Tx tx, TxOutput txOutput, int index, int blockHeight, Model model) {
        final long bsqInputBalanceValue = model.getAvailableInputValue();
        // We do not check for pubKeyScript.scriptType.NULL_DATA because that is only set if dumpBlockchainData is true
        final byte[] opReturnData = txOutput.getOpReturnData();
        if (opReturnData == null) {
            final long txOutputValue = txOutput.getValue();
            if (bsqInputBalanceValue > 0 && bsqInputBalanceValue >= txOutputValue) {
                handleBsqOutput(txOutput, index, model, txOutputValue);
            } else {
                handleBtcOutput(txOutput, index, model);
            }
        } else {
            // We got a OP_RETURN output.
            opReturnController.processTxOutput(opReturnData, txOutput, tx, index, bsqInputBalanceValue, blockHeight, model);
        }
    }

    private void handleBsqOutput(TxOutput txOutput, int index, Model model, long txOutputValue) {
        // Update the input balance.
        model.subtractFromInputValue(txOutputValue);

        // At a blind vote tx we get the stake at output 0.
        if (index == 0 && model.getOpReturnTypeCandidate() == OpReturnType.BLIND_VOTE) {
            // First output might be vote stake output.
            model.setBlindVoteLockStakeOutput(txOutput);

            // We don't set the txOutputType yet as we have not fully validated the tx but keep the candidate
            // in the model.
            applyStateChangeForBsqOutput(txOutput, null);
        } else if (index == 0 && model.getOpReturnTypeCandidate() == OpReturnType.VOTE_REVEAL) {
            // At a vote reveal tx we get the released stake at output 0.
            // First output might be stake release output.
            model.setVoteRevealUnlockStakeOutput(txOutput);

            // We don't set the txOutputType yet as we have not fully validated the tx but keep the candidate
            // in the model.
            applyStateChangeForBsqOutput(txOutput, null);
        } else {
            applyStateChangeForBsqOutput(txOutput, TxOutputType.BSQ_OUTPUT);
        }

        model.setBsqOutputFound(true);
    }

    private void handleBtcOutput(TxOutput txOutput, int index, Model model) {
        // If we have BSQ left for burning and at the second output a compensation request output we set the
        // candidate to the model and we don't apply the TxOutputType as we do that later as the OpReturn check.
        if (model.isInputValuePositive() &&
                index == 1 &&
                model.getOpReturnTypeCandidate() == OpReturnType.COMPENSATION_REQUEST) {
            // We don't set the txOutputType yet as we have not fully validated the tx but put the candidate
            // into our model.
            model.setIssuanceCandidate(txOutput);
        } else {
            applyStateChangeForBtcOutput(txOutput);
        }
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
