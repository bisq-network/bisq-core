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

package bisq.core.dao.voting.proposal;

import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.Block;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.state.period.PeriodService;
import bisq.core.dao.voting.ValidationException;
import bisq.core.dao.voting.proposal.storage.appendonly.ProposalAppendOnlyPayload;

import bisq.common.util.Utilities;

import javax.inject.Inject;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.commons.lang3.Validate.notEmpty;

@Slf4j
public class ProposalValidator {

    private final StateService stateService;
    private final PeriodService periodService;

    @Inject
    public ProposalValidator(StateService stateService, PeriodService periodService) {
        this.stateService = stateService;
        this.periodService = periodService;
    }

    public boolean areDataFieldsValid(Proposal proposal) {
        try {
            validateDataFields(proposal);
            return true;
        } catch (ValidationException e) {
            return false;
        }
    }

    public void validateDataFields(Proposal proposal) throws ValidationException {
        try {
            notEmpty(proposal.getName(), "name must not be empty");
            notEmpty(proposal.getTitle(), "title must not be empty");
            notEmpty(proposal.getDescription(), "description must not be empty");
            notEmpty(proposal.getLink(), "link must not be empty");

            checkArgument(ProposalConsensus.isDescriptionSizeValid(proposal.getDescription()), "description is too long");
        } catch (Throwable throwable) {
            throw new ValidationException(throwable);
        }
    }


    public boolean isValidOrUnconfirmed(Proposal proposal) {
        return isValid(proposal, true);
    }

    public boolean isValidAndConfirmed(Proposal proposal) {
        return isValid(proposal, false);
    }

    private boolean isValid(Proposal proposal, boolean allowUnconfirmed) {
        if (!areDataFieldsValid(proposal)) {
            log.warn("proposal data fields are invalid. proposal.getTxId()={}", proposal.getTxId());
            return false;
        }

        final String txId = proposal.getTxId();
        if (txId == null || txId.equals("")) {
            log.warn("txId must be set. proposal.getTxId()={}", proposal.getTxId());
            return false;
        }

        Optional<Tx> optionalTx = stateService.getTx(txId);
        final boolean isTxConfirmed = optionalTx.isPresent();
        int chainHeight = stateService.getChainHeight();

        if (isTxConfirmed) {
            final int txHeight = optionalTx.get().getBlockHeight();
            if (!periodService.isTxInCorrectCycle(txHeight, chainHeight)) {
                log.debug("Tx is not in current cycle. proposal.getTxId()={}", proposal.getTxId());
                return false;
            }
            if (!periodService.isInPhase(txHeight, DaoPhase.Phase.PROPOSAL)) {
                log.debug("Tx is not in PROPOSAL phase. proposal.getTxId()={}", proposal.getTxId());
                return false;
            }
            return true;
        } else if (allowUnconfirmed) {
            // We want to show own unconfirmed proposals in the active proposals list.
            final boolean inPhase = periodService.isInPhase(chainHeight, DaoPhase.Phase.PROPOSAL);
            if (inPhase)
                log.debug("proposal is unconfirmed and we are in proposal phase: txId={}", txId);
            return inPhase;
        } else {
            return false;
        }
    }

    public boolean hasCorrectBlockHash(ProposalAppendOnlyPayload appendOnlyPayload,
                                       int blockHeightOfBreakStart,
                                       StateService stateService) {
        final Optional<Block> optionalBlock = stateService.getBlockAtHeight(blockHeightOfBreakStart);
        if (optionalBlock.isPresent()) {
            final String blockHash = Utilities.encodeToHex(appendOnlyPayload.getBlockHash());
            final boolean hasCorrectBlockHash = blockHash.equals(optionalBlock.get().getHash());
            if (!hasCorrectBlockHash) {
                //TODO called at startup when we are not in cycle of proposal
                log.debug("ProposalAppendOnlyPayload has not correct block hash. blockHeightOfBreakStart={}",
                        blockHeightOfBreakStart);
                return false;
            }

            return true;
        } else {
            log.debug("block at blockHeightOfBreakStart is not present.");
            return false;
        }
    }
}
