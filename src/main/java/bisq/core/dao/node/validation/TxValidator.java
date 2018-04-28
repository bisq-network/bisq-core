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
import bisq.core.dao.state.blockchain.TxType;

import bisq.common.app.DevEnv;

import javax.inject.Inject;

import com.google.common.annotations.VisibleForTesting;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Verifies if a given transaction is a BSQ transaction.
 */
@Slf4j
public class TxValidator {

    private final StateService stateService;
    private final TxInputsIterator txInputsIterator;
    private final TxOutputsIterator txOutputsIterator;

    @Inject
    public TxValidator(StateService stateService,
                       TxInputsIterator txInputsIterator,
                       TxOutputsIterator txOutputsIterator) {
        this.stateService = stateService;
        this.txInputsIterator = txInputsIterator;
        this.txOutputsIterator = txOutputsIterator;
    }

    // Apply state changes to tx, inputs and outputs
    // return true if any input contained BSQ
    // Any tx with BSQ input is a BSQ tx (except genesis tx but that is not handled in
    // that class).
    // There might be txs without any valid BSQ txOutput but we still keep track of it,
    // for instance to calculate the total burned BSQ.
    public boolean validate(int blockHeight, Tx tx) {
        final String txId = tx.getId();
        TxState txState = txInputsIterator.iterate(tx, blockHeight);
        final boolean bsqInputBalancePositive = txState.isInputValuePositive();
        if (bsqInputBalancePositive) {
            txOutputsIterator.processOpReturnCandidate(tx, txState);
            txOutputsIterator.iterate(tx, blockHeight, txState);

            // Multiple op return outputs are non-standard but to be safe lets check it.
            if (txOutputsIterator.getNumOpReturnOutputs(tx) <= 1) {
                // If we had an issuanceCandidate and the type was not applied in the opReturnController due failed validation
                // we set it to an BTC_OUTPUT.
                final TxOutput issuanceCandidate = txState.getIssuanceCandidate();
                if (issuanceCandidate != null &&
                        stateService.getTxOutputType(issuanceCandidate) == TxOutputType.UNDEFINED) {
                    stateService.setTxOutputType(issuanceCandidate, TxOutputType.BTC_OUTPUT);
                }

                if (!txOutputsIterator.isAnyTxOutputTypeUndefined(tx)) {
                    final TxType txType = getTxType(tx, txState);
                    stateService.setTxType(txId, txType);
                    final long burnedFee = txState.getAvailableInputValue();
                    if (burnedFee > 0)
                        stateService.setBurntFee(txId, burnedFee);
                } else {
                    String msg = "We have undefined txOutput types which must not happen. tx=" + tx;
                    DevEnv.logErrorAndThrowIfDevMode(msg);
                }
            } else {
                // We don't consider a tx with multiple OpReturn outputs valid.
                stateService.setTxType(txId, TxType.INVALID);
                String msg = "Invalid tx. We have multiple opReturn outputs. tx=" + tx;
                log.warn(msg);
            }
        }

        return bsqInputBalancePositive;
    }

    // TODO add tests
    @SuppressWarnings("WeakerAccess")
    @VisibleForTesting
    TxType getTxType(Tx tx, TxState txState) {
        TxType txType;
        // We need to have at least one BSQ output

        Optional<OpReturnType> optionalOpReturnType = getOptionalOpReturnType(tx, txState);
        if (optionalOpReturnType.isPresent()) {
            txType = getTxTypeForOpReturn(tx, optionalOpReturnType.get());
        } else if (txState.getOpReturnTypeCandidate() == null) {
            final boolean bsqFeesBurnt = txState.isInputValuePositive();
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
            case LOCK_UP:
                // TODO
                txType = TxType.LOCK_UP;
                break;
            case UNLOCK:
                // TODO
                txType = TxType.UN_LOCK;
                break;
            default:
                log.warn("We got a BSQ tx with fee and unknown OP_RETURN. tx={}", tx);
                txType = TxType.INVALID;
        }
        return txType;
    }

    private Optional<OpReturnType> getOptionalOpReturnType(Tx tx, TxState txState) {
        if (txState.isBsqOutputFound()) {
            // We want to be sure that the initial assumption of the opReturn type was matching the result after full
            // validation.
            final OpReturnType opReturnTypeCandidate = txState.getOpReturnTypeCandidate();
            final OpReturnType verifiedOpReturnType = txState.getVerifiedOpReturnType();
            if (opReturnTypeCandidate == verifiedOpReturnType) {
                return Optional.ofNullable(verifiedOpReturnType);
            } else {
                final String msg = "We got a different opReturn type after validation as we expected initially. " +
                        "opReturnTypeCandidate=" + opReturnTypeCandidate +
                        " / verifiedOpReturnType=" + txState.getVerifiedOpReturnType();
                log.error(msg);
            }
        } else {
            final String msg = "We got a tx without any valid BSQ output but with burned BSQ. " +
                    "Burned fee=" + txState.getAvailableInputValue() / 100D + " BSQ. tx=" + tx;
            log.warn(msg);
        }
        return Optional.empty();
    }
}
