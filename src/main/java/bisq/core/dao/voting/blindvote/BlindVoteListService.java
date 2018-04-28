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

import bisq.core.app.BisqEnvironment;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.state.period.PeriodService;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.HashMapChangedListener;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;

import javax.inject.Inject;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * We listen to blind votes arriving from the p2p network and maintain the blindVoteList.
 * After voteReveal we re-publish all the blind votes we received in that cycle again to add resilience for our
 * data view.
 */
@Slf4j
public class BlindVoteListService implements PersistedDataHost {
    private final P2PService p2PService;
    private final PeriodService periodService;
    private final StateService stateService;
    private final BlindVoteValidator blindVoteValidator;

    //TODO not needed to store as it is stored in PersistedEntryMap
    private final Storage<BlindVoteList> storage;

    @Getter
    private final BlindVoteList blindVoteList = new BlindVoteList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BlindVoteListService(P2PService p2PService,
                                PeriodService periodService,
                                StateService stateService,
                                BlindVoteValidator blindVoteValidator,
                                Storage<BlindVoteList> storage) {
        this.p2PService = p2PService;
        this.periodService = periodService;
        this.stateService = stateService;
        this.blindVoteValidator = blindVoteValidator;
        this.storage = storage;

        p2PService.getP2PDataStorage().addHashMapChangedListener(new HashMapChangedListener() {
            @Override
            public void onAdded(ProtectedStorageEntry entry) {
                onAddedProtectedStorageEntry(entry, true);
            }

            @Override
            public void onRemoved(ProtectedStorageEntry entry) {
                if (entry.getProtectedStoragePayload() instanceof BlindVoteProtectedStoragePayload)
                    throw new UnsupportedOperationException("Removal of blind vote data is not supported");
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted() {
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq()) {
            BlindVoteList persisted = storage.initAndGetPersisted(blindVoteList, 20);
            if (persisted != null) {
                blindVoteList.clear();
                blindVoteList.addAll(persisted.getList());
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
        // We apply already existing protectedStorageEntries
        p2PService.getP2PDataStorage().getMap().values()
                .forEach(entry -> onAddedProtectedStorageEntry(entry, false));
    }

    public void addMyBlindVote(BlindVote blindVote) {
        if (!BlindVoteUtils.blindVoteListContains(blindVote, blindVoteList.getList()) &&
                blindVoteValidator.isValid(blindVote)) {
            blindVoteList.add(blindVote);
            persist();
        }
    }

    // For additional resilience we re-publish our list of blindVotes a the moment we publish the revealVote tx.
    public void republishAllBlindVotesOfCycle() {
        p2PService.getDataMap().values().stream()
                .filter(entry -> entry.getProtectedStoragePayload() instanceof BlindVoteProtectedStoragePayload)
                .filter(entry -> {
                    final BlindVoteProtectedStoragePayload blindVoteProtectedStoragePayload = (BlindVoteProtectedStoragePayload) entry.getProtectedStoragePayload();
                    final String txId = blindVoteProtectedStoragePayload.getBlindVote().getTxId();
                    final int chainHeight = stateService.getChainHeight();
                    return periodService.isTxInCorrectCycle(txId, chainHeight) &&
                            periodService.isTxInPhase(txId, DaoPhase.Phase.BLIND_VOTE);
                })
                .forEach(entry -> p2PService.getP2PDataStorage().broadcastProtectedStorageEntry(
                        entry, p2PService.getNetworkNode().getNodeAddress(), null, false));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onAddedProtectedStorageEntry(ProtectedStorageEntry protectedStorageEntry, boolean storeLocally) {
        final ProtectedStoragePayload protectedStoragePayload = protectedStorageEntry.getProtectedStoragePayload();
        if (protectedStoragePayload instanceof BlindVoteProtectedStoragePayload) {
            final BlindVoteProtectedStoragePayload blindVoteProtectedStoragePayload = (BlindVoteProtectedStoragePayload) protectedStoragePayload;
            final BlindVote blindVote = blindVoteProtectedStoragePayload.getBlindVote();
            if (blindVoteList.stream().noneMatch(e -> e.equals(blindVote))) {
                final int height = stateService.getChainHeight();

                if (!BlindVoteUtils.blindVoteListContains(blindVote, blindVoteList.getList()) &&
                        blindVoteValidator.isValid(blindVote)) {
                    log.info("We received a BlindVoteProtectedStoragePayload from the P2P network. BlindVoteProtectedStoragePayload=" + blindVoteProtectedStoragePayload);
                    blindVoteList.add(blindVote);

                    if (storeLocally) {
                        persist();
                    }
                } else {
                    log.warn("We are not in the tolerated phase anymore and ignore that " +
                                    "blindVoteProtectedStoragePayload. blindVoteProtectedStoragePayload={}, height={}", blindVoteProtectedStoragePayload,
                            height);
                }
            } else {
                log.debug("We have that blindVoteProtectedStoragePayload already in our list. blindVoteProtectedStoragePayload={}", blindVoteProtectedStoragePayload);
            }
        }
    }

    private void persist() {
        storage.queueUpForSave();
    }
}
