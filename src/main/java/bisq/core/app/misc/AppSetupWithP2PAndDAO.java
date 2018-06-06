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

package bisq.core.app.misc;

import bisq.core.dao.DaoSetup;
import bisq.core.dao.vote.blindvote.BlindVoteService;
import bisq.core.dao.vote.proposal.ProposalService;
import bisq.core.filter.FilterManager;
import bisq.core.payment.AccountAgeWitnessService;
import bisq.core.trade.statistics.TradeStatisticsManager;

import bisq.network.crypto.EncryptionService;
import bisq.network.p2p.P2PService;

import bisq.common.crypto.KeyRing;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AppSetupWithP2PAndDAO extends AppSetupWithP2P {
    private final DaoSetup daoSetup;

    @Inject
    public AppSetupWithP2PAndDAO(EncryptionService encryptionService,
                                 KeyRing keyRing,
                                 P2PService p2PService,
                                 TradeStatisticsManager tradeStatisticsManager,
                                 AccountAgeWitnessService accountAgeWitnessService,
                                 FilterManager filterManager,
                                 DaoSetup daoSetup,
                                 ProposalService proposalService,
                                 BlindVoteService blindVoteService) {
        super(encryptionService,
                keyRing,
                p2PService,
                tradeStatisticsManager,
                accountAgeWitnessService,
                filterManager);
        this.daoSetup = daoSetup;
        this.persistedDataHosts.add(proposalService);
        persistedDataHosts.add(blindVoteService);
    }

    @Override
    protected void onBasicServicesInitialized() {
        super.onBasicServicesInitialized();

        daoSetup.onAllServicesInitialized(log::error);
    }
}
