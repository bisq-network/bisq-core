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

package bisq.core.dao.consensus.vote.proposal;

import bisq.core.dao.consensus.state.events.payloads.Proposal;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.commons.lang3.Validate.notEmpty;

@Slf4j
public class ProposalPayloadValidator {

    @Inject
    public ProposalPayloadValidator() {
    }

    public boolean isValid(Proposal proposal) {
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
}
