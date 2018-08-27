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

import bisq.core.dao.bonding.BondingConsensus;
import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.state.blockchain.OpReturnType;
import bisq.core.dao.state.blockchain.TempTx;
import bisq.core.dao.state.blockchain.TempTxOutput;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.blockchain.TxOutputType;

import com.google.common.annotations.VisibleForTesting;

import java.util.Optional;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Checks if an output is a BSQ output and apply state change.
 */
@Slf4j
public class TxOutputParser {
    private final BsqStateService bsqStateService;

    @Getter
    @Setter
    private long availableInputValue = 0;
    @Getter
    private boolean bsqOutputFound;
    @Setter
    private int unlockBlockHeight;

    @Getter
    private Optional<OpReturnType> optionalOpReturnTypeCandidate = Optional.empty();
    @Getter
    private Optional<OpReturnType> optionalVerifiedOpReturnType = Optional.empty();
    @Getter
    private Optional<TempTxOutput> optionalIssuanceCandidate = Optional.empty();
    @Getter
    private Optional<TempTxOutput> optionalBlindVoteLockStakeOutput = Optional.empty();
    @Getter
    private Optional<TempTxOutput> optionalVoteRevealUnlockStakeOutput = Optional.empty();
    @Getter
    private Optional<TempTxOutput> optionalLockupOutput = Optional.empty();

    TxOutputParser(BsqStateService bsqStateService) {
        this.bsqStateService = bsqStateService;
    }

    public void processGenesisTxOutput(TempTx genesisTx) {
        for (int i = 0; i < genesisTx.getTempTxOutputs().size(); ++i) {
            TempTxOutput tempTxOutput = genesisTx.getTempTxOutputs().get(i);
            bsqStateService.addUnspentTxOutput(TxOutput.fromTempOutput(tempTxOutput));
        }
    }

    void processOpReturnCandidate(TempTxOutput txOutput) {
        optionalOpReturnTypeCandidate = OpReturnParser.getOptionalOpReturnTypeCandidate(txOutput);
    }

    /**
     * Process a transaction output.
     *
     * @param isLastOutput  If it is the last output
     * @param tempTxOutput  The TempTxOutput we are parsing
     * @param index         The index in the outputs
     * @param parsingModel  The ParsingModel
     */
    void processTxOutput(boolean isLastOutput, TempTxOutput tempTxOutput, int index, ParsingModel parsingModel) {
        // We do not check for pubKeyScript.scriptType.NULL_DATA because that is only set if dumpBlockchainData is true
        final byte[] opReturnData = tempTxOutput.getOpReturnData();
        if (opReturnData == null) {
            final long txOutputValue = tempTxOutput.getValue();
            if (isUnlockBondTx(tempTxOutput.getValue(), index, parsingModel)) {
                // We need to handle UNLOCK transactions separately as they don't follow the pattern on spending BSQ
                // The LOCKUP BSQ is burnt unless the output exactly matches the input, that would cause the
                // output to not be BSQ output at all
                handleUnlockBondTx(tempTxOutput, parsingModel);
            } else if (availableInputValue > 0 && availableInputValue >= txOutputValue) {
                handleBsqOutput(tempTxOutput, index, parsingModel, txOutputValue);
            } else {
                handleBtcOutput(tempTxOutput, index, parsingModel);
            }
        } else {
            handleOpReturnOutput(tempTxOutput, isLastOutput, parsingModel);
        }
    }

    boolean isOpReturnOutput(TempTxOutput txOutput) {
        return txOutput.getOpReturnData() != null;
    }

    /**
     * Whether a transaction is a valid unlock bond transaction or not.
     *
     * @param txOutputValue The value of the current output, in satoshi.
     * @param index         The index of the output.
     * @param parsingModel  The parsing model.
     * @return True if the transaction is an unlock transaction, false otherwise.
     */
    private boolean isUnlockBondTx(long txOutputValue, int index, ParsingModel parsingModel) {
        // We require that the input value is exact the available value and the output value
        return parsingModel.getSpentLockupTxOutput() != null &&
                index == 0 &&
                parsingModel.getSpentLockupTxOutput().getValue() == txOutputValue &&
                availableInputValue == txOutputValue;
    }

