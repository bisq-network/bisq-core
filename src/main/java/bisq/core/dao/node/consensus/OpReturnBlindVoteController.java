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

import bisq.core.dao.consensus.OpReturnType;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.blockchain.TxOutputType;
import bisq.core.dao.vote.PeriodService;
import bisq.core.dao.vote.Phase;
import bisq.core.dao.vote.proposal.param.Param;
import bisq.core.dao.vote.proposal.param.ParamService;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Verifies if OP_RETURN data matches rules for a blind vote tx and applies state change.
 */
@Slf4j
public class OpReturnBlindVoteController {
    private final ParamService paramService;
    private final PeriodService periodService;
    private final StateService stateService;

    @Inject
    public OpReturnBlindVoteController(ParamService paramService, PeriodService periodService,
                                       StateService stateService) {
        this.paramService = paramService;
        this.periodService = periodService;
        this.stateService = stateService;
    }

    // We do not check the version as if we upgrade the a new version old clients would fail. Rather we need to make
    // a change backward compatible so that new clients can handle both versions and old clients are tolerant.
    void process(byte[] opReturnData, TxOutput txOutput, Tx tx, long bsqFee, int blockHeight, Model model) {
        if (model.getBlindVoteLockStakeOutput() != null &&
                opReturnData.length == 22 &&
                bsqFee == paramService.getDaoParamValue(Param.BLIND_VOTE_FEE, blockHeight) &&
                periodService.isInPhase(blockHeight, Phase.BLIND_VOTE)) {
            stateService.setTxOutputType(txOutput, TxOutputType.BLIND_VOTE_OP_RETURN_OUTPUT);
            model.setVerifiedOpReturnType(OpReturnType.BLIND_VOTE);

            checkArgument(model.getBlindVoteLockStakeOutput() != null,
                    "model.getBlindVoteLockStakeOutput() must not be null");
            stateService.setTxOutputType(model.getBlindVoteLockStakeOutput(), TxOutputType.BLIND_VOTE_LOCK_STAKE_OUTPUT);
        } else {
            log.info("We expected a blind vote op_return data but it did not " +
                    "match our rules. txOutput={}", txOutput);
            log.info("blockHeight: " + blockHeight);
            log.info("isInPhase: " + periodService.isInPhase(blockHeight, Phase.BLIND_VOTE));
            stateService.setTxOutputType(txOutput, TxOutputType.INVALID_OUTPUT);

            // We don't want to burn the BlindVoteLockStakeOutput. We verified it at the output
            // iteration that it is valid BSQ.
            if (model.getBlindVoteLockStakeOutput() != null)
                stateService.setTxOutputType(model.getBlindVoteLockStakeOutput(), TxOutputType.BSQ_OUTPUT);
        }
    }
}
