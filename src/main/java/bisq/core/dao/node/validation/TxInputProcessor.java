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
import bisq.core.dao.state.blockchain.SpentInfo;
import bisq.core.dao.state.blockchain.TxInput;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.blockchain.TxOutputType;

import javax.inject.Inject;

import java.util.Set;

import lombok.extern.slf4j.Slf4j;

/**
 * Processes TxInput and add input value to available balance if the input is a valid BSQ input.
 */
@Slf4j
public class TxInputProcessor {

    private final StateService stateService;

    @Inject
    public TxInputProcessor(StateService stateService) {
        this.stateService = stateService;
    }

    void process(TxInput txInput, int blockHeight, String txId, int inputIndex, ParsingModel parsingModel,
                 StateService stateService) {
        this.stateService.getUnspentAndMatureTxOutput(txInput.getConnectedTxOutputKey())
                .ifPresent(connectedTxOutput -> {
                    parsingModel.addToInputValue(connectedTxOutput.getValue());

                    // If we are spending an output from a blind vote tx marked as VOTE_STAKE_OUTPUT we save it in our parsingModel
                    // for later verification at the outputs of a reveal tx.
                    TxOutputType connectedTxOutputType = stateService.getTxOutputType(connectedTxOutput);
                    Set<TxOutput> spentUnlockedConnectedTxOutputs = parsingModel.getSpentUnlockedConnectedTxOutputs();
                    if (connectedTxOutputType == TxOutputType.BLIND_VOTE_LOCK_STAKE_OUTPUT) {
                        if (parsingModel.getInputFromBlindVoteStakeOutput() == null) {
                            parsingModel.setInputFromBlindVoteStakeOutput(txInput);
                            parsingModel.setSingleInputFromBlindVoteStakeOutput(true);
                        } else {
                            log.warn("We have a tx which has 2 connected txOutputs marked as BLIND_VOTE_LOCK_STAKE_OUTPUT. " +
                                    "This is not a valid BSQ tx.");
                            parsingModel.setSingleInputFromBlindVoteStakeOutput(false);
                        }
                    } else if (connectedTxOutputType == TxOutputType.LOCKUP) {
                        // A locked BSQ txOutput is spent to a corresponding BOND_UNLOCK
                        // txOutput. The BOND_UNLOCK can only be spent after lock time
                        // blocks has passed.
                        //TODO rename setSpentLockedConnectedTxOutput to setInputFromLockupTxOutput
                        if (parsingModel.getSpentLockedTxOutput() == null) {
                            parsingModel.setSpentLockedTxOutput(connectedTxOutput);
                            stateService.getLockTime(connectedTxOutput).ifPresent(lockTime ->
                                    parsingModel.setUnlockBlockHeight(blockHeight + lockTime));
                        }

                        // TODO do we need to check if there is only one?
                    } else if (connectedTxOutputType == TxOutputType.UNLOCK) {
                        // Spending an unlocked txOutput
                        // Use new method at parsingModel.addSpentUnlockedConnectedTxOutput
                        spentUnlockedConnectedTxOutputs.add(connectedTxOutput);
                        stateService.getUnlockBlockHeight(connectedTxOutput).ifPresent(unlockBlockHeight -> {
                            // Only count the input as BSQ input if spent after unlock time
                            //TODO <= or < ?
                            if (blockHeight <= unlockBlockHeight)
                                parsingModel.burnBond(connectedTxOutput.getValue());
                        });

                        // TODO do we need to check if there is only one?
                    }

                    //TODO ??? should be above? why removeLockTimeTxOutput
                    if (parsingModel.getSpentLockedTxOutput() != null)
                        stateService.removeLockTimeTxOutput(connectedTxOutput);
                    //TODO ???
                    spentUnlockedConnectedTxOutputs.stream().forEach(txOutput ->
                            stateService.removeUnlockBlockHeightTxOutput(txOutput));

                    stateService.setSpentInfo(connectedTxOutput.getKey(), new SpentInfo(blockHeight, txId, inputIndex));
                    stateService.removeUnspentTxOutput(connectedTxOutput);
                });
    }
}
