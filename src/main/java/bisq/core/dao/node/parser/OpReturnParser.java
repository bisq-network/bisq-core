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

package bisq.core.dao.node.parser;

import bisq.core.dao.bonding.BondingConsensus;
import bisq.core.dao.bonding.lockup.LockupType;
import bisq.core.dao.state.blockchain.OpReturnType;
import bisq.core.dao.state.blockchain.TempTx;
import bisq.core.dao.state.blockchain.TempTxOutput;
import bisq.core.dao.state.blockchain.TxOutputType;

import bisq.common.app.DevEnv;
import bisq.common.util.Utilities;

import org.bitcoinj.core.Utils;

import javax.inject.Inject;

import java.util.Arrays;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

/**
 * Processes OpReturn output if valid and delegates validation to specific validators.
 */
@Slf4j
public class OpReturnParser {
    private final OpReturnProposalParser opReturnProposalParser;
    private final OpReturnCompReqParser opReturnCompReqParser;
    private final OpReturnBlindVoteParser opReturnBlindVoteParser;
    private final OpReturnVoteRevealParser opReturnVoteRevealParser;
    private final OpReturnLockupParser opReturnLockupParser;

    @Inject
    public OpReturnParser(OpReturnProposalParser opReturnProposalParser,
                          OpReturnCompReqParser opReturnCompReqParser,
                          OpReturnBlindVoteParser opReturnBlindVoteParser,
                          OpReturnVoteRevealParser opReturnVoteRevealParser,
                          OpReturnLockupParser opReturnLockupParser) {

        this.opReturnProposalParser = opReturnProposalParser;
        this.opReturnCompReqParser = opReturnCompReqParser;
        this.opReturnBlindVoteParser = opReturnBlindVoteParser;
        this.opReturnVoteRevealParser = opReturnVoteRevealParser;
        this.opReturnLockupParser = opReturnLockupParser;
    }

    // We only check partially the rules here as we do not know the BSQ fee at that moment which is always used when
    // we have OP_RETURN data.
    public void processOpReturnCandidate(TempTxOutput txOutput, ParsingModel parsingModel) {
        // We do not check for pubKeyScript.scriptType.NULL_DATA because that is only set if dumpBlockchainData is true
        final byte[] opReturnData = txOutput.getOpReturnData();
        if (txOutput.getValue() == 0 && opReturnData != null && opReturnData.length >= 1) {
            OpReturnType.getOpReturnType(opReturnData[0])
                    .ifPresent(parsingModel::setOpReturnTypeCandidate);
        }
    }

    /**
     * Parse the type of OP_RETURN data and validate it.
     *
     * @param opReturnData  The raw bytes of the OP_RETURN to parse.
     * @param nonZeroOutput If true, the output being parsed has a non-zero value.
     * @param tx            The transaction that the output belongs to.
     * @param index         The index of the output in the {@code tx}.
     * @param bsqFee        The fee which should be paid in BSQ.
     * @param blockHeight   The height of the block that includes {@code tx}.
     * @param parsingModel  The parsing model.
     * @return The type of the transaction output, which will be either one of the
     *                          {@code *_OP_RETURN_OUTPUT} values, or {@code UNDEFINED} in case of
     *                          unexpected state.
     *
     * todo(chirhonul): simplify signature by combining types: tx, nonZeroOutput, index, bsqFee, blockHeight all seem related
     */
    public TxOutputType parseAndValidate(byte[] opReturnData, boolean nonZeroOutput, TempTx tx, int index, long bsqFee,
                                         int blockHeight, ParsingModel parsingModel) {
        if (nonZeroOutput ||
                index != tx.getTempTxOutputs().size() - 1 ||
                opReturnData.length < 1) {
            log.warn("OP_RETURN data does not match our rules. opReturnData={}",
                    tx, Utils.HEX.encode(opReturnData));
            return TxOutputType.UNDEFINED;
        }
        final Optional<OpReturnType> optionalOpReturnType = OpReturnType.getOpReturnType(opReturnData[0]);
        if (!optionalOpReturnType.isPresent()) {
            // TODO add exception?
            log.warn("OP_RETURN data does not match our defined types. opReturnData={}",
                    tx, Utils.HEX.encode(opReturnData));
            return TxOutputType.UNDEFINED;
        }
        return validate(opReturnData, tx, bsqFee, blockHeight, parsingModel, optionalOpReturnType.get());
    }

