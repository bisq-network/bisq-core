/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.node.consensus;

import bisq.core.dao.OpReturnTypes;
import bisq.core.dao.blockchain.vo.Tx;
import bisq.core.dao.blockchain.vo.TxOutput;

import org.bitcoinj.core.Utils;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Verifies if a given transaction is a BSQ OP_RETURN transaction.
 */
@Slf4j
public class OpReturnController {
    private final OpReturnCompReqController opReturnCompReqController;
    private final OpReturnBlindVoteController opReturnBlindVoteController;
    private final OpReturnVoteRevealController opReturnVoteRevealController;

    @Inject
    public OpReturnController(OpReturnCompReqController opReturnCompReqController,
                              OpReturnBlindVoteController opReturnBlindVoteController,
                              OpReturnVoteRevealController opReturnVoteRevealController) {
        this.opReturnCompReqController = opReturnCompReqController;
        this.opReturnBlindVoteController = opReturnBlindVoteController;
        this.opReturnVoteRevealController = opReturnVoteRevealController;
    }

    // We only check partially the rules here as we do not know the BSQ fee at that moment which is always used when
    // we have OP_RETURN data.
    public boolean verifyOpReturnCandidate(TxOutput txOutput) {
        // We do not check for pubKeyScript.scriptType.NULL_DATA because that is only set if dumpBlockchainData is true
        final byte[] opReturnData = txOutput.getOpReturnData();
        //TODO check if it one of our supported types
        return txOutput.getValue() == 0 && opReturnData != null && opReturnData.length >= 1;
    }

    public void setOpReturnTypeCandidate(TxOutput txOutput, Model model) {
        final byte[] opReturnData = txOutput.getOpReturnData();
        checkNotNull(opReturnData, "opReturnData must nto be null");
        checkArgument(opReturnData.length >= 1, "We need to have at least 1 byte");
        model.setOpReturnTypeCandidate(opReturnData[0]);
    }

    public void process(TxOutput txOutput, Tx tx, int index, long bsqFee, int blockHeight, Model model) {
        final long txOutputValue = txOutput.getValue();
        // A BSQ OP_RETURN has to be the last output, the txOutputValue has to be 0 as well as there have to be a BSQ fee.
        if (txOutputValue == 0 && index == tx.getOutputs().size() - 1 && bsqFee > 0) {
            byte[] opReturnData = txOutput.getOpReturnData();
            if (opReturnData != null) {
                // All BSQ OP_RETURN txs have at least a type byte
                if (opReturnData.length >= 1) {
                    // Check with the type byte which kind of OP_RETURN we have.
                    switch (opReturnData[0]) {
                        case OpReturnTypes.COMPENSATION_REQUEST:
                            if (opReturnCompReqController.verify(opReturnData, bsqFee, blockHeight, model)) {
                                opReturnCompReqController.applyStateChange(txOutput, model);
                            } else {
                                log.warn("We expected a compensation request op_return data but it did not " +
                                        "match our rules. tx={}", tx);
                            }
                            break;
                        case OpReturnTypes.PROPOSAL:
                            // TODO
                            break;
                        case OpReturnTypes.BLIND_VOTE:
                            if (opReturnBlindVoteController.verify(opReturnData, bsqFee, blockHeight, model)) {
                                opReturnBlindVoteController.applyStateChange(txOutput, model);
                            } else {
                                log.warn("We expected a blind vote op_return data but it did not " +
                                        "match our rules. tx={}", tx);
                            }
                            break;
                        case OpReturnTypes.VOTE_REVEAL:
                            if (opReturnVoteRevealController.verify(opReturnData, bsqFee, blockHeight, model)) {
                                opReturnVoteRevealController.applyStateChange(txOutput, model);
                            } else {
                                log.warn("We expected a vote reveal op_return data but it did not " +
                                        "match our rules. tx={}", tx);
                            }
                            break;
                        case OpReturnTypes.LOCK_UP:
                            // TODO
                            break;
                        case OpReturnTypes.UNLOCK:
                            // TODO
                            break;
                        default:
                            log.warn("OP_RETURN version of the BSQ tx ={} does not match expected version bytes. opReturnData={}",
                                    tx.getId(), Utils.HEX.encode(opReturnData));
                            break;
                    }
                } else {
                    log.warn("opReturnData is null or has no content. opReturnData={}", Utils.HEX.encode(opReturnData));
                }
            } else {
                // TODO check if that can be a valid state or if we should thrown an exception
                log.warn("opReturnData is null");
            }
        } else {
            log.warn("opReturnData is not matching DAO rules txId={} outValue={} index={} #outputs={} bsqFee={}",
                    tx.getId(), txOutputValue, index, tx.getOutputs().size(), bsqFee);
        }
    }
}
