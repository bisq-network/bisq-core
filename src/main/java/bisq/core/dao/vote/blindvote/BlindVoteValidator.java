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

package bisq.core.dao.vote.blindvote;

import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.blockchain.TxOutputType;
import bisq.core.dao.state.blockchain.TxType;
import bisq.core.dao.vote.PeriodService;
import bisq.core.dao.vote.proposal.ValidationException;

import javax.inject.Inject;

import java.util.Arrays;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class BlindVoteValidator {

    private final PeriodService periodService;
    private final StateService stateService;

    @Inject
    public BlindVoteValidator(PeriodService periodService, StateService stateService) {
        this.periodService = periodService;
        this.stateService = stateService;
    }


    public boolean isValid(BlindVote blindVote, Tx tx, int chainHeight) {
        try {
            validateCorrectTxType(tx);
            validateCorrectTxOutputType(tx);
            validatePhase(tx.getBlockHeight());
            validateCycle(tx.getBlockHeight(), chainHeight);
            validateDataFields(blindVote);
            validateHashOfOpReturnData(blindVote, tx);
            return true;
        } catch (Throwable e) {
            log.warn("BlindVote validation failed. txId={}, blindVote={}, validationException={}",
                    tx.getId(), blindVote, e.toString());
            return false;
        }
    }

    public void validate(BlindVote blindVote, Tx tx, int chainHeight) throws ValidationException {
        validateCorrectTxType(tx);
        validateCorrectTxOutputType(tx);
        validatePhase(tx.getBlockHeight());
        validateCycle(tx.getBlockHeight(), chainHeight);
        validateDataFields(blindVote);
        validateHashOfOpReturnData(blindVote, tx);
    }

    public void validateCorrectTxType(Tx tx) throws ValidationException {
        try {
            Optional<TxType> optionalTxType = stateService.getTxType(tx.getId());
            checkArgument(optionalTxType.isPresent(), "optionalTxType must be present");
            checkArgument(optionalTxType.get() == TxType.BLIND_VOTE, "BlindVote has wrong txType");
        } catch (Throwable e) {
            log.warn("BlindVote has wrong txType. tx={},BlindVote={}", tx, this);
            throw new ValidationException(e, tx);
        }
    }


    public void validateCorrectTxOutputType(Tx tx) throws ValidationException {
        try {
            final TxOutput opReturnTxOutput = tx.getLastOutput();
            final TxOutputType opReturnTxOutputType = stateService.getTxOutputType(opReturnTxOutput);
            checkArgument(opReturnTxOutputType == TxOutputType.BLIND_VOTE_OP_RETURN_OUTPUT,
                    "Last output of tx has wrong txOutputType: txOutputType=" + opReturnTxOutputType);

            final TxOutput stakeOutput = tx.getOutputs().get(0);
            final TxOutputType stakeTxOutputType = stateService.getTxOutputType(stakeOutput);
            checkArgument(stakeTxOutputType == TxOutputType.BLIND_VOTE_LOCK_STAKE_OUTPUT,
                    "Stake output of tx has wrong txOutputType: txOutputType=" + stakeTxOutputType);
        } catch (Throwable e) {
            log.warn(e.toString());
            throw new ValidationException(e, tx);
        }
    }

    public void validatePhase(int txBlockHeight) throws ValidationException {
        try {
            checkArgument(periodService.isInPhase(txBlockHeight, PeriodService.Phase.BLIND_VOTE),
                    "Tx is not in BLIND_VOTE phase");
        } catch (Throwable e) {
            log.warn(e.toString());
            throw new ValidationException(e);
        }
    }

    public void validateDataFields(BlindVote blindVote) throws ValidationException {
        try {
            checkNotNull(blindVote.getEncryptedProposalList(), "encryptedProposalList must not be null");
            checkArgument(blindVote.getEncryptedProposalList().length > 0, "encryptedProposalList must not be empty");
            checkNotNull(blindVote.getTxId(), "txId must not be null");
            checkArgument(blindVote.getTxId().length() > 0, "txId must not be empty");
            checkNotNull(blindVote.getOwnerPubKeyEncoded(), "ownerPubKeyEncoded must not be null");
            checkArgument(blindVote.getOwnerPubKeyEncoded().length > 0, "ownerPubKeyEncoded must not be empty");
            checkArgument(blindVote.getStake() > 0, "stake must be positive");
            //TODO check stake min/max
        } catch (Throwable throwable) {
            throw new ValidationException(throwable);
        }
    }

    // We do not verify type or version as that gets verified in parser. Version might have been changed as well
    // so we don't want to fail in that case.
    public void validateHashOfOpReturnData(BlindVote blindVote, Tx tx) throws ValidationException {
        try {
            byte[] txOpReturnData = tx.getTxOutput(tx.getOutputs().size() - 1).getOpReturnData();
            checkNotNull(txOpReturnData, "txOpReturnData must not be null");
            byte[] txHashOfEncryptedProposalList = Arrays.copyOfRange(txOpReturnData, 2, 22);
            byte[] hash = BlindVoteConsensus.getHashOfEncryptedProposalList(blindVote.getEncryptedProposalList());
            checkArgument(Arrays.equals(txHashOfEncryptedProposalList, hash),
                    "OpReturn data from blind vote tx is not matching the one created from the encryptedProposalList");
        } catch (Throwable e) {
            log.warn("OpReturnData validation of blind vote failed.  blindVote={}, tx={}", this, tx);
            throw new ValidationException(e, tx);
        }
    }

    public void validateCycle(int txBlockHeight, int chainHeight)
            throws ValidationException {
        try {
            checkArgument(periodService.isTxInCorrectCycle(txBlockHeight, chainHeight),
                    "Tx is not in current cycle");
        } catch (Throwable e) {
            log.warn(e.toString());
            throw new ValidationException(e);
        }
    }
}
