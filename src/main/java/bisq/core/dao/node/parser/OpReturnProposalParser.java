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

package bisq.core.dao.node.parser;

import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.state.blockchain.TempTxOutput;
import bisq.core.dao.state.governance.Param;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.state.period.PeriodService;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

/**
 * Verifies if OP_RETURN data matches rules for a compensation request tx and applies state change.
 */
@Slf4j
public class OpReturnProposalParser {
    private final PeriodService periodService;
    private final BsqStateService bsqStateService;


    @Inject
    public OpReturnProposalParser(PeriodService periodService,
                                  BsqStateService bsqStateService) {
        this.periodService = periodService;
        this.bsqStateService = bsqStateService;
    }

    // We do not check the version as if we upgrade the a new version old clients would fail. Rather we need to make
    // a change backward compatible so that new clients can handle both versions and old clients are tolerant.
    boolean validate(byte[] opReturnData, long fee, int blockHeight, ParsingModel parsingModel) {
        boolean isInPhase = periodService.isInPhase(blockHeight, DaoPhase.Phase.PROPOSAL);
        if (!isInPhase)
            log.warn("Not in PROPOSAL phase. blockHeight={}", blockHeight);
        boolean isFeeCorrect = fee == bsqStateService.getParamValue(Param.PROPOSAL_FEE, blockHeight);
        if (!isFeeCorrect)
            log.warn("Invalid fee. used fee={}, required fee={}", fee, bsqStateService.getParamValue(Param.PROPOSAL_FEE, blockHeight));
        return opReturnData.length == 22 &&
                isFeeCorrect &&
                isInPhase;
    }
}
