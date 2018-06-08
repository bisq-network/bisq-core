/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.voting.blindvote;

import bisq.core.dao.state.ParseBlockChainListener;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.state.period.PeriodService;
import bisq.core.dao.voting.blindvote.storage.BlindVotePayload;
import bisq.core.dao.voting.blindvote.storage.BlindVoteStorageService;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreListener;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;
import bisq.network.p2p.storage.persistence.ProtectedDataStoreService;

import javax.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * Maintains preliminaryBlindVotes and confirmedBlindVotes. Republishes preliminaryBlindVotes to append-only data store
 * when entering the vote reveal phase.
 */
@Slf4j
public class BlindVoteService implements ParseBlockChainListener, AppendOnlyDataStoreListener {
    private final StateService stateService;
    private final P2PService p2PService;
    private final PeriodService periodService;
    private final BlindVoteValidator blindVoteValidator;

    private final ObservableList<BlindVotePayload> appendOnlyStoreList = FXCollections.observableArrayList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BlindVoteService(StateService stateService,
                            P2PService p2PService,
                            PeriodService periodService,
                            BlindVoteStorageService blindVoteStorageService,
                            AppendOnlyDataStoreService appendOnlyDataStoreService,
                            ProtectedDataStoreService protectedDataStoreService,
                            BlindVoteValidator blindVoteValidator) {
        this.stateService = stateService;
        this.p2PService = p2PService;
        this.periodService = periodService;
        this.blindVoteValidator = blindVoteValidator;

        appendOnlyDataStoreService.addService(blindVoteStorageService);
        stateService.addParseBlockChainListener(this);
        p2PService.getP2PDataStorage().addAppendOnlyDataStoreListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ParseBlockChainListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onComplete() {
        stateService.removeParseBlockChainListener(this);

        // Fill the lists with the data we have collected in out stores.
        fillListFromAppendOnlyDataStore();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // AppendOnlyDataStoreListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAdded(PersistableNetworkPayload payload) {
        onAppendOnlyDataAdded(payload);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
        // Fill the lists with the data we have collected in out stores.
        fillListFromAppendOnlyDataStore();
    }

    public List<BlindVote> getVerifiedBlindVotes() {
        return appendOnlyStoreList.stream()
                .filter(payload -> blindVoteValidator.isValidAndConfirmed(payload.getBlindVote()))
                .map(BlindVotePayload::getBlindVote)
                .collect(Collectors.toList());
    }

    public int getTriggerHeight() {
        // TODO we will use 10 block after first block of break2 phase to avoid re-org issues.
        return periodService.getFirstBlockOfPhase(stateService.getChainHeight(), DaoPhase.Phase.BREAK2) /* + 10*/;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We need to trigger a change when our block height has changes by applying the predicate again.
    // The underlying list might not have been changes and would not trigger a new evaluation of the filtered list.
   /* private void setPredicateForAppendOnlyPayload() {
        verifiedList.setPredicate(proposalAppendOnlyPayload -> {
            *//*if (!blindVoteValidator.isAppendOnlyPayloadValid(proposalAppendOnlyPayload,
                    getTriggerHeight(), stateService)) {
                log.debug("We received an invalid proposalAppendOnlyPayload. payload={}, blockHeight={}",
                        proposalAppendOnlyPayload, stateService.getChainHeight());
                return false;
            }*//*

            // TODO after phase we check for confirmed items only
            if (blindVoteValidator.isValidOrUnconfirmed(proposalAppendOnlyPayload.getBlindVote())) {
                return true;
            } else {
                log.debug("We received a invalid append-only proposal from the P2P network. " +
                                "Proposal.txId={}, blockHeight={}",
                        proposalAppendOnlyPayload.getBlindVote().getTxId(), stateService.getChainHeight());
                return false;
            }
        });
    }*/

    private void fillListFromAppendOnlyDataStore() {
        p2PService.getP2PDataStorage().getAppendOnlyDataStoreMap().values().forEach(this::onAppendOnlyDataAdded);
    }

    private void onAppendOnlyDataAdded(PersistableNetworkPayload persistableNetworkPayload) {
        if (persistableNetworkPayload instanceof BlindVotePayload) {
            BlindVotePayload blindVotePayload = (BlindVotePayload) persistableNetworkPayload;
            if (!appendOnlyStoreList.contains(blindVotePayload))
                appendOnlyStoreList.add(blindVotePayload);
        }
    }
}
