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

import bisq.core.dao.governance.ballot.BallotListService;
import bisq.core.dao.governance.blindvote.BlindVoteService;
import bisq.core.dao.governance.blindvote.MyBlindVoteListService;
import bisq.core.dao.governance.proposal.ProposalService;
import bisq.core.dao.governance.voteresult.VoteResultService;
import bisq.core.dao.governance.votereveal.VoteRevealService;
import bisq.core.dao.node.BsqNode;
import bisq.core.dao.node.BsqNodeProvider;
import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.state.period.CycleService;

import bisq.common.handlers.ErrorMessageHandler;

import com.google.inject.Inject;

/**
 * High level entry point for Dao domain
 */
public class DaoSetup {
    private final BsqNode bsqNode;
    private final ProposalService proposalService;
    private final BsqStateService bsqStateService;
    private final CycleService cycleService;
    private final VoteRevealService voteRevealService;
    private final VoteResultService voteResultService;
    private final BallotListService ballotListService;
    private final BlindVoteService blindVoteService;
    private final MyBlindVoteListService myBlindVoteListService;

    @Inject
    public DaoSetup(BsqNodeProvider bsqNodeProvider,
                    BsqStateService bsqStateService,
                    CycleService cycleService,
                    VoteRevealService voteRevealService,
                    VoteResultService voteResultService,
                    BallotListService ballotListService,
                    BlindVoteService blindVoteService,
                    MyBlindVoteListService myBlindVoteListService,
                    ProposalService proposalService) {
        this.bsqStateService = bsqStateService;
        this.cycleService = cycleService;
        this.voteRevealService = voteRevealService;
        this.voteResultService = voteResultService;
        this.ballotListService = ballotListService;
        this.blindVoteService = blindVoteService;
        this.myBlindVoteListService = myBlindVoteListService;
        this.proposalService = proposalService;

        bsqNode = bsqNodeProvider.getBsqNode();
    }

    public void onAllServicesInitialized(ErrorMessageHandler errorMessageHandler) {
        bsqNode.setErrorMessageHandler(errorMessageHandler);

        bsqStateService.start();
        cycleService.start();
        proposalService.start();
        voteRevealService.start();
        ballotListService.start();
        blindVoteService.start();
        myBlindVoteListService.start();
        voteResultService.start();
        bsqNode.start();
    }

    public void shutDown() {
        bsqNode.shutDown();
    }
}
