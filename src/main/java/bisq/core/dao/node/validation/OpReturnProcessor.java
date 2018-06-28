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

package bisq.core.dao.node.validation;

import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.OpReturnType;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.blockchain.TxOutputType;
import bisq.core.dao.state.blockchain.Util;

import bisq.common.app.DevEnv;

import org.bitcoinj.core.Utils;

import javax.inject.Inject;

import java.util.Arrays;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

/**
 * Processes OpReturn output if valid and delegates validation to specific validators.
 */
@Slf4j
public class OpReturnProcessor {
    private final OpReturnProposalValidator opReturnProposalValidator;
    private final OpReturnCompReqValidator opReturnCompReqValidator;
    private final OpReturnBlindVoteValidator opReturnBlindVoteValidator;
    private final OpReturnVoteRevealValidator opReturnVoteRevealValidator;
    private final OpReturnLockupValidator opReturnLockupValidator;
    private final StateService stateService;

    @Inject
    public OpReturnProcessor(OpReturnProposalValidator opReturnProposalValidator,
                             OpReturnCompReqValidator opReturnCompReqValidator,
                             OpReturnBlindVoteValidator opReturnBlindVoteValidator,
                             OpReturnVoteRevealValidator opReturnVoteRevealValidator,
                             OpReturnLockupValidator opReturnLockupValidator,
                             StateService stateService) {

        this.opReturnProposalValidator = opReturnProposalValidator;
        this.opReturnCompReqValidator = opReturnCompReqValidator;
        this.opReturnBlindVoteValidator = opReturnBlindVoteValidator;
        this.opReturnVoteRevealValidator = opReturnVoteRevealValidator;
        this.opReturnLockupValidator = opReturnLockupValidator;
        this.stateService = stateService;
    }

    // We only check partially the rules here as we do not know the BSQ fee at that moment which is always used when
    // we have OP_RETURN data.
    public void processOpReturnCandidate(TxOutput txOutput, ParsingModel parsingModel) {
        // We do not check for pubKeyScript.scriptType.NULL_DATA because that is only set if dumpBlockchainData is true
        final byte[] opReturnData = txOutput.getOpReturnData();
        if (txOutput.getValue() == 0 && opReturnData != null && opReturnData.length >= 1) {
            OpReturnType.getOpReturnType(opReturnData[0])
                    .ifPresent(parsingModel::setOpReturnTypeCandidate);
        }
    }

    public void validate(byte[] opReturnData, TxOutput txOutput, Tx tx, int index, long bsqFee,
                         int blockHeight, ParsingModel parsingModel) {
        if (txOutput.getValue() == 0 &&
                index == tx.getOutputs().size() - 1 &&
                opReturnData.length >= 1) {
            final Optional<OpReturnType> optionalOpReturnType = OpReturnType.getOpReturnType(opReturnData[0]);
            if (optionalOpReturnType.isPresent()) {
                selectValidator(opReturnData, txOutput, tx, bsqFee, blockHeight, parsingModel, optionalOpReturnType.get());
            } else {
                log.warn("OP_RETURN data does not match our defined types. opReturnData={}",
                        tx, Utils.HEX.encode(opReturnData));
            }
        } else {
            log.warn("OP_RETURN data does not match our rules. opReturnData={}",
                    tx, Utils.HEX.encode(opReturnData));
        }
    }

