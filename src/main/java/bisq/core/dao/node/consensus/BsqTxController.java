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
import bisq.core.dao.blockchain.vo.TxType;
import bisq.core.dao.consensus.OpReturnType;

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
public class BsqTxController {

    private final WritableBsqBlockChain writableBsqBlockChain;
    private final TxInputsController txInputsController;
    private final TxOutputsController txOutputsController;

    @Inject
    public BsqTxController(WritableBsqBlockChain writableBsqBlockChain,
                           TxInputsController txInputsController,
                           TxOutputsController txOutputsController) {
        this.writableBsqBlockChain = writableBsqBlockChain;
        this.txInputsController = txInputsController;
        this.txOutputsController = txOutputsController;
    }

    // Apply state changes to tx, inputs and outputs
    // return true if any input contained BSQ
    // Any tx with BSQ input is a BSQ tx (except genesis tx but that is not handled in
    // that class).
    // There might be txs without any valid BSQ txOutput but we still keep track of it,
    // for instance to calculate the total burned BSQ.
    public boolean isBsqTx(int blockHeight, Tx tx) {
        Model model = new Model();
        txInputsController.iterateInputs(tx, blockHeight, model);
        final boolean bsqInputBalancePositive = model.isInputValuePositive();
        if (bsqInputBalancePositive) {
            txOutputsController.processOpReturnCandidate(tx, model);
            txOutputsController.iterateOutputs(tx, blockHeight, model);
            if (!txOutputsController.isAnyTxOutputTypeUndefined(tx)) {
                tx.setTxType(getTxType(tx, model));
                tx.setBurntFee(model.getAvailableInputValue());
                writableBsqBlockChain.addTxToMap(tx);
            } else {
                String msg = "We have undefined txOutput types which must not happen. tx=" + tx;
                DevEnv.logErrorAndThrowIfDevMode(msg);
            }
        }

        return bsqInputBalancePositive;
    }

    // TODO add tests
    @SuppressWarnings("WeakerAccess")
    @VisibleForTesting
    TxType getTxType(Tx tx, Model model) {
        TxType txType;
        // We need to have at least one BSQ output

        Optional<OpReturnType> optionalOpReturnType = getOptionalOpReturnType(tx, model);
        final boolean bsqFeesBurnt = model.isInputValuePositive();
        //noinspection OptionalIsPresent
        if (optionalOpReturnType.isPresent()) {
            txType = getTxTypeForOpReturn(tx, optionalOpReturnType.get());
        } else if (model.getOpReturnTypeCandidate() == null) {
            if (bsqFeesBurnt) {
                // Burned fee but no opReturn
                txType = TxType.PAY_TRADE_FEE;
            } else {
                // No burned fee and no opReturn.
                txType = TxType.TRANSFER_BSQ;
            }
        } else {
            // We got some OP_RETURN type but it failed at validation
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
                checkArgument(issuanceTxOutput.getTxOutputType() == TxOutputType.ISSUANCE_CANDIDATE_OUTPUT,
                        "Compensation request txOutput type need to be COMPENSATION_REQUEST_ISSUANCE_CANDIDATE_OUTPUT");
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

    private Optional<OpReturnType> getOptionalOpReturnType(Tx tx, Model model) {
        if (model.isBsqOutputFound()) {
            // We want to be sure that the initial assumption of the opReturn type was matching the result after full
            // validation.
            if (model.getOpReturnTypeCandidate() == model.getVerifiedOpReturnType()) {
                final OpReturnType verifiedOpReturnType = model.getVerifiedOpReturnType();
                return verifiedOpReturnType != null ? Optional.of(verifiedOpReturnType) : Optional.empty();
            } else {
                final String msg = "We got a different opReturn type after validation as we expected initially. " +
                        "opReturnTypeCandidate=" + model.getOpReturnTypeCandidate() +
                        " / verifiedOpReturnType=" + model.getVerifiedOpReturnType();
                log.error(msg);
            }
        } else {
            final String msg = "We got a tx without any valid BSQ output but with burned BSQ. tx=" + tx;
            log.warn(msg);
            if (DevEnv.isDevMode())
                throw new RuntimeException(msg);
        }
        return Optional.empty();
    }
}
