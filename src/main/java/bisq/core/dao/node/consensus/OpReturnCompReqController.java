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
import bisq.core.dao.proposal.param.ChangeParamService;
import bisq.core.dao.proposal.param.Param;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.OpReturnType;
import bisq.core.dao.state.blockchain.Tx;
import bisq.core.dao.state.blockchain.TxOutput;
import bisq.core.dao.state.blockchain.TxOutputType;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * Verifies if OP_RETURN data matches rules for a compensation request tx and applies state change.
 */
@Slf4j
public class OpReturnCompReqController {
    private final ChangeParamService changeParamService;
    private final PeriodService periodService;
    private final StateService stateService;


    @Inject
    public OpReturnCompReqController(ChangeParamService changeParamService, PeriodService periodService,
                                     StateService stateService) {
        this.changeParamService = changeParamService;
        this.periodService = periodService;
        this.stateService = stateService;
    }

    // We do not check the version as if we upgrade the a new version old clients would fail. Rather we need to make
    // a change backward compatible so that new clients can handle both versions and old clients are tolerant.
    void process(byte[] opReturnData, TxOutput txOutput, Tx tx, long bsqFee, int blockHeight, Model model) {
        if (model.getIssuanceCandidate() != null &&
                opReturnData.length == 22 &&
                bsqFee == changeParamService.getDaoParamValue(Param.PROPOSAL_FEE, blockHeight) &&
                periodService.isInPhase(blockHeight, Phase.PROPOSAL)) {
            stateService.setTxOutputType(txOutput, TxOutputType.COMP_REQ_OP_RETURN_OUTPUT);
            model.setVerifiedOpReturnType(OpReturnType.COMPENSATION_REQUEST);

            checkArgument(model.getIssuanceCandidate() != null,
                    "model.getCompRequestIssuanceOutputCandidate() must not be null");
            stateService.setTxOutputType(model.getIssuanceCandidate(), TxOutputType.ISSUANCE_CANDIDATE_OUTPUT);
        } else {
            log.info("We expected a compensation request op_return data but it did not " +
                    "match our rules. txOutput={}", txOutput);
            log.info("blockHeight: " + blockHeight);
            log.info("isInPhase:{}, blockHeight={}, getPhaseForHeight={}", periodService.isInPhase(blockHeight, Phase.PROPOSAL), blockHeight, periodService.getPhaseForHeight(blockHeight));
            stateService.setTxOutputType(txOutput, TxOutputType.INVALID_OUTPUT);

            // If the opReturn is invalid the issuance candidate cannot become BSQ, so we set it to BTC
            if (model.getIssuanceCandidate() != null)
                stateService.setTxOutputType(model.getIssuanceCandidate(), TxOutputType.BTC_OUTPUT);
        }
    }
}
