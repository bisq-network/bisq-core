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

import bisq.core.dao.blockchain.vo.Tx;
import bisq.core.dao.blockchain.vo.TxOutput;
import bisq.core.dao.blockchain.vo.TxOutputType;
import bisq.core.dao.consensus.OpReturnType;
import bisq.core.dao.param.DaoParam;
import bisq.core.dao.param.DaoParamService;
import bisq.core.dao.state.StateService;
import bisq.core.dao.vote.PeriodService;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

/**
 * Verifies if OP_RETURN data matches rules for a compensation request tx and applies state change.
 */
@Slf4j
public class OpReturnProposalController {
    private final DaoParamService daoParamService;
    private final PeriodService periodService;
    private final StateService stateService;


    @Inject
    public OpReturnProposalController(DaoParamService daoParamService, PeriodService periodService,
                                      StateService stateService) {
        this.daoParamService = daoParamService;
        this.periodService = periodService;
        this.stateService = stateService;
    }

    // We do not check the version as if we upgrade the a new version old clients would fail. Rather we need to make
    // a change backward compatible so that new clients can handle both versions and old clients are tolerant.
    void process(byte[] opReturnData, TxOutput txOutput, Tx tx, long bsqFee, int blockHeight, Model model) {
        if (opReturnData.length == 22 &&
                bsqFee == daoParamService.getDaoParamValue(DaoParam.PROPOSAL_FEE, blockHeight) &&
                periodService.isInPhase(blockHeight, PeriodService.Phase.PROPOSAL)) {
            stateService.setTxOutputType(txOutput, TxOutputType.PROPOSAL_OP_RETURN_OUTPUT);
            model.setVerifiedOpReturnType(OpReturnType.PROPOSAL);
        } else {
            log.info("We expected a proposal op_return data but it did not " +
                    "match our rules. txOutput={}", txOutput);
            log.info("blockHeight: " + blockHeight);
            log.info("isInPhase: " + periodService.isInPhase(blockHeight, PeriodService.Phase.PROPOSAL));
            stateService.setTxOutputType(txOutput, TxOutputType.INVALID_OUTPUT);

        }
    }
}
