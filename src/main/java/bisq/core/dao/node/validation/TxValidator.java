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
import bisq.core.dao.state.blockchain.MutableTx;
import bisq.core.dao.state.blockchain.OpReturnType;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.blockchain.TxInput;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.blockchain.TxOutputType;
import bisq.core.dao.state.blockchain.TxType;

import bisq.common.app.DevEnv;

import javax.inject.Inject;

import com.google.common.annotations.VisibleForTesting;

import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Verifies if a given transaction is a BSQ transaction.
 */
@Slf4j
public class TxValidator {

    private final StateService stateService;
    private final TxInputProcessor txInputProcessor;
    private final TxOutputProcessor txOutputProcessor;

    @Inject
    public TxValidator(StateService stateService,
                       TxInputProcessor txInputProcessor,
                       TxOutputProcessor txOutputProcessor) {
        this.stateService = stateService;
        this.txInputProcessor = txInputProcessor;
        this.txOutputProcessor = txOutputProcessor;
    }

    // Apply state changes to tx, inputs and outputs
    // return true if any input contained BSQ
    // Any tx with BSQ input is a BSQ tx (except genesis tx but that is not handled in
    // that class).
    // There might be txs without any valid BSQ txOutput but we still keep track of it,
    // for instance to calculate the total burned BSQ.
    public boolean validate(int blockHeight, Tx tx) {
        MutableTx mutableTx = new MutableTx(tx);
        ParsingModel parsingModel = new ParsingModel();

        // We could pass mutableTx also to the sub validators but as long we have not refactored the validators to pure
        // functions lets use the parsingModel.
        parsingModel.setMutableTx(mutableTx);

        for (int inputIndex = 0; inputIndex < tx.getInputs().size(); inputIndex++) {
            TxInput input = tx.getInputs().get(inputIndex);
            txInputProcessor.process(input, blockHeight, tx.getId(), inputIndex, parsingModel, stateService);
        }
        //TODO rename  to leftOverBsq
        final boolean bsqInputBalancePositive = parsingModel.isInputValuePositive();
        if (bsqInputBalancePositive) {
            stateService.addMutableTx(mutableTx);

            final List<TxOutput> outputs = tx.getOutputs();
            // We start with last output as that might be an OP_RETURN output and gives us the specific tx type, so it is
            // easier and cleaner at parsing the other outputs to detect which kind of tx we deal with.
            // Setting the opReturn type here does not mean it will be a valid BSQ tx as the checks are only partial and
            // BSQ inputs are not verified yet.
            // We keep the temporary opReturn type in the parsingModel object.
            checkArgument(!outputs.isEmpty(), "outputs must not be empty");
            int lastIndex = outputs.size() - 1;
            txOutputProcessor.processOpReturnCandidate(outputs.get(lastIndex), parsingModel);

            // txOutputsIterator.iterate(tx, blockHeight, parsingModel);

            // We use order of output index. An output is a BSQ utxo as long there is enough input value
            // We iterate all outputs including the opReturn to do a full validation including the BSQ fee
            for (int index = 0; index < outputs.size(); index++) {
                txOutputProcessor.processTxOutput(tx, outputs.get(index), index, blockHeight, parsingModel);
            }

            // We don't allow multiple opReturn outputs (they are non-standard but to be safe lets check it)
            long numOpReturnOutputs = tx.getOutputs().stream().filter(txOutputProcessor::isOpReturnOutput).count();
            if (numOpReturnOutputs <= 1) {
                // If we had an issuanceCandidate and the type was not applied in the opReturnController due failed validation
                // we set it to an BTC_OUTPUT.
                final TxOutput issuanceCandidate = parsingModel.getIssuanceCandidate();
                if (issuanceCandidate != null &&
                        stateService.getTxOutputType(issuanceCandidate) == TxOutputType.UNDEFINED) {
                    stateService.setTxOutputType(issuanceCandidate, TxOutputType.BTC_OUTPUT);
                }

                boolean isAnyTxOutputTypeUndefined = tx.getOutputs().stream()
                        .anyMatch(txOutput -> TxOutputType.UNDEFINED == stateService.getTxOutputType(txOutput));
                if (!isAnyTxOutputTypeUndefined) {
                    final TxType txType = getTxType(tx, parsingModel);
                    mutableTx.setTxType(txType);
                    final long burntFee = parsingModel.getAvailableInputValue();
                    if (burntFee > 0)
                        mutableTx.setBurntFee(burntFee);
                } else {
                    String msg = "We have undefined txOutput types which must not happen. tx=" + tx;
                    DevEnv.logErrorAndThrowIfDevMode(msg);
                }
            } else {
                // We don't consider a tx with multiple OpReturn outputs valid.
                mutableTx.setTxType(TxType.INVALID);
                String msg = "Invalid tx. We have multiple opReturn outputs. tx=" + tx;
                log.warn(msg);
            }
        }

        // TODO || parsingModel.getBurntBondValue() > 0; shoud not be necessy
        return bsqInputBalancePositive || parsingModel.getBurntBondValue() > 0;
    }