    private void handleUnlockBondTx(TempTxOutput txOutput, ParsingModel parsingModel) {
        TxOutput spentLockupTxOutput = parsingModel.getSpentLockupTxOutput();
        checkNotNull(spentLockupTxOutput, "spentLockupTxOutput must not be null");

        availableInputValue -= spentLockupTxOutput.getValue();

        txOutput.setTxOutputType(TxOutputType.UNLOCK);
        bsqStateService.addUnspentTxOutput(TxOutput.fromTempOutput(txOutput));

        parsingModel.getTx().setUnlockBlockHeight(unlockBlockHeight);
        bsqOutputFound = true;
    }

    private void handleBsqOutput(TempTxOutput txOutput, int index, ParsingModel parsingModel, long txOutputValue) {
        // Update the input balance.
        availableInputValue -= txOutputValue;

        boolean isFirstOutput = index == 0;

        OpReturnType opReturnTypeCandidate = null;
        if (optionalOpReturnTypeCandidate.isPresent())
            opReturnTypeCandidate = optionalOpReturnTypeCandidate.get();

        TxOutputType bsqOutput;
        if (isFirstOutput && opReturnTypeCandidate == OpReturnType.BLIND_VOTE) {
            bsqOutput = TxOutputType.BLIND_VOTE_LOCK_STAKE_OUTPUT;
            optionalBlindVoteLockStakeOutput = Optional.of(txOutput);
        } else if (isFirstOutput && opReturnTypeCandidate == OpReturnType.VOTE_REVEAL) {
            bsqOutput = TxOutputType.VOTE_REVEAL_UNLOCK_STAKE_OUTPUT;
            optionalVoteRevealUnlockStakeOutput = Optional.of(txOutput);
        } else if (isFirstOutput && opReturnTypeCandidate == OpReturnType.LOCKUP) {
            bsqOutput = TxOutputType.LOCKUP;
            optionalLockupOutput = Optional.of(txOutput);
        } else {
            bsqOutput = TxOutputType.BSQ_OUTPUT;
        }
        txOutput.setTxOutputType(bsqOutput);
        bsqStateService.addUnspentTxOutput(TxOutput.fromTempOutput(txOutput));

        bsqOutputFound = true;
    }

    private void handleBtcOutput(TempTxOutput txOutput, int index, ParsingModel parsingModel) {
        // If we have BSQ left for burning and at the second output a compensation request output we set the
        // candidate to the parsingModel and we don't apply the TxOutputType as we do that later as the OpReturn check.
        if (availableInputValue > 0 &&
                index == 1 &&
                optionalOpReturnTypeCandidate.isPresent() &&
                optionalOpReturnTypeCandidate.get() == OpReturnType.COMPENSATION_REQUEST) {
            // We don't set the txOutputType yet as we have not fully validated the tx but put the candidate
            // into our local optionalIssuanceCandidate.

            optionalIssuanceCandidate = Optional.of(txOutput);
        } else {
            txOutput.setTxOutputType(TxOutputType.BTC_OUTPUT);
        }
    }

    private void handleOpReturnOutput(TempTxOutput tempTxOutput, boolean isLastOutput, ParsingModel parsingModel) {
        TxOutputType txOutputType = OpReturnParser.getTxOutputType(tempTxOutput, isLastOutput);
        tempTxOutput.setTxOutputType(txOutputType);

        optionalVerifiedOpReturnType = getMappedOpReturnType(txOutputType);
        optionalVerifiedOpReturnType.filter(verifiedOpReturnType -> verifiedOpReturnType == OpReturnType.LOCKUP)
                .ifPresent(verifiedOpReturnType -> {
                    byte[] opReturnData = tempTxOutput.getOpReturnData();
                    checkNotNull(opReturnData, "opReturnData must not be null");
                    int lockTime = BondingConsensus.getLockTime(opReturnData);
                    tempTxOutput.setLockTime(lockTime);
                });
    }

    @SuppressWarnings("WeakerAccess")
    @VisibleForTesting
    static Optional<OpReturnType> getMappedOpReturnType(TxOutputType outputType) {
        switch (outputType) {
            case PROPOSAL_OP_RETURN_OUTPUT:
                return Optional.of(OpReturnType.PROPOSAL);
            case COMP_REQ_OP_RETURN_OUTPUT:
                return Optional.of(OpReturnType.COMPENSATION_REQUEST);
            case BLIND_VOTE_OP_RETURN_OUTPUT:
                return Optional.of(OpReturnType.BLIND_VOTE);
            case VOTE_REVEAL_OP_RETURN_OUTPUT:
                return Optional.of(OpReturnType.VOTE_REVEAL);
            case LOCKUP_OP_RETURN_OUTPUT:
                return Optional.of(OpReturnType.LOCKUP);
            default:
                return Optional.empty();
        }
    }
}
