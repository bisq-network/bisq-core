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

package bisq.core.dao.proposal;

import bisq.core.dao.exceptions.ValidationException;
import bisq.core.dao.period.PeriodService;
import bisq.core.dao.period.Phase;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.Tx;

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

    public boolean isValid(Proposal proposal) {
        if (!areDataFieldsValid(proposal)) {
            log.warn("proposal data fields are invalid. proposal={}", proposal);
            return false;
        }

        final String txId = proposal.getTxId();
        Optional<Tx> optionalTx = stateService.getTx(txId);
        int chainHeight = periodService.getChainHeight();
        final boolean isTxConfirmed = optionalTx.isPresent();
        if (isTxConfirmed) {
            final int txHeight = optionalTx.get().getBlockHeight();
            if (!periodService.isTxInCorrectCycle(txHeight, chainHeight)) {
                log.warn("Tx is not in current cycle. proposal={}", proposal);
                return false;
            }
            if (!periodService.isInPhase(txHeight, Phase.PROPOSAL)) {
                log.warn("Tx is not in PROPOSAL phase. proposal={}", proposal);
                return false;
            }
        } else {
            if (!periodService.isInPhase(chainHeight, Phase.PROPOSAL)) {
                log.warn("We received an unconfirmed tx and are not in PROPOSAL phase anymore. proposal={}", proposal);
                return false;
            }
        }
        return true;
    }
}