    // TODO add tests
    @SuppressWarnings("WeakerAccess")
    @VisibleForTesting
    TxType getTxType(Tx tx, ParsingModel parsingModel) {
        TxType txType;
        // We need to have at least one BSQ output

        Optional<OpReturnType> optionalOpReturnType = getOptionalOpReturnType(tx, parsingModel);
        if (optionalOpReturnType.isPresent()) {
            txType = getTxTypeForOpReturn(tx, optionalOpReturnType.get());
        } else if (parsingModel.getOpReturnTypeCandidate() == null) {
            final boolean bsqFeesBurnt = parsingModel.isInputValuePositive();
            if (bsqFeesBurnt) {
                // Burned fee but no opReturn
                txType = TxType.PAY_TRADE_FEE;
            } else {
                // No burned fee and no opReturn.
                txType = TxType.TRANSFER_BSQ;
            }
        } else {
            // We got some OP_RETURN type candidate but it failed at validation
            txType = TxType.INVALID;
        }
        return txType;
    }

    private TxType getTxTypeForOpReturn(Tx tx, OpReturnType opReturnType) {
        TxType txType;
        switch (opReturnType) {
            case COMPENSATION_REQUEST:
                checkArgument(tx.getOutputs().size() >= 3, "Compensation request tx need to have at least 3 outputs");
                final TxOutput issuanceTxOutput = tx.getOutputs().get(1);
                checkArgument(stateService.getTxOutputType(issuanceTxOutput) == TxOutputType.ISSUANCE_CANDIDATE_OUTPUT,
                        "Compensation request txOutput type need to be ISSUANCE_CANDIDATE_OUTPUT");
                txType = TxType.COMPENSATION_REQUEST;
                break;
            case PROPOSAL:
                txType = TxType.PROPOSAL;
                break;
            case BLIND_VOTE:
                txType = TxType.BLIND_VOTE;
                break;
            case VOTE_REVEAL:
                txType = TxType.VOTE_REVEAL;
                break;
            case LOCKUP:
                txType = TxType.LOCKUP;
                break;
            case UNLOCK:
                txType = TxType.UNLOCK;
                break;
            default:
                log.warn("We got a BSQ tx with fee and unknown OP_RETURN. tx={}", tx);
                txType = TxType.INVALID;
        }
        return txType;
    }

    private Optional<OpReturnType> getOptionalOpReturnType(Tx tx, ParsingModel parsingModel) {
        if (parsingModel.isBsqOutputFound()) {
            // We want to be sure that the initial assumption of the opReturn type was matching the result after full
            // validation.
            final OpReturnType opReturnTypeCandidate = parsingModel.getOpReturnTypeCandidate();
            final OpReturnType verifiedOpReturnType = parsingModel.getVerifiedOpReturnType();
            if (opReturnTypeCandidate == verifiedOpReturnType) {
                return Optional.ofNullable(verifiedOpReturnType);
            } else {
                final String msg = "We got a different opReturn type after validation as we expected initially. " +
                        "opReturnTypeCandidate=" + opReturnTypeCandidate +
                        ", verifiedOpReturnType=" + parsingModel.getVerifiedOpReturnType() +
                        ", txId=" + tx.getId();
                log.error(msg);
            }
        } else {
            final String msg = "We got a tx without any valid BSQ output but with burned BSQ. " +
                    "Burned fee=" + parsingModel.getAvailableInputValue() / 100D + " BSQ. tx=" + tx;
            log.warn(msg);
        }
        return Optional.empty();
    }
}
