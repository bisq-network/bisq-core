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

package bisq.core.dao.voting.proposal;

import bisq.core.btc.wallet.WalletsManager;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.state.period.PeriodService;
import bisq.core.dao.voting.MyListService;
import bisq.core.dao.voting.proposal.storage.protectedstorage.ProposalPayload;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.crypto.KeyRing;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;

import com.google.inject.Inject;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * Publishes proposal tx and proposalPayload to p2p network. Allow removal of proposal if in proposal phase.
 * Maintains ProposalList for own proposals. Triggers republishing of my proposals at startup.
 */
@Slf4j
public class MyProposalListService extends MyListService<Proposal, ProposalList> implements PersistedDataHost {

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MyProposalListService(P2PService p2PService,
                                 StateService stateService,
                                 PeriodService periodService,
                                 WalletsManager walletsManager,
                                 Storage<ProposalList> storage,
                                 KeyRing keyRing) {
        super(p2PService,
                stateService,
                periodService,
                walletsManager,
                storage,
                keyRing);
    }

    @Override
    protected String getListName() {
        return "MyProposalList";
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected ProposalList createMyList() {
        return new ProposalList();
    }

    @Override
    protected ProtectedStoragePayload createProtectedStoragePayload(Proposal proposal) {
        return new ProposalPayload(proposal, signaturePubKey);
    }

    @Override
    protected void rePublishProposals() {
        myList.forEach(proposal -> {
            final String txId = proposal.getTxId();
            if (periodService.isTxInPhase(txId, DaoPhase.Phase.PROPOSAL) &&
                    periodService.isTxInCorrectCycle(txId, periodService.getChainHeight())) {
                if (!addToP2PNetwork(proposal))
                    log.warn("Adding of proposal to P2P network failed.\nproposal=" + proposal);
            }
        });
    }

    @Override
    protected boolean canRemoveProposal(Proposal proposal, StateService stateService, PeriodService periodService) {
        return ProposalUtils.canRemoveProposal(proposal, stateService, periodService);
    }

    @Override
    protected boolean listContainsPayload(Proposal proposal, List<Proposal> list) {
        return ProposalUtils.containsProposal(proposal, list);
    }
}
