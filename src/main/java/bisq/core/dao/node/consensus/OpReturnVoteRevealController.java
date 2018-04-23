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

import bisq.core.dao.period.PeriodService;
import bisq.core.dao.period.Phase;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.OpReturnType;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.blockchain.TxOutputType;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Verifies if OP_RETURN data matches rules for a vote reveal tx and applies state change.
 */
@Slf4j
public class OpReturnVoteRevealController {
    private final PeriodService periodService;
    private final StateService stateService;


    @Inject
    public OpReturnVoteRevealController(PeriodService periodService,
                                        StateService stateService) {
        this.periodService = periodService;
        this.stateService = stateService;
    }

    // opReturnData: 2 bytes version and type, 20 bytes hash, 16 bytes key

    // We do not check the version as if we upgrade the a new version old clients would fail. Rather we need to make
    // a change backward compatible so that new clients can handle both versions and old clients are tolerant.
    void process(byte[] opReturnData, TxOutput txOutput, Tx tx, int blockHeight, Model model) {
        if (model.isVoteStakeSpentAtInputs() &&
                opReturnData.length == 38 &&
                periodService.isInPhase(blockHeight, Phase.VOTE_REVEAL)) {
            stateService.setTxOutputType(txOutput, TxOutputType.VOTE_REVEAL_OP_RETURN_OUTPUT);
            model.setVerifiedOpReturnType(OpReturnType.VOTE_REVEAL);
            checkArgument(model.getVoteRevealUnlockStakeOutput() != null,
                    "model.getVoteRevealUnlockStakeOutput() must not be null");
            stateService.setTxOutputType(model.getVoteRevealUnlockStakeOutput(), TxOutputType.VOTE_REVEAL_UNLOCK_STAKE_OUTPUT);

        } else {
            log.info("We expected a vote reveal op_return data but it did not " +
                    "match our rules. txOutput={}", txOutput);
            log.info("blockHeight: " + blockHeight);
            log.info("isInPhase: " + periodService.isInPhase(blockHeight, Phase.VOTE_REVEAL));
            stateService.setTxOutputType(txOutput, TxOutputType.INVALID_OUTPUT);

            // We don't want to burn the VoteRevealUnlockStakeOutput. We verified it at the output iteration
            // that it is valid BSQ.
            if (model.getVoteRevealUnlockStakeOutput() != null)
                stateService.setTxOutputType(model.getVoteRevealUnlockStakeOutput(), TxOutputType.BSQ_OUTPUT);
        }
    }
}
