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

import bisq.core.dao.blockchain.vo.TxInput;
import bisq.core.dao.blockchain.vo.TxOutputType;
import bisq.core.dao.state.ChainStateService;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

/**
 * Processes TxInput and add input value to available balance if the input is a valid BSQ input.
 */
@Slf4j
public class TxInputController {

    private final ChainStateService chainStateService;

    @Inject
    public TxInputController(ChainStateService chainStateService) {
        this.chainStateService = chainStateService;
    }

    void processInput(TxInput txInput, int blockHeight, String txId, int inputIndex, Model model,
                      ChainStateService chainStateService) {
        this.chainStateService.getUnspentAndMatureTxOutput(txInput.getTxIdIndexTuple()).ifPresent(connectedTxOutput -> {
            model.addToInputValue(connectedTxOutput.getValue());

            // If we are spending an output from a blind vote tx marked as VOTE_STAKE_OUTPUT we save it in our model
            // for later verification at the outputs of a reveal tx.
            if (chainStateService.getTxOutputType(connectedTxOutput) == TxOutputType.BLIND_VOTE_LOCK_STAKE_OUTPUT) {
                if (!model.isVoteStakeSpentAtInputs()) {
                    model.setVoteStakeSpentAtInputs(true);
                } else {
                    log.warn("We have a tx which has 2 connected txOutputs marked as VOTE_STAKE_OUTPUT. " +
                            "This is not a valid BSQ tx.");
                }
            }

            chainStateService.setSpentInfo(connectedTxOutput, blockHeight, txId, inputIndex);
            chainStateService.removeUnspentTxOutput(connectedTxOutput);
        });
    }
}
