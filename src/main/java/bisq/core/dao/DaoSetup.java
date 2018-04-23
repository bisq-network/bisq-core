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

import bisq.core.dao.ballot.BallotListService;
import bisq.core.dao.ballot.FilteredBallotListService;
import bisq.core.dao.ballot.MyBallotListService;
import bisq.core.dao.blindvote.BlindVoteService;
import bisq.core.dao.myvote.MyBlindVoteService;
import bisq.core.dao.node.BsqNode;
import bisq.core.dao.node.BsqNodeProvider;
import bisq.core.dao.period.PeriodStateUpdater;
import bisq.core.dao.proposal.ProposalService;
import bisq.core.dao.proposal.param.ChangeParamService;
import bisq.core.dao.voteresult.VoteResultService;
import bisq.core.dao.votereveal.VoteRevealService;

import bisq.common.handlers.ErrorMessageHandler;

import com.google.inject.Inject;

/**
 * High level entry point for Dao domain
 */
public class DaoSetup {
    private final BsqNode bsqNode;
    private final ProposalService proposalService;
    private final PeriodStateUpdater periodStateUpdater;
    private final VoteRevealService voteRevealService;
    private final VoteResultService voteResultService;
    private final ChangeParamService changeParamService;
    private final FilteredBallotListService filteredBallotListService;
    private final BallotListService ballotListService;
    private final MyBallotListService myBallotListService;
    private final MyBlindVoteService myBlindVoteService;
    private final BlindVoteService blindVoteService;

    @Inject
    public DaoSetup(BsqNodeProvider bsqNodeProvider,
                    PeriodStateUpdater periodStateUpdater,
                    VoteRevealService voteRevealService,
                    VoteResultService voteResultService,
                    ChangeParamService changeParamService,
                    FilteredBallotListService filteredBallotListService,
                    BallotListService ballotListService,
                    MyBallotListService myBallotListService,
                    MyBlindVoteService myBlindVoteService,
                    BlindVoteService blindVoteService,
                    ProposalService proposalService) {
        this.periodStateUpdater = periodStateUpdater;
        this.voteRevealService = voteRevealService;
        this.voteResultService = voteResultService;
        this.changeParamService = changeParamService;
        this.filteredBallotListService = filteredBallotListService;
        this.ballotListService = ballotListService;
        this.myBallotListService = myBallotListService;
        this.myBlindVoteService = myBlindVoteService;
        this.blindVoteService = blindVoteService;
        this.proposalService = proposalService;

        bsqNode = bsqNodeProvider.getBsqNode();
    }

    public void onAllServicesInitialized(ErrorMessageHandler errorMessageHandler) {
        periodStateUpdater.start();
        changeParamService.start();
        proposalService.start();
        voteRevealService.start();
        ballotListService.start();
        myBallotListService.start();
        myBlindVoteService.start();
        filteredBallotListService.start();
        blindVoteService.start();
        voteResultService.start();
        bsqNode.start(errorMessageHandler);
    }

    public void shutDown() {
        bsqNode.shutDown();
    }
}
