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

package bisq.core.dao.voting.blindvote;

import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.state.period.PeriodService;
import bisq.core.dao.voting.ValidationException;

import javax.inject.Inject;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class BlindVoteValidator {

    private final StateService stateService;
    private final PeriodService periodService;

    @Inject
    public BlindVoteValidator(StateService stateService, PeriodService periodService) {
        this.stateService = stateService;
        this.periodService = periodService;
    }

    public boolean areDataFieldsValid(BlindVote blindVote) {
        try {
            validateDataFields(blindVote);
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    private void validateDataFields(BlindVote blindVote) throws ValidationException {
        try {
            checkNotNull(blindVote.getEncryptedBallotList(), "encryptedProposalList must not be null");
            checkArgument(blindVote.getEncryptedBallotList().length > 0, "encryptedProposalList must not be empty");
            checkNotNull(blindVote.getTxId(), "txId must not be null");
            checkArgument(blindVote.getTxId().length() > 0, "txId must not be empty");
            checkArgument(blindVote.getStake() > 0, "stake must be positive");
            //TODO check stake min/max
        } catch (Throwable e) {
            log.warn(e.toString());
            throw new ValidationException(e);
        }
    }

    public boolean isValid(BlindVote blindVote) {
        if (!areDataFieldsValid(blindVote)) {
            log.warn("blindVote is invalid. blindVote={}", blindVote);
            return false;
        }

        final String txId = blindVote.getTxId();
        Optional<Tx> optionalTx = stateService.getTx(txId);
        int chainHeight = stateService.getChainHeight();
        final boolean isTxConfirmed = optionalTx.isPresent();
        if (isTxConfirmed) {
            final int txHeight = optionalTx.get().getBlockHeight();
            if (!periodService.isTxInCorrectCycle(txHeight, chainHeight)) {
                log.debug("Tx is not in current cycle. blindVote={}", blindVote);
                return false;
            }
            if (!periodService.isInPhase(txHeight, DaoPhase.Phase.BLIND_VOTE)) {
                log.warn("Tx is not in BLIND_VOTE phase. blindVote={}", blindVote);
                return false;
            }
        } else {
            return periodService.isInPhase(chainHeight, DaoPhase.Phase.BLIND_VOTE);
        }
        return true;
    }
}
