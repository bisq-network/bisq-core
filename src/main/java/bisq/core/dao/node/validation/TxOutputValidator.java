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
import bisq.core.dao.state.blockchain.OpReturnType;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.blockchain.TxOutputType;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * Checks if an output is a BSQ output and apply state change.
 */
@Slf4j
public class TxOutputValidator {
    private final StateService stateService;
    private final OpReturnProcessor opReturnProcessor;

    @Inject
    public TxOutputValidator(StateService stateService, OpReturnProcessor opReturnProcessor) {
        this.stateService = stateService;
        this.opReturnProcessor = opReturnProcessor;
    }

    void processOpReturnCandidate(TxOutput txOutput, ParsingModel parsingModel) {
        opReturnProcessor.processOpReturnCandidate(txOutput, parsingModel);
    }

    void processTxOutput(Tx tx, TxOutput txOutput, int index, int blockHeight, ParsingModel parsingModel) {
        final long bsqInputBalanceValue = parsingModel.getAvailableInputValue();
        // We do not check for pubKeyScript.scriptType.NULL_DATA because that is only set if dumpBlockchainData is true
        final byte[] opReturnData = txOutput.getOpReturnData();
        if (opReturnData == null) {
            if (handleUnlockBondTx(txOutput, index, parsingModel))
                return;

            final long txOutputValue = txOutput.getValue();
            if (bsqInputBalanceValue > 0 && bsqInputBalanceValue >= txOutputValue) {
                //TODO can handleUnlockBondTx be here?
                handleBsqOutput(txOutput, index, parsingModel, txOutputValue);
            } else {
                handleBtcOutput(txOutput, index, parsingModel);
            }
        } else {
            // We got a OP_RETURN output.
            opReturnProcessor.validate(opReturnData, txOutput, tx, index, bsqInputBalanceValue, blockHeight, parsingModel);
        }
    }

    boolean isOpReturnOutput(TxOutput txOutput) {
        return txOutput.getOpReturnData() != null;
    }

    private boolean handleUnlockBondTx(TxOutput txOutput, int index, ParsingModel parsingModel) {
        if (parsingModel.getSpentLockedTxOutput() != null) {
            // This is a bond unlock transaction
            if (index == 0) {
                // All the BSQ from the locked bond txOutput are spent, either burnt or send to bond unlock txOutput
                parsingModel.subtractFromInputValue(parsingModel.getSpentLockedTxOutput().getValue());

                // The first txOutput value must match the spent locked connectedTxOutput value
                if (parsingModel.getSpentLockedTxOutput().getValue() == txOutput.getValue()) {
                    applyStateChangeForBsqOutput(txOutput, TxOutputType.UNLOCK);
                    parsingModel.getMutableTx().setUnlockBlockHeight(parsingModel.getUnlockBlockHeight());
                    parsingModel.setBsqOutputFound(true);
                } else {
                    applyStateChangeForBtcOutput(txOutput);
                }
            } else {
                applyStateChangeForBtcOutput(txOutput);
            }
            return true;
        }
        return false;
    }

    private void handleBsqOutput(TxOutput txOutput, int index, ParsingModel parsingModel, long txOutputValue) {
        // Update the input balance.
        parsingModel.subtractFromInputValue(txOutputValue);

        if (index == 0 && parsingModel.getOpReturnTypeCandidate() == OpReturnType.BLIND_VOTE) {
            // At a blind vote tx we get the stake at output 0.
            // First output might be vote stake output.
            parsingModel.setBlindVoteLockStakeOutput(txOutput);

            // We don't set the txOutputType yet as we have not fully validated the tx but keep the candidate
            // in the parsingModel.
            applyStateChangeForBsqOutput(txOutput, null);
        } else if (index == 0 && parsingModel.getOpReturnTypeCandidate() == OpReturnType.VOTE_REVEAL) {
            // At a vote reveal tx we get the released stake at output 0.
            // First output might be stake release output.
            parsingModel.setVoteRevealUnlockStakeOutput(txOutput);

            // We don't set the txOutputType yet as we have not fully validated the tx but keep the candidate
            // in the parsingModel.
            applyStateChangeForBsqOutput(txOutput, null);
        } else if (index == 0 && parsingModel.getOpReturnTypeCandidate() == OpReturnType.LOCKUP) {
            // First output might be lockup output.
            parsingModel.setLockupOutput(txOutput);

            // We don't set the txOutputType yet as we have not fully validated the tx but keep the candidate
            // in the parsingModel.
            applyStateChangeForBsqOutput(txOutput, null);
        } else {
            applyStateChangeForBsqOutput(txOutput, TxOutputType.BSQ_OUTPUT);
        }

        parsingModel.setBsqOutputFound(true);
    }

    private void handleBtcOutput(TxOutput txOutput, int index, ParsingModel parsingModel) {
        // If we have BSQ left for burning and at the second output a compensation request output we set the
        // candidate to the parsingModel and we don't apply the TxOutputType as we do that later as the OpReturn check.
        if (parsingModel.isInputValuePositive() &&
                index == 1 &&
                parsingModel.getOpReturnTypeCandidate() == OpReturnType.COMPENSATION_REQUEST) {
            // We don't set the txOutputType yet as we have not fully validated the tx but put the candidate
            // into our parsingModel.
            parsingModel.setIssuanceCandidate(txOutput);
        } else {
            applyStateChangeForBtcOutput(txOutput);
        }
    }

    protected void applyStateChangeForBsqOutput(TxOutput txOutput, @Nullable TxOutputType txOutputType) {
        stateService.addUnspentTxOutput(txOutput);
        if (txOutputType != null)
            stateService.setTxOutputType(txOutput, txOutputType);
    }

    protected void applyStateChangeForBtcOutput(TxOutput txOutput) {
        stateService.setTxOutputType(txOutput, TxOutputType.BTC_OUTPUT);
    }
}
