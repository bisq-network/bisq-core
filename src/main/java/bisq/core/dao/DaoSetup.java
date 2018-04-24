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

import bisq.core.dao.node.BsqNode;
import bisq.core.dao.node.BsqNodeProvider;
import bisq.core.dao.period.CycleService;
import bisq.core.dao.state.StateService;
import bisq.core.dao.voting.ballot.BallotListService;
import bisq.core.dao.voting.ballot.FilteredBallotListService;
import bisq.core.dao.voting.ballot.MyBallotListService;
import bisq.core.dao.voting.blindvote.BlindVoteListService;
import bisq.core.dao.voting.blindvote.BlindVoteService;
import bisq.core.dao.voting.proposal.ProposalService;
import bisq.core.dao.voting.proposal.param.ChangeParamListService;
import bisq.core.dao.voting.voteresult.VoteResultService;
import bisq.core.dao.voting.votereveal.VoteRevealService;

import bisq.common.handlers.ErrorMessageHandler;

import com.google.inject.Inject;

/**
 * High level entry point for Dao domain
 */
public class DaoSetup {
    private final BsqNode bsqNode;
    private final ProposalService proposalService;
    private final StateService stateService;
    private final CycleService cycleService;
    private final VoteRevealService voteRevealService;
    private final VoteResultService voteResultService;
    private final ChangeParamListService changeParamListService;
    private final FilteredBallotListService filteredBallotListService;
    private final BallotListService ballotListService;
    private final MyBallotListService myBallotListService;
    private final BlindVoteService blindVoteService;
    private final BlindVoteListService blindVoteListService;

    @Inject
    public DaoSetup(BsqNodeProvider bsqNodeProvider,
                    StateService stateService,
                    CycleService cycleService,
                    VoteRevealService voteRevealService,
                    VoteResultService voteResultService,
                    ChangeParamListService changeParamListService,
                    FilteredBallotListService filteredBallotListService,
                    BallotListService ballotListService,
                    MyBallotListService myBallotListService,
                    BlindVoteService blindVoteService,
                    BlindVoteListService blindVoteListService,
                    ProposalService proposalService) {
        this.stateService = stateService;
        this.cycleService = cycleService;
        this.voteRevealService = voteRevealService;
        this.voteResultService = voteResultService;
        this.changeParamListService = changeParamListService;
        this.filteredBallotListService = filteredBallotListService;
        this.ballotListService = ballotListService;
        this.myBallotListService = myBallotListService;
        this.blindVoteService = blindVoteService;
        this.blindVoteListService = blindVoteListService;
        this.proposalService = proposalService;

        bsqNode = bsqNodeProvider.getBsqNode();
    }

    public void onAllServicesInitialized(ErrorMessageHandler errorMessageHandler) {
        cycleService.start();
        changeParamListService.start();
        proposalService.start();
        voteRevealService.start();
        ballotListService.start();
        myBallotListService.start();
        blindVoteService.start();
        filteredBallotListService.start();
        blindVoteListService.start();
        voteResultService.start();
        bsqNode.start(errorMessageHandler);
    }

    public void shutDown() {
        bsqNode.shutDown();
    }
}
