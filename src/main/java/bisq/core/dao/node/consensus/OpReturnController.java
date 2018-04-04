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

import bisq.core.dao.blockchain.vo.Tx;
import bisq.core.dao.blockchain.vo.TxOutput;
import bisq.core.dao.blockchain.vo.TxOutputType;
import bisq.core.dao.consensus.OpReturnType;

import bisq.common.app.DevEnv;

import org.bitcoinj.core.Utils;

import javax.inject.Inject;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

/**
 * Verifies if a given transaction is a BSQ OP_RETURN transaction.
 */
@Slf4j
public class OpReturnController {
    private final OpReturnProposalController opReturnProposalController;
    private final OpReturnCompReqController opReturnCompReqController;
    private final OpReturnBlindVoteController opReturnBlindVoteController;
    private final OpReturnVoteRevealController opReturnVoteRevealController;

    @Inject
    public OpReturnController(OpReturnProposalController opReturnProposalController,
                              OpReturnCompReqController opReturnCompReqController,
                              OpReturnBlindVoteController opReturnBlindVoteController,
                              OpReturnVoteRevealController opReturnVoteRevealController) {
        this.opReturnProposalController = opReturnProposalController;
        this.opReturnCompReqController = opReturnCompReqController;
        this.opReturnBlindVoteController = opReturnBlindVoteController;
        this.opReturnVoteRevealController = opReturnVoteRevealController;
    }

    // We only check partially the rules here as we do not know the BSQ fee at that moment which is always used when
    // we have OP_RETURN data.
    public void processOpReturnCandidate(TxOutput txOutput, Model model) {
        // We do not check for pubKeyScript.scriptType.NULL_DATA because that is only set if dumpBlockchainData is true
        final byte[] opReturnData = txOutput.getOpReturnData();
        if (txOutput.getValue() == 0 && opReturnData != null && opReturnData.length >= 1) {
            OpReturnType.getOpReturnType(opReturnData[0]).ifPresent(model::setOpReturnTypeCandidate);
        }
    }

    public void processTxOutput(byte[] opReturnData, TxOutput txOutput, Tx tx, int index, long bsqFee,
                                int blockHeight, Model model) {
        getOptionalOpReturnType(opReturnData, txOutput, tx, index)
                .ifPresent(opReturnType -> {
                    switch (opReturnType) {
                        case COMPENSATION_REQUEST:
                            opReturnCompReqController.process(opReturnData, txOutput, tx, bsqFee, blockHeight, model);
                            break;
                        case PROPOSAL:
                            opReturnProposalController.process(opReturnData, txOutput, tx, bsqFee, blockHeight, model);
                            break;
                        case BLIND_VOTE:
                            opReturnBlindVoteController.process(opReturnData, txOutput, tx, bsqFee, blockHeight, model);
                            break;
                        case VOTE_REVEAL:
                            opReturnVoteRevealController.process(opReturnData, txOutput, tx, blockHeight, model);
                            break;
                        case LOCK_UP:
                            // TODO
                            txOutput.setTxOutputType(TxOutputType.BOND_LOCK_OP_RETURN_OUTPUT);
                            break;
                        case UNLOCK:
                            // TODO
                            txOutput.setTxOutputType(TxOutputType.BOND_UNLOCK_OP_RETURN_OUTPUT);
                            break;
                        default:
                            // Should never happen as long we keep OpReturnType entries in sync with out switch case.
                            final String msg = "Unsupported OpReturnType. tx=" + tx +
                                    "; opReturnData=" + Utils.HEX.encode(opReturnData);
                            log.error(msg);
                            if (DevEnv.isDevMode())
                                throw new RuntimeException(msg);

                            break;
                    }
                });
    }

    private Optional<OpReturnType> getOptionalOpReturnType(byte[] opReturnData, TxOutput txOutput, Tx tx, int index) {
        if (txOutput.getValue() == 0 &&
                index == tx.getOutputs().size() - 1 &&
                opReturnData.length >= 1) {
            return OpReturnType.getOpReturnType(opReturnData[0]);
        } else {
            log.warn("OP_RETURN version of the BSQ tx ={} does not match expected version bytes. opReturnData={}",
                    tx, Utils.HEX.encode(opReturnData));
            return Optional.empty();
        }
    }
}
