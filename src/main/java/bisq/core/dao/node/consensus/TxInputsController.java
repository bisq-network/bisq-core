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
import bisq.core.dao.blockchain.vo.SpentInfo;
import bisq.core.dao.blockchain.vo.Tx;
import bisq.core.dao.blockchain.vo.TxInput;
import bisq.core.dao.blockchain.vo.TxOutput;
import bisq.core.dao.blockchain.vo.TxOutputType;

import javax.inject.Inject;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

/**
 * Calculate the available BSQ balance from all inputs and apply state change.
 */
@Slf4j
public class TxInputsController {

    private final WritableBsqBlockChain writableBsqBlockChain;
    private final TxInputController txInputController;

    @Inject
    public TxInputsController(WritableBsqBlockChain writableBsqBlockChain, TxInputController txInputController) {
        this.writableBsqBlockChain = writableBsqBlockChain;
        this.txInputController = txInputController;
    }

    BsqTxController.BsqInputBalance getBsqInputBalance(Tx tx, int blockHeight, BsqTxController.MutableState mutableState) {
        BsqTxController.BsqInputBalance bsqInputBalance = new BsqTxController.BsqInputBalance();
        for (int inputIndex = 0; inputIndex < tx.getInputs().size(); inputIndex++) {
            TxInput input = tx.getInputs().get(inputIndex);
            final Optional<TxOutput> optionalSpendableTxOutput = txInputController.getOptionalSpendableTxOutput(input);
            if (optionalSpendableTxOutput.isPresent()) {
                final TxOutput spendableTxOutput = optionalSpendableTxOutput.get();
                bsqInputBalance.add(spendableTxOutput.getValue());

                // If we are spending an output marked as VOTE_STAKE_OUTPUT we save it in our model for later
                // verification if that tx is a valid reveal tx.
                if (spendableTxOutput.getTxOutputType() == TxOutputType.VOTE_STAKE_OUTPUT) {
                    if (!mutableState.isVoteStakeSpentAtInputs()) {
                        mutableState.setVoteStakeSpentAtInputs(true);
                    } else {
                        log.warn("We have a tx which has 2 connected txOutputs marked as VOTE_STAKE_OUTPUT. " +
                                "This is not a valid BSQ tx.");
                    }
                }

                applyStateChange(input, spendableTxOutput, blockHeight, tx, inputIndex);
            }
        }
        return bsqInputBalance;
    }

    private void applyStateChange(TxInput input, TxOutput spendableTxOutput, int blockHeight, Tx tx, int inputIndex) {
        input.setConnectedTxOutput(spendableTxOutput);
        spendableTxOutput.setUnspent(false);
        spendableTxOutput.setSpentInfo(new SpentInfo(blockHeight, tx.getId(), inputIndex));
        writableBsqBlockChain.removeUnspentTxOutput(spendableTxOutput);
    }
}
