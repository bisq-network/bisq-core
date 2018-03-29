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

import javax.inject.Inject;

import com.google.common.annotations.VisibleForTesting;

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
            txOutputsController.verifyOpReturnCandidate(tx, model);
            txOutputsController.iterateOutputs(tx, blockHeight, model);
            tx.setTxType(getTxType(tx, model));
            writableBsqBlockChain.addTxToMap(tx);
        }

        return bsqInputBalancePositive;
    }


    // TODO add tests
    @VisibleForTesting
    TxType getTxType(Tx tx, Model model) {
        TxType txType;
        // We need to have at least one BSQ output
        if (model.isAnyBsqOutputFound()) {
            // We want to be sure that the initial assumption of the opReturn type was matching the result after full
            // validation.
            if (model.getOpReturnTypeCandidate() == model.getVerifiedOpReturnType()) {
                final OpReturnType verifiedOpReturnType = model.getVerifiedOpReturnType();
                if (model.isInputValuePositive()) {
                    // We have some BSQ burnt

                    log.debug("BSQ have been left which was not spent. Burned BSQ amount={}, tx={}",
                            model.getAvailableInputValue(), tx.toString());
                    tx.setBurntFee(model.getAvailableInputValue());

                    if (verifiedOpReturnType != null) {
                        switch (verifiedOpReturnType) {
                            case COMPENSATION_REQUEST:
                                checkArgument(tx.getOutputs().size() >= 3, "Compensation request tx need to have at least 3 outputs");
                                final TxOutput issuanceTxOutput = tx.getOutputs().get(1);
                                checkArgument(issuanceTxOutput.getTxOutputType() == TxOutputType.ISSUANCE_CANDIDATE_OUTPUT,
                                        "Compensation request txOutput type need to be COMPENSATION_REQUEST_ISSUANCE_CANDIDATE_OUTPUT");
                                // second output is issuance candidate
                                if (issuanceTxOutput.isVerified()) {
                                    // TODO can that even happen as the voting will be applied later then the parsing of the tx
                                    // If he have the issuance candidate already accepted by voting it gets the verified flag set
                                    txType = TxType.ISSUANCE;
                                } else {
                                    // Otherwise we have an open or rejected compensation request
                                    txType = TxType.COMPENSATION_REQUEST;
                                }
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
                                txType = TxType.INVALID;
                                break;
                            case UNLOCK:
                                // TODO
                                txType = TxType.INVALID;
                                break;
                            default:
                                log.warn("We got a BSQ tx with fee and unknown OP_RETURN. tx={}", tx);
                                txType = TxType.INVALID;
                        }
                    } else {
                        // Burned fee but no opReturn
                        txType = TxType.PAY_TRADE_FEE;
                    }

                } else {
                    if (verifiedOpReturnType == null) {
                        // No burned fee and no opReturn.
                        txType = TxType.TRANSFER_BSQ;
                    } else {
                        //TODO REVEAL VOTE might move here if we remove the fee there
                        log.warn("We got a BSQ tx without fee and unknown OP_RETURN. tx={}", tx);
                        txType = TxType.INVALID;
                    }
                }
            } else {
                // TODO check if that can happen legally or if it would make the tx inv
                log.warn("We got a different opReturn type after validation as we expected initially. tx={}", tx);
                txType = TxType.INVALID;
            }
        } else {
            log.warn("We got a tx without any valid BSQ output but with burned BSQ. tx={}", tx);
            txType = TxType.INVALID;
        }

        return txType;
    }

}