    /**
     * Validate that the OP_RETURN data is correct for specified type.
     *
     * @param opReturnData The raw OP_RETURN data.
     * @param tx           The transaction that the OP_RETURN output is from.
     * @param bsqFee       The fee in BSQ for the operation.
     * @param parsingModel The parsing model.
     * @param opReturnType The type of the OP_RETURN operation.
     * @return The type of transaction output, which will be either one of the
     *                          {@code *_OP_RETURN_OUTPUT} values, or {@code UNDEFINED} in case of
     *                          unexpected state.
     */
    private TxOutputType validate(byte[] opReturnData, TempTx tx, long bsqFee, int blockHeight,
                                  ParsingModel parsingModel, OpReturnType opReturnType) {
        TxOutputType outputType = TxOutputType.UNDEFINED;
        switch (opReturnType) {
            case PROPOSAL:
                outputType = processProposal(opReturnData);
                break;
            case COMPENSATION_REQUEST:
                outputType = processCompensationRequest(opReturnData);
                break;
            case BLIND_VOTE:
                outputType = processBlindVote(opReturnData);
                break;
            case VOTE_REVEAL:
                outputType = processVoteReveal(opReturnData);
                break;
            case LOCKUP:
                outputType = processLockup(opReturnData, tx.getTempTxOutputs().get(tx.getTempTxOutputs().size() - 1));
                break;
            default:
                // Should never happen as long we keep OpReturnType entries in sync with out switch case.
                final String msg = "Unsupported OpReturnType. tx=" + tx +
                        "; opReturnData=" + Utils.HEX.encode(opReturnData);
                log.error(msg);
                DevEnv.logErrorAndThrowIfDevMode(msg);
                break;
        }
        return outputType;
    }

    private TxOutputType processProposal(byte[] opReturnData) {
        if (validateProposal(opReturnData)) {
            return TxOutputType.PROPOSAL_OP_RETURN_OUTPUT;
        } else {
            log.info("We expected a proposal op_return data but it did not " +
                    "match our rules.");
            return TxOutputType.INVALID_OUTPUT;
        }
    }

    private boolean validateProposal(byte[] opReturnData) {
        return opReturnData.length == 22;
    }

    private TxOutputType processCompensationRequest(byte[] opReturnData) {
        if (validateProposal(opReturnData)/*opReturnCompReqParser.validate(opReturnData, bsqFee, blockHeight, parsingModel)*/) {
            return TxOutputType.COMP_REQ_OP_RETURN_OUTPUT;
        } else {
            log.info("We expected a compensation request op_return data but it did not " +
                    "match our rules.");

            return TxOutputType.INVALID_OUTPUT;
        }
    }

    private TxOutputType processBlindVote(byte[] opReturnData) {
        if (opReturnData.length == 22) {
            return TxOutputType.BLIND_VOTE_OP_RETURN_OUTPUT;
        } else {
            log.info("We expected a blind vote op_return data but it did not " +
                    "match our rules.");
            return TxOutputType.INVALID_OUTPUT;
        }
    }

    private TxOutputType processVoteReveal(byte[] opReturnData) {
        if (opReturnData.length == 38) {
            return TxOutputType.VOTE_REVEAL_OP_RETURN_OUTPUT;
        } else {
            log.info("We expected a vote reveal op_return data but it did not " +
                    "match our rules.");
            return TxOutputType.INVALID_OUTPUT;
        }
    }

    private TxOutputType processLockup(byte[] opReturnData, TempTxOutput tempTxOutput) {
        if (opReturnData.length != 25) {
            return TxOutputType.INVALID_OUTPUT;
        }

        Optional<LockupType> lockupType = LockupType.getLockupType(opReturnData[2]);
        if (!lockupType.isPresent()) {
            log.warn("No lockupType found for lockup tx, opReturnData=" + Utilities.encodeToHex(opReturnData));
            return TxOutputType.INVALID_OUTPUT;
        }

        int lockTime = Utilities.byteArrayToInteger(Arrays.copyOfRange(opReturnData, 3, 5));
        if (lockTime >= BondingConsensus.getMinLockTime() &&
                lockTime <= BondingConsensus.getMaxLockTime()) {

            //TODO should be in txOutputParser
            tempTxOutput.setLockTime(lockTime);
            return TxOutputType.LOCKUP_OP_RETURN_OUTPUT;
        } else {
            log.info("We expected a lockup op_return data but it did not " +
                    "match our rules.");
            return TxOutputType.INVALID_OUTPUT;
        }
    }
}
