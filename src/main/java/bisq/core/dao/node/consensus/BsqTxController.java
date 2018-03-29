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
import bisq.core.dao.blockchain.vo.TxType;

import javax.inject.Inject;

import com.google.common.annotations.VisibleForTesting;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

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
    // Any tx with BSQ input is a BSQ tx (except genesis tx but that not handled in that class).
    public boolean isBsqTx(int blockHeight, Tx tx) {
        MutableState mutableState = new MutableState();
        BsqInputBalance bsqInputBalance = txInputsController.getBsqInputBalance(tx, blockHeight, mutableState);
        final boolean bsqInputBalancePositive = bsqInputBalance.isPositive();
        if (bsqInputBalancePositive) {
            txOutputsController.iterate(tx, blockHeight, bsqInputBalance, mutableState);
            tx.setTxType(getTxType(tx, bsqInputBalance, mutableState));
            writableBsqBlockChain.addTxToMap(tx);
        }

        return bsqInputBalancePositive;
    }


    // TODO add tests
    @VisibleForTesting
    TxType getTxType(Tx tx, BsqTxController.BsqInputBalance bsqInputBalance, MutableState mutableState) {
        TxType txType;
        // We need to have at least one BSQ output
        if (mutableState.getNumBsqOutputs() > 0) {
            // We want to be sure that the initial assumption of the opReturn type was matching the result after full
            // validation.
            if (mutableState.getOpReturnTypeCandidate() == mutableState.getVerifiedOpReturnType()) {
                if (bsqInputBalance.isPositive()) {
                    // We have some BSQ burnt

                    log.debug("BSQ have been left which was not spent. Burned BSQ amount={}, tx={}",
                            bsqInputBalance.getValue(), tx.toString());
                    tx.setBurntFee(bsqInputBalance.getValue());

                    //TODO add  LOCK_UP, UN_LOCK
                    final TxOutput txOutput = tx.getOutputs().get(1);
                    if (mutableState.getVerifiedOpReturnType() == OpReturnTypes.COMPENSATION_REQUEST) {
                        checkArgument(tx.getOutputs().size() >= 3, "Compensation request tx need to have at least 3 outputs");
                        checkArgument(txOutput.getTxOutputType() == TxOutputType.COMPENSATION_REQUEST_ISSUANCE_CANDIDATE_OUTPUT,
                                "Compensation request txOutput type need to be COMPENSATION_REQUEST_ISSUANCE_CANDIDATE_OUTPUT");
                        // second output is issuance candidate
                        if (txOutput.isVerified()) {
                            // If he have the issuance candidate already accepted by voting it gets the verified flag set
                            txType = TxType.ISSUANCE;
                        } else {
                            // Otherwise we have an open or rejected compensation request
                            txType = TxType.COMPENSATION_REQUEST;
                        }
                    } else if (mutableState.getVerifiedOpReturnType() == OpReturnTypes.PROPOSAL) {
                        txType = TxType.PROPOSAL;
                    } else if (mutableState.getVerifiedOpReturnType() == OpReturnTypes.BLIND_VOTE) {
                        txType = TxType.VOTE;
                    } else if (mutableState.getVerifiedOpReturnType() == OpReturnTypes.VOTE_REVEAL) {
                        txType = TxType.VOTE_REVEAL;
                    } else if (mutableState.getOpReturnTypeCandidate() == 0x00) {
                        // verifiedOpReturnType is not set in that case, so we use opReturnTypeCandidate.
                        txType = TxType.PAY_TRADE_FEE;
                    } else {
                        log.warn("We got a BSQ tx with fee and unknown OP_RETURN. tx={}", tx);
                        txType = TxType.INVALID;
                    }
                } else {
                    if (mutableState.getOpReturnTypeCandidate() == 0x00) {
                        // Tx has no burned fee and no opReturn.
                        // If opReturnTypeCandidate is 0 then verifiedOpReturnType as to be well.
                        txType = TxType.TRANSFER_BSQ;
                    } else {
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

    @Getter
    @Setter
    static class BsqInputBalance {
        // Remaining BSQ from inputs
        private long value = 0;

        BsqInputBalance() {
        }

        BsqInputBalance(long value) {
            this.value = value;
        }

        public void add(long value) {
            this.value += value;
        }

        public void subtract(long value) {
            this.value -= value;
        }

        public boolean isPositive() {
            return value > 0;
        }

        public boolean isZero() {
            return value == 0;
        }
    }

    @Getter
    @Setter
    static class MutableState {
        @Nullable
        private TxOutput compRequestIssuanceOutputCandidate;
        @Nullable
        private TxOutput blindVoteStakeOutput;
        private int numBsqOutputs;
        private boolean voteStakeSpentAtInputs;

        // That will be set preliminary at first parsing the last output. Not guaranteed
        // that it is a valid BSQ tx at that moment.
        private byte opReturnTypeCandidate = 0x00;

        // At end of parsing when we do the full validation we set the type here
        private byte verifiedOpReturnType = 0x00;

        MutableState() {
        }

        public void increaseNumBsqOutputs() {
            numBsqOutputs++;
        }
    }
}
