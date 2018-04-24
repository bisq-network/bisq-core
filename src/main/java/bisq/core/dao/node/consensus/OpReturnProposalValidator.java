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

import bisq.core.dao.period.DaoPhase;
import bisq.core.dao.period.PeriodService;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.OpReturnType;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.blockchain.TxOutputType;
import bisq.core.dao.voting.proposal.param.Param;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

/**
 * Verifies if OP_RETURN data matches rules for a compensation request tx and applies state change.
 */
@Slf4j
public class OpReturnProposalValidator {
    private final PeriodService periodService;
    private final StateService stateService;


    @Inject
    public OpReturnProposalValidator(PeriodService periodService,
                                     StateService stateService) {
        this.periodService = periodService;
        this.stateService = stateService;
    }

    // We do not check the version as if we upgrade the a new version old clients would fail. Rather we need to make
    // a change backward compatible so that new clients can handle both versions and old clients are tolerant.
    void process(byte[] opReturnData, TxOutput txOutput, Tx tx, long bsqFee, int blockHeight, TxState txState) {
        if (opReturnData.length == 22 &&
                bsqFee == stateService.getParamValue(Param.PROPOSAL_FEE, blockHeight) &&
                periodService.isInPhase(blockHeight, DaoPhase.Phase.PROPOSAL)) {
            stateService.setTxOutputType(txOutput, TxOutputType.PROPOSAL_OP_RETURN_OUTPUT);
            txState.setVerifiedOpReturnType(OpReturnType.PROPOSAL);
        } else {
            log.info("We expected a proposal op_return data but it did not " +
                    "match our rules. txOutput={}", txOutput);
            log.info("blockHeight: " + blockHeight);
            log.info("isInPhase: " + periodService.isInPhase(blockHeight, DaoPhase.Phase.PROPOSAL));
            stateService.setTxOutputType(txOutput, TxOutputType.INVALID_OUTPUT);

        }
    }
}
