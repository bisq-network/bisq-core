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
import bisq.core.dao.consensus.ConsensusServicesSetup;
import bisq.core.dao.presentation.PresentationServicesSetup;

import bisq.common.app.DevEnv;
import bisq.common.handlers.ErrorMessageHandler;

import com.google.inject.Inject;

/**
 * High level entry point for Dao domain
 */
public class DaoSetup {
    private final ConsensusServicesSetup consensusServicesSetup;
    private final PresentationServicesSetup presentationServicesSetup;

    @Inject
    public DaoSetup(ConsensusServicesSetup consensusServicesSetup,
                    PresentationServicesSetup presentationServicesSetup) {
        this.consensusServicesSetup = consensusServicesSetup;
        this.presentationServicesSetup = presentationServicesSetup;

    }

    public void onAllServicesInitialized(ErrorMessageHandler errorMessageHandler) {
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq() && DevEnv.isDaoPhase2Activated()) {
            // For consensus critical code we map to parser thread and delegate to consensusServicesSetup
            // ErrorMessages get mapped back to userThread.
            consensusServicesSetup.start(errorMessageHandler::handleErrorMessage);

            presentationServicesSetup.start();
        }
    }

    public void shutDown() {
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq() && DevEnv.isDaoPhase2Activated()) {
            consensusServicesSetup.shutDown();
        }
    }
}
