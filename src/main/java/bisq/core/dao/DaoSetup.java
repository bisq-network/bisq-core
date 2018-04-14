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
import bisq.core.dao.node.NodeExecutor;
import bisq.core.dao.vote.PeriodService;
import bisq.core.dao.vote.blindvote.BlindVoteService;
import bisq.core.dao.vote.proposal.MyProposalService;
import bisq.core.dao.vote.proposal.ProposalListService;
import bisq.core.dao.vote.proposal.ProposalService;
import bisq.core.dao.vote.proposal.param.ParamService;
import bisq.core.dao.vote.result.VoteResultService;
import bisq.core.dao.vote.result.issuance.IssuanceService;
import bisq.core.dao.vote.votereveal.VoteRevealService;

import bisq.common.app.DevEnv;
import bisq.common.handlers.ErrorMessageHandler;

import com.google.inject.Inject;

/**
 * High level entry point for Dao domain
 */
public class DaoSetup {
    private final NodeExecutor nodeExecutor;
    private final PeriodService periodService;
    private final MyProposalService myProposalService;
    private final BsqNode bsqNode;
    private final ParamService paramService;
    private final VoteRevealService voteRevealService;
    private final VoteResultService voteResultService;
    private final IssuanceService issuanceService;
    private final ProposalService proposalService;
    private final ProposalListService proposalListService;
    private final BlindVoteService blindVoteService;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public DaoSetup(NodeExecutor nodeExecutor,
                    BsqNodeProvider bsqNodeProvider,
                    PeriodService periodService,
                    MyProposalService myProposalService,
                    ProposalService proposalService,
                    ProposalListService proposalListService,
                    BlindVoteService blindVoteService,
                    VoteRevealService voteRevealService,
                    VoteResultService voteResultService,
                    IssuanceService issuanceService,
                    ParamService paramService) {
        this.nodeExecutor = nodeExecutor;
        this.periodService = periodService;
        this.myProposalService = myProposalService;
        this.proposalService = proposalService;
        this.proposalListService = proposalListService;
        this.blindVoteService = blindVoteService;
        this.voteRevealService = voteRevealService;
        this.voteResultService = voteResultService;
        this.issuanceService = issuanceService;
        this.paramService = paramService;

        bsqNode = bsqNodeProvider.getBsqNode();
    }

    public void onAllServicesInitialized(ErrorMessageHandler errorMessageHandler) {
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq() && DevEnv.DAO_PHASE2_ACTIVATED) {
            periodService.onAllServicesInitialized();
            myProposalService.onAllServicesInitialized();
            proposalListService.onAllServicesInitialized();
            bsqNode.onAllServicesInitialized(errorMessageHandler);
            voteResultService.onAllServicesInitialized();
            paramService.onAllServicesInitialized();

            nodeExecutor.get().execute(() -> {
                proposalService.onAllServicesInitialized();
                blindVoteService.onAllServicesInitialized();
                voteRevealService.onAllServicesInitialized();
            });
        }
    }

    public void shutDown() {
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq() && DevEnv.DAO_PHASE2_ACTIVATED) {
            /*periodService.shutDown();
            myProposalService.shutDown();
            proposalService.shutDown();
            proposalListService.shutDown();
            bsqNode.shutDown();
            voteRevealService.shutDown();
            voteResultService.shutDown();
            issuanceService.shutDown();
            paramService.shutDown();*/
        }
    }
}
