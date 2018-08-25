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

package bisq.core.dao.node.parser;

import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.state.blockchain.OpReturnType;
import bisq.core.dao.state.blockchain.TempTx;
import bisq.core.dao.state.blockchain.TempTxOutput;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.blockchain.TxOutputType;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Checks if an output is a BSQ output and apply state change.
 */
@Slf4j
public class TxOutputParser {
    private final BsqStateService bsqStateService;
    private final OpReturnParser opReturnParser;

    @Inject
    public TxOutputParser(BsqStateService bsqStateService, OpReturnParser opReturnParser) {
        this.bsqStateService = bsqStateService;
        this.opReturnParser = opReturnParser;
    }

    public void processGenesisTxOutput(TempTx genesisTx) {
        for (int i = 0; i < genesisTx.getTempTxOutputs().size(); ++i) {
            TempTxOutput tempTxOutput = genesisTx.getTempTxOutputs().get(i);
            bsqStateService.addUnspentTxOutput(TxOutput.fromTempOutput(tempTxOutput));
        }
    }

    void processOpReturnCandidate(TempTxOutput txOutput, ParsingModel parsingModel) {
        opReturnParser.processOpReturnCandidate(txOutput, parsingModel);
    }

    void processTxOutput(TempTx tx, TempTxOutput txOutput, int index, int blockHeight, ParsingModel parsingModel) {
        final long bsqInputBalanceValue = parsingModel.getAvailableInputValue();
        // We do not check for pubKeyScript.scriptType.NULL_DATA because that is only set if dumpBlockchainData is true
        final byte[] opReturnData = txOutput.getOpReturnData();
        if (opReturnData == null) {
            final long txOutputValue = txOutput.getValue();
            if (isUnlockBondTx(txOutput, index, parsingModel)) {
                // We need to handle UNLOCK transactions separately as they don't follow the pattern on spending BSQ
                // The LOCKUP BSQ is burnt unless the output exactly matches the input, that would cause the
                // output to not be BSQ output at all
                handleUnlockBondTx(txOutput, parsingModel);
            } else if (bsqInputBalanceValue > 0 && bsqInputBalanceValue >= txOutputValue) {
                handleBsqOutput(txOutput, index, parsingModel, txOutputValue);
            } else {
                handleBtcOutput(txOutput, index, parsingModel);
            }
        } else {
            // We got a OP_RETURN output.
            TxOutputType outputType = opReturnParser.validate(
                    opReturnData,
                    txOutput.getValue() != 0,
                    tx,
                    index,
                    bsqInputBalanceValue,
                    blockHeight,
                    parsingModel
            );
            txOutput.setTxOutputType(outputType);
        }
    }

    boolean isOpReturnOutput(TempTxOutput txOutput) {
        return txOutput.getOpReturnData() != null;
    }

    private boolean isUnlockBondTx(TempTxOutput txOutput, int index, ParsingModel parsingModel) {
        // We require that the input value is exact the available value and the output value
        return parsingModel.getSpentLockupTxOutput() != null &&
                index == 0 &&
                parsingModel.getSpentLockupTxOutput().getValue() == txOutput.getValue() &&
                parsingModel.getAvailableInputValue() == txOutput.getValue();
    }

    private void handleUnlockBondTx(TempTxOutput txOutput, ParsingModel parsingModel) {
        TxOutput spentLockupTxOutput = parsingModel.getSpentLockupTxOutput();
        checkNotNull(spentLockupTxOutput, "spentLockupTxOutput must not be null");
        parsingModel.subtractFromInputValue(spentLockupTxOutput.getValue());

        txOutput.setTxOutputType(TxOutputType.UNLOCK);
        bsqStateService.addUnspentTxOutput(TxOutput.fromTempOutput(txOutput));

        parsingModel.getTx().setUnlockBlockHeight(parsingModel.getUnlockBlockHeight());
        parsingModel.setBsqOutputFound(true);
    }

    private void handleBsqOutput(TempTxOutput txOutput, int index, ParsingModel parsingModel, long txOutputValue) {
        // Update the input balance.
        parsingModel.subtractFromInputValue(txOutputValue);

        boolean isFirstOutput = index == 0;
        OpReturnType candidate = parsingModel.getOpReturnTypeCandidate();

        TxOutputType bsqOutput;
        if (isFirstOutput && candidate == OpReturnType.BLIND_VOTE) {
            bsqOutput = TxOutputType.BLIND_VOTE_LOCK_STAKE_OUTPUT;
            parsingModel.setBlindVoteLockStakeOutput(txOutput);
        } else if (isFirstOutput && candidate == OpReturnType.VOTE_REVEAL) {
            bsqOutput = TxOutputType.VOTE_REVEAL_UNLOCK_STAKE_OUTPUT;
            parsingModel.setVoteRevealUnlockStakeOutput(txOutput);
        } else if (isFirstOutput && candidate == OpReturnType.LOCKUP) {
            bsqOutput = TxOutputType.LOCKUP;
            parsingModel.setLockupOutput(txOutput);
        } else {
            bsqOutput = TxOutputType.BSQ_OUTPUT;
        }
        txOutput.setTxOutputType(bsqOutput);
        bsqStateService.addUnspentTxOutput(TxOutput.fromTempOutput(txOutput));

        parsingModel.setBsqOutputFound(true);
    }

    private void handleBtcOutput(TempTxOutput txOutput, int index, ParsingModel parsingModel) {
        // If we have BSQ left for burning and at the second output a compensation request output we set the
        // candidate to the parsingModel and we don't apply the TxOutputType as we do that later as the OpReturn check.
        if (parsingModel.isInputValuePositive() &&
                index == 1 &&
                parsingModel.getOpReturnTypeCandidate() == OpReturnType.COMPENSATION_REQUEST) {
            // We don't set the txOutputType yet as we have not fully validated the tx but put the candidate
            // into our parsingModel.
            parsingModel.setIssuanceCandidate(txOutput);
        } else {
            txOutput.setTxOutputType(TxOutputType.BTC_OUTPUT);
        }
    }
}