    private void selectValidator(byte[] opReturnData, TxOutput txOutput, Tx tx, long bsqFee, int blockHeight,
                                 ParsingModel parsingModel, OpReturnType opReturnType) {
        switch (opReturnType) {
            case PROPOSAL:
                processProposal(opReturnData, txOutput, bsqFee, blockHeight, parsingModel);
                break;
            case COMPENSATION_REQUEST:
                processCompensationRequest(opReturnData, txOutput, bsqFee, blockHeight, parsingModel);
                break;
            case BLIND_VOTE:
                processBlindVote(opReturnData, txOutput, bsqFee, blockHeight, parsingModel);
                break;
            case VOTE_REVEAL:
                processVoteReveal(opReturnData, txOutput, blockHeight, parsingModel);
                break;
            case LOCKUP:
                processLockup(opReturnData, txOutput, blockHeight, parsingModel);
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
    }

    private void processProposal(byte[] opReturnData, TxOutput txOutput, long bsqFee, int blockHeight, ParsingModel parsingModel) {
        if (opReturnProposalValidator.validate(opReturnData, txOutput, bsqFee, blockHeight, parsingModel)) {
            stateService.setTxOutputType(txOutput, TxOutputType.PROPOSAL_OP_RETURN_OUTPUT);
            parsingModel.setVerifiedOpReturnType(OpReturnType.PROPOSAL);
        } else {
            log.info("We expected a proposal op_return data but it did not " +
                    "match our rules. txOutput={}; blockHeight={}", txOutput, blockHeight);
            stateService.setTxOutputType(txOutput, TxOutputType.INVALID_OUTPUT);
        }
    }

    private void processCompensationRequest(byte[] opReturnData, TxOutput txOutput, long bsqFee, int blockHeight, ParsingModel parsingModel) {
        final TxOutput issuanceCandidate = parsingModel.getIssuanceCandidate();
        if (opReturnCompReqValidator.validate(opReturnData, txOutput, bsqFee, blockHeight, parsingModel)) {
            stateService.setTxOutputType(txOutput, TxOutputType.COMP_REQ_OP_RETURN_OUTPUT);
            stateService.setTxOutputType(issuanceCandidate, TxOutputType.ISSUANCE_CANDIDATE_OUTPUT);
            parsingModel.setVerifiedOpReturnType(OpReturnType.COMPENSATION_REQUEST);
        } else {
            log.info("We expected a compensation request op_return data but it did not " +
                    "match our rules. txOutput={}; blockHeight={}", txOutput, blockHeight);
            stateService.setTxOutputType(txOutput, TxOutputType.INVALID_OUTPUT);

            // If the opReturn is invalid the issuance candidate cannot become BSQ, so we set it to BTC
            if (issuanceCandidate != null)
                stateService.setTxOutputType(issuanceCandidate, TxOutputType.BTC_OUTPUT);
        }
    }

    private void processBlindVote(byte[] opReturnData, TxOutput txOutput, long bsqFee, int blockHeight, ParsingModel parsingModel) {
        final TxOutput blindVoteLockStakeOutput = parsingModel.getBlindVoteLockStakeOutput();
        if (opReturnBlindVoteValidator.validate(opReturnData, bsqFee, blockHeight, parsingModel)) {
            stateService.setTxOutputType(txOutput, TxOutputType.BLIND_VOTE_OP_RETURN_OUTPUT);
            stateService.setTxOutputType(blindVoteLockStakeOutput, TxOutputType.BLIND_VOTE_LOCK_STAKE_OUTPUT);
            parsingModel.setVerifiedOpReturnType(OpReturnType.BLIND_VOTE);
        } else {
            log.info("We expected a blind vote op_return data but it did not " +
                    "match our rules. txOutput={}; blockHeight={}", txOutput, blockHeight);

            //TODO does it makes the tx invalid if we set opReturn type to INVALID_OUTPUT?
            stateService.setTxOutputType(txOutput, TxOutputType.INVALID_OUTPUT);

            // We don't want to burn the BlindVoteLockStakeOutput. We verified it at the output
            // iteration that it is valid BSQ so we set TxOutputType.BSQ_OUTPUT.
            if (blindVoteLockStakeOutput != null)
                stateService.setTxOutputType(blindVoteLockStakeOutput, TxOutputType.BSQ_OUTPUT);
        }
    }

    private void processVoteReveal(byte[] opReturnData, TxOutput txOutput, int blockHeight, ParsingModel parsingModel) {
        if (opReturnVoteRevealValidator.validate(opReturnData, blockHeight, parsingModel)) {
            stateService.setTxOutputType(txOutput, TxOutputType.VOTE_REVEAL_OP_RETURN_OUTPUT);
            stateService.setTxOutputType(parsingModel.getVoteRevealUnlockStakeOutput(), TxOutputType.VOTE_REVEAL_UNLOCK_STAKE_OUTPUT);
            parsingModel.setVerifiedOpReturnType(OpReturnType.VOTE_REVEAL);
        } else {
            log.info("We expected a vote reveal op_return data but it did not " +
                    "match our rules. txOutput={}; blockHeight={}", txOutput, blockHeight);

            //TODO does it makes the tx invalid if we set opReturn type to INVALID_OUTPUT?
            stateService.setTxOutputType(txOutput, TxOutputType.INVALID_OUTPUT);

            // We don't want to burn the VoteRevealUnlockStakeOutput. We verified it at the output iteration
            // that it is valid BSQ.
            if (parsingModel.getVoteRevealUnlockStakeOutput() != null)
                stateService.setTxOutputType(parsingModel.getVoteRevealUnlockStakeOutput(), TxOutputType.BSQ_OUTPUT);
        }
    }

    private void processLockup(byte[] opReturnData, TxOutput txOutput, int blockHeight, ParsingModel parsingModel) {
        final TxOutput lockupCandidate = parsingModel.getLockupOutput();

        int lockTime = Util.parseAsInt(Arrays.copyOfRange(opReturnData, 2, 4));

        if (opReturnLockupValidator.validate(opReturnData, lockTime, blockHeight, parsingModel)) {
            stateService.setTxOutputType(txOutput, TxOutputType.LOCKUP_OP_RETURN_OUTPUT);
            stateService.setTxOutputType(lockupCandidate, TxOutputType.LOCKUP);
            stateService.setLockTime(lockupCandidate, lockTime);
            parsingModel.setVerifiedOpReturnType(OpReturnType.LOCKUP);
        } else {
            log.info("We expected a lockup op_return data but it did not " +
                    "match our rules. txOutput={}; blockHeight={}", txOutput, blockHeight);
            stateService.setTxOutputType(txOutput, TxOutputType.INVALID_OUTPUT);

            // If the opReturn is invalid the lockup candidate cannot become BSQ, so we set it to BTC
            if (lockupCandidate != null)
                stateService.setTxOutputType(lockupCandidate, TxOutputType.BTC_OUTPUT);
        }
    }
}
