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

import bisq.core.dao.presentation.blindvote.BlindVoteServiceFacade;
import bisq.core.dao.presentation.myvote.MyBlindVoteServiceFacade;
import bisq.core.dao.presentation.period.PeriodServiceFacade;
import bisq.core.dao.presentation.proposal.FilteredBallotListService;
import bisq.core.dao.presentation.proposal.MyBallotListService;

import com.google.inject.Inject;

/**
 * Manages startup of non consensus critical services (presentationServices).
 */
public class PresentationServicesSetup {
    private final PeriodServiceFacade periodServiceFacade;
    private final FilteredBallotListService filteredBallotListService;
    private final MyBallotListService myBallotListService;
    private final MyBlindVoteServiceFacade myBlindVoteServiceFacade;
    private final BlindVoteServiceFacade blindVoteServiceFacade;

    @Inject
    public PresentationServicesSetup(PeriodServiceFacade periodServiceFacade, FilteredBallotListService filteredBallotListService,
                                     MyBallotListService myBallotListService, MyBlindVoteServiceFacade myBlindVoteServiceFacade,
                                     BlindVoteServiceFacade blindVoteServiceFacade) {
        this.periodServiceFacade = periodServiceFacade;
        this.filteredBallotListService = filteredBallotListService;
        this.myBallotListService = myBallotListService;
        this.myBlindVoteServiceFacade = myBlindVoteServiceFacade;
        this.blindVoteServiceFacade = blindVoteServiceFacade;
    }

    public void start() {
        periodServiceFacade.start();
        myBallotListService.start();
        myBlindVoteServiceFacade.start();
        filteredBallotListService.start();
        blindVoteServiceFacade.start();
    }
}
