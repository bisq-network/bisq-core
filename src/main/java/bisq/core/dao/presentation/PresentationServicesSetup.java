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

package bisq.core.dao.presentation;

import bisq.core.dao.presentation.myvote.MyVoteService;
import bisq.core.dao.presentation.period.PeriodServiceFacade;
import bisq.core.dao.presentation.proposal.MyProposalService;
import bisq.core.dao.presentation.proposal.ProposalListService;

import com.google.inject.Inject;

/**
 * Manages startup of non consensus critical services (presentationServices).
 */
public class PresentationServicesSetup {
    private PeriodServiceFacade periodServiceFacade;
    private final ProposalListService proposalListService;
    private final MyProposalService myProposalService;
    private MyVoteService myVoteService;

    @Inject
    public PresentationServicesSetup(PeriodServiceFacade periodServiceFacade, ProposalListService proposalListService,
                                     MyProposalService myProposalService, MyVoteService myVoteService) {
        this.periodServiceFacade = periodServiceFacade;
        this.proposalListService = proposalListService;
        this.myProposalService = myProposalService;
        this.myVoteService = myVoteService;
    }

    public void start() {
        periodServiceFacade.start();
        myProposalService.start();
        myVoteService.start();
        proposalListService.start();
    }
}
