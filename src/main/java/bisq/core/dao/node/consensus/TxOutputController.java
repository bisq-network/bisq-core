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

import bisq.core.dao.OpReturnTypes;
import bisq.core.dao.blockchain.WritableBsqBlockChain;
import bisq.core.dao.blockchain.vo.Tx;
import bisq.core.dao.blockchain.vo.TxOutput;
import bisq.core.dao.blockchain.vo.TxOutputType;

import bisq.common.util.Utilities;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

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

    void verifyOpReturnCandidate(TxOutput txOutput,
                                 BsqTxController.BsqInputBalance bsqInputBalance,
                                 BsqTxController.MutableState mutableState) {
        if (bsqInputBalance.isPositive()) {
            if (opReturnController.verifyOpReturnCandidate(txOutput)) {
                opReturnController.setOpReturnTypeCandidate(txOutput, mutableState);
            } else {
                log.warn("OP_RETURN data do not match our rules. txOutput={}",
                        Utilities.bytesAsHexString(txOutput.getOpReturnData()));
            }
        } else {
            log.debug("We don't have any BSQ in the inputs so it is not a BSQ tx.");
        }
    }

    void verify(Tx tx,
                TxOutput txOutput,
                int index,
                int blockHeight,
                BsqTxController.BsqInputBalance bsqInputBalance,
                BsqTxController.MutableState mutableState) {

        final long bsqInputBalanceValue = bsqInputBalance.getValue();
        final long txOutputValue = txOutput.getValue();
        // We do not check for pubKeyScript.scriptType.NULL_DATA because that is only set if dumpBlockchainData is true
        if (txOutput.getOpReturnData() == null) {
            if (bsqInputBalance.isPositive() && bsqInputBalanceValue >= txOutputValue) {
                handleBsqOutput(txOutput, index, bsqInputBalance, mutableState, txOutputValue);
            } else {
                handleBtcOutput(txOutput, index, bsqInputBalance, mutableState);
            }
        } else {
            // We got a OP_RETURN output.
            opReturnController.process(txOutput, tx, index, bsqInputBalanceValue, blockHeight, mutableState);
        }
    }

    private void handleBsqOutput(TxOutput txOutput, int index, BsqTxController.BsqInputBalance bsqInputBalance,
                                 BsqTxController.MutableState mutableState, long txOutputValue) {
        // We have enough BSQ in the inputs to fund that output.

        // Update the input balance.
        bsqInputBalance.subtract(txOutputValue);

        // At a blind vote tx we get the stake at output 0.
        if (index == 0 && mutableState.getOpReturnTypeCandidate() == OpReturnTypes.BLIND_VOTE) {
            applyStateChangeForBsqOutput(txOutput, TxOutputType.VOTE_STAKE_OUTPUT);

            // First output might be vote stake output.
            mutableState.setBlindVoteStakeOutput(txOutput);
        } else {
            applyStateChangeForBsqOutput(txOutput, TxOutputType.BSQ_OUTPUT);
        }

        mutableState.increaseNumBsqOutputs();
    }

    private void handleBtcOutput(TxOutput txOutput, int index, BsqTxController.BsqInputBalance bsqInputBalance,
                                 BsqTxController.MutableState mutableState) {
        // We have a BTC output

        // We have BSQ left for burning
        if (bsqInputBalance.isPositive()) {
            // At the second output we might have a compensation request output if the opReturn type matches.
            if (index == 1 && mutableState.getOpReturnTypeCandidate() == OpReturnTypes.COMPENSATION_REQUEST) {
                // We don't set the txOutputType yet as we have not fully validated the tx but keep the candidate
                // in the model.
                mutableState.setCompRequestIssuanceOutputCandidate(txOutput);
            }
        }

        applyStateChangeForBtcOutput(txOutput);
    }

    protected void applyStateChangeForBsqOutput(TxOutput txOutput, TxOutputType txOutputType) {
        txOutput.setVerified(true);
        txOutput.setUnspent(true);
        txOutput.setTxOutputType(txOutputType);
        writableBsqBlockChain.addUnspentTxOutput(txOutput);
    }

    protected void applyStateChangeForBtcOutput(TxOutput txOutput) {
        txOutput.setTxOutputType(TxOutputType.BTC_OUTPUT);
    }
}
