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

package bisq.core.dao.consensus;

import bisq.core.dao.consensus.blindvote.BlindVoteService;
import bisq.core.dao.consensus.myvote.MyVoteService;
import bisq.core.dao.consensus.node.BsqNode;
import bisq.core.dao.consensus.node.BsqNodeProvider;
import bisq.core.dao.consensus.period.PeriodStateMutator;
import bisq.core.dao.consensus.proposal.param.ChangeParamService;
import bisq.core.dao.presentation.proposal.BallotListService;

import bisq.common.handlers.ErrorMessageHandler;

import com.google.inject.Inject;

/**
 * Manages the start of the consensus critical services
 */
public class ConsensusServicesSetup {
    private final BsqNode bsqNode;
    private final PeriodStateMutator periodStateMutator;
    private final MyVoteService myVoteService;
    private final ChangeParamService changeParamService;
    private final BallotListService ballotListService;
    private final BlindVoteService blindVoteService;

    @Inject
    public ConsensusServicesSetup(BsqNodeProvider bsqNodeProvider,
                                  BallotListService ballotListService,
                                  BlindVoteService blindVoteService,
                                  PeriodStateMutator periodStateMutator,
                                  MyVoteService myVoteService,
                                  ChangeParamService changeParamService) {
        this.ballotListService = ballotListService;
        this.blindVoteService = blindVoteService;
        this.periodStateMutator = periodStateMutator;
        this.myVoteService = myVoteService;

        this.changeParamService = changeParamService;

        bsqNode = bsqNodeProvider.getBsqNode();
    }

    public void start(ErrorMessageHandler errorMessageHandler) {
        periodStateMutator.start();
        ballotListService.start();
        blindVoteService.start();
        changeParamService.start();
        myVoteService.start();
        bsqNode.start(errorMessageHandler);
    }

    public void shutDown() {
        bsqNode.shutDown();
    }
}
