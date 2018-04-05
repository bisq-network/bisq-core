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

package bisq.core.dao;

import bisq.core.app.BisqEnvironment;
import bisq.core.dao.node.BsqNode;
import bisq.core.dao.node.BsqNodeProvider;
import bisq.core.dao.vote.PeriodService;
import bisq.core.dao.vote.blindvote.BlindVoteService;
import bisq.core.dao.vote.proposal.ProposalService;
import bisq.core.dao.vote.proposal.compensation.issuance.IssuanceService;
import bisq.core.dao.vote.votereveal.VoteRevealService;

import bisq.common.app.DevEnv;
import bisq.common.handlers.ErrorMessageHandler;

import com.google.inject.Inject;

/**
 * High level entry point for Dao domain
 */
public class DaoSetup {
    private final PeriodService periodService;
    private final ProposalService proposalService;
    private final BsqNode bsqNode;
    private final VoteRevealService voteRevealService;
    private final IssuanceService issuanceService;
    private final BlindVoteService blindVoteService;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public DaoSetup(BsqNodeProvider bsqNodeProvider,
                    PeriodService periodService,
                    ProposalService proposalService,
                    BlindVoteService blindVoteService,
                    VoteRevealService voteRevealService,
                    IssuanceService issuanceService) {
        this.periodService = periodService;
        this.proposalService = proposalService;
        this.blindVoteService = blindVoteService;
        this.voteRevealService = voteRevealService;
        this.issuanceService = issuanceService;

        bsqNode = bsqNodeProvider.getBsqNode();
    }

    public void onAllServicesInitialized(ErrorMessageHandler errorMessageHandler) {
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq() && DevEnv.DAO_PHASE2_ACTIVATED) {
            periodService.onAllServicesInitialized();
            proposalService.onAllServicesInitialized();
            bsqNode.onAllServicesInitialized(errorMessageHandler);
            blindVoteService.onAllServicesInitialized();
            voteRevealService.onAllServicesInitialized();
            issuanceService.onAllServicesInitialized();
        }
    }

    public void shutDown() {
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq() && DevEnv.DAO_PHASE2_ACTIVATED) {
            periodService.shutDown();
            proposalService.shutDown();
            bsqNode.shutDown();
            blindVoteService.shutDown();
            voteRevealService.shutDown();
            issuanceService.shutDown();
        }
    }
}
