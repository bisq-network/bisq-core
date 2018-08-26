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
import bisq.core.dao.state.blockchain.SpentInfo;
import bisq.core.dao.state.blockchain.TxInput;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.blockchain.TxOutputKey;
import bisq.core.dao.state.blockchain.TxOutputType;

import javax.inject.Inject;

import java.util.Set;

import lombok.extern.slf4j.Slf4j;

/**
 * Processes TxInput and add input value to available balance if the input is a valid BSQ input.
 */
@Slf4j
public class TxInputParser {

    private final BsqStateService bsqStateService;

    @Inject
    public TxInputParser(BsqStateService bsqStateService) {
        this.bsqStateService = bsqStateService;
    }

    @SuppressWarnings("IfCanBeSwitch")
    void process(TxOutputKey txOutputKey, int blockHeight, String txId, int inputIndex, ParsingModel parsingModel) {
        bsqStateService.getUnspentTxOutput(txOutputKey)
                .ifPresent(connectedTxOutput -> {
                    parsingModel.addToInputValue(connectedTxOutput.getValue());

                    // If we are spending an output from a blind vote tx marked as VOTE_STAKE_OUTPUT we save it in our parsingModel
                    // for later verification at the outputs of a reveal tx.
                    TxOutputType connectedTxOutputType = connectedTxOutput.getTxOutputType();
                    switch (connectedTxOutputType) {
                        case UNDEFINED:
                        case GENESIS_OUTPUT:
                        case BSQ_OUTPUT:
                        case BTC_OUTPUT:
                        case PROPOSAL_OP_RETURN_OUTPUT:
                        case COMP_REQ_OP_RETURN_OUTPUT:
                        case CONFISCATE_BOND_OP_RETURN_OUTPUT:
                        case ISSUANCE_CANDIDATE_OUTPUT:
                            break;
                        case BLIND_VOTE_LOCK_STAKE_OUTPUT:
                            if (parsingModel.getVoteRevealInputState() == ParsingModel.VoteRevealInputState.UNKNOWN) {
                                // The connected tx output of the blind vote tx is our input for the reveal tx.
                                // We allow only one input from any blind vote tx otherwise the vote reveal tx is invalid.
                                parsingModel.setVoteRevealInputState(ParsingModel.VoteRevealInputState.VALID);
                            } else {
                                log.warn("We have a tx which has >1 connected txOutputs marked as BLIND_VOTE_LOCK_STAKE_OUTPUT. " +
                                        "This is not a valid BSQ tx.");
                                parsingModel.setVoteRevealInputState(ParsingModel.VoteRevealInputState.INVALID);
                            }
                            break;
                        case BLIND_VOTE_OP_RETURN_OUTPUT:
                        case VOTE_REVEAL_UNLOCK_STAKE_OUTPUT:
                        case VOTE_REVEAL_OP_RETURN_OUTPUT:
                            break;
                        case LOCKUP:
                            // A LOCKUP BSQ txOutput is spent to a corresponding UNLOCK
                            // txOutput. The UNLOCK can only be spent after lockTime blocks has passed.
                            if (parsingModel.getSpentLockupTxOutput() == null) {
                                parsingModel.setSpentLockupTxOutput(connectedTxOutput);
                                bsqStateService.getTx(connectedTxOutput.getTxId()).ifPresent(tx ->
                                        parsingModel.setUnlockBlockHeight(blockHeight + tx.getLockTime()));
                            }
                            break;
                        case LOCKUP_OP_RETURN_OUTPUT:
                            break;
                        case UNLOCK:
                            // This txInput is Spending an UNLOCK txOutput
                            Set<TxOutput> spentUnlockConnectedTxOutputs = parsingModel.getSpentUnlockConnectedTxOutputs();
                            if (spentUnlockConnectedTxOutputs != null)
                                spentUnlockConnectedTxOutputs.add(connectedTxOutput);

                            bsqStateService.getTx(connectedTxOutput.getTxId()).ifPresent(unlockTx -> {
                                // Only count the input as BSQ input if spent after unlock time
                                if (blockHeight < unlockTx.getUnlockBlockHeight())
                                    parsingModel.burnBond(connectedTxOutput.getValue());
                            });
                            break;
                        case INVALID_OUTPUT:
                        default:
                            break;
                    }

                    bsqStateService.setSpentInfo(connectedTxOutput.getKey(), new SpentInfo(blockHeight, txId, inputIndex));
                    bsqStateService.removeUnspentTxOutput(connectedTxOutput);
                });
    }
}
