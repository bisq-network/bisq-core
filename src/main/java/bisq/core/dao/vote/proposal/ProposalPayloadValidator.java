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

package bisq.core.dao.vote.proposal;

import bisq.core.dao.blockchain.vo.Tx;
import bisq.core.dao.blockchain.vo.TxOutput;
import bisq.core.dao.blockchain.vo.TxOutputType;
import bisq.core.dao.blockchain.vo.TxType;
import bisq.core.dao.state.StateService;
import bisq.core.dao.vote.PeriodService;

import bisq.common.util.Utilities;

import javax.inject.Inject;

import java.util.Arrays;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.commons.lang3.Validate.notEmpty;

@Slf4j
public class ProposalPayloadValidator {
    protected final PeriodService periodService;
    protected final StateService stateService;

    @Inject
    public ProposalPayloadValidator(PeriodService periodService, StateService stateService) {
        this.periodService = periodService;
        this.stateService = stateService;
    }

    public void validate(ProposalPayload proposalPayload, Tx tx) throws ValidationException {
        validateCorrectTxType(proposalPayload, tx);
        validateCorrectTxOutputType(proposalPayload, tx);
        validatePhase(tx.getBlockHeight());
        validateDataFields(proposalPayload);
        validateHashOfOpReturnData(proposalPayload, tx);
    }

    public void validateCorrectTxType(ProposalPayload proposalPayload, Tx tx) throws ValidationException {
        try {
            Optional<TxType> optionalTxType = stateService.getTxType(tx.getId());
            checkArgument(optionalTxType.isPresent(), "optionalTxType must be present");
            checkArgument(optionalTxType.get() == proposalPayload.getTxType(),
                    "ProposalPayload has wrong txType. txType=" + optionalTxType.get());
        } catch (Throwable e) {
            log.warn(e.toString());
            throw new ValidationException(e, tx);
        }
    }

    public void validateCorrectTxOutputType(ProposalPayload proposalPayload, Tx tx) throws ValidationException {
        try {
            final TxOutput lastOutput = tx.getLastOutput();
            final TxOutputType txOutputType = stateService.getTxOutputType(lastOutput);
            checkArgument(txOutputType == proposalPayload.getTxOutputType(),
                    "Last output of tx has wrong txOutputType: txOutputType=" + txOutputType);
        } catch (Throwable e) {
            log.warn(e.toString());
            throw new ValidationException(e, tx);
        }
    }


    public void validatePhase(int txBlockHeight) throws ValidationException {
        try {
            checkArgument(periodService.isInPhase(txBlockHeight, PeriodService.Phase.PROPOSAL),
                    "Tx is not in PROPOSAL phase");
        } catch (Throwable e) {
            log.warn(e.toString());
            throw new ValidationException(e);
        }
    }

    public void validateDataFields(ProposalPayload proposalPayload) throws ValidationException {
        try {
            notEmpty(proposalPayload.getName(), "name must not be empty");
            notEmpty(proposalPayload.getTitle(), "title must not be empty");
            notEmpty(proposalPayload.getDescription(), "description must not be empty");
            notEmpty(proposalPayload.getLink(), "link must not be empty");

            checkArgument(ProposalConsensus.isDescriptionSizeValid(proposalPayload.getDescription()), "description is too long");
        } catch (Throwable throwable) {
            throw new ValidationException(throwable);
        }
    }


    // We do not verify type or version as that gets verified in parser. Version might have been changed as well
    // so we don't want to fail in that case.
    public void validateHashOfOpReturnData(ProposalPayload proposalPayload, Tx tx) throws ValidationException {
        try {
            byte[] txOpReturnData = tx.getTxOutput(tx.getOutputs().size() - 1).getOpReturnData();
            checkNotNull(txOpReturnData, "txOpReturnData must not be null");
            byte[] txHashOfPayload = Arrays.copyOfRange(txOpReturnData, 2, 22);
            // We need to set txId to null in clone to get same hash as used in the tx return data
            byte[] hash = ProposalConsensus.getHashOfPayload(proposalPayload.getCloneWithoutTxId());
            checkArgument(Arrays.equals(txHashOfPayload, hash),
                    "OpReturn data from proposal tx is not matching the one created from the payload." +
                            "\ntxHashOfPayload=" + Utilities.encodeToHex(txHashOfPayload) +
                            "\nhash=" + Utilities.encodeToHex(hash));
        } catch (Throwable e) {
            log.debug("OpReturnData validation of proposalPayload failed. proposalPayload={}, tx={}", this, tx);
            throw new ValidationException(e, tx);
        }
    }


    public void validateCycle(int txBlockHeight, int currentChainHeight) throws ValidationException {
        try {
            checkArgument(periodService.isTxInCorrectCycle(txBlockHeight, currentChainHeight),
                    "Tx is not in current cycle");
        } catch (Throwable e) {
            log.warn(e.toString());
            throw new ValidationException(e);
        }
    }
}
