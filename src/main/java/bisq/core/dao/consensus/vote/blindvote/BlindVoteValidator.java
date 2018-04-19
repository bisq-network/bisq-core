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

package bisq.core.dao.consensus.vote.blindvote;

import bisq.core.dao.consensus.vote.proposal.ValidationException;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class BlindVoteValidator {

    @Inject
    public BlindVoteValidator() {
    }

    public boolean isValid(BlindVote blindVote) {
        try {
            validateDataFields(blindVote);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private void validateDataFields(BlindVote blindVote) throws ValidationException {
        try {
            checkNotNull(blindVote.getEncryptedProposalList(), "encryptedProposalList must not be null");
            checkArgument(blindVote.getEncryptedProposalList().length > 0, "encryptedProposalList must not be empty");
            checkNotNull(blindVote.getTxId(), "txId must not be null");
            checkArgument(blindVote.getTxId().length() > 0, "txId must not be empty");
            checkArgument(blindVote.getStake() > 0, "stake must be positive");
            //TODO check stake min/max
        } catch (Throwable e) {
            log.warn(e.toString());
            throw new ValidationException(e);
        }
    }
}
