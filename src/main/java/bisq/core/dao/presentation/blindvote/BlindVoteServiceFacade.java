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

package bisq.core.dao.presentation.blindvote;

import bisq.core.app.BisqEnvironment;
import bisq.core.dao.consensus.blindvote.BlindVoteList;
import bisq.core.dao.consensus.blindvote.BlindVotePayload;
import bisq.core.dao.consensus.period.PeriodService;
import bisq.core.dao.consensus.period.Phase;

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
 *
 * This class is designed to be used in userThread.
 */
@Slf4j
public class BlindVoteServiceFacade implements PersistedDataHost {
    private final P2PService p2PService;
    private final PeriodService periodService;
    private final Storage<BlindVoteList> storage;

    @Getter
    private final BlindVoteList blindVoteList = new BlindVoteList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BlindVoteServiceFacade(P2PService p2PService,
                                  PeriodService periodService,
                                  Storage<BlindVoteList> storage) {
        this.p2PService = p2PService;
        this.periodService = periodService;
        this.storage = storage;

        p2PService.getP2PDataStorage().addHashMapChangedListener(new HashMapChangedListener() {
            @Override
            public void onAdded(ProtectedStorageEntry entry) {
                onAddedProtectedStorageEntry(entry, true);
            }

            @Override
            public void onRemoved(ProtectedStorageEntry entry) {
                if (entry.getProtectedStoragePayload() instanceof BlindVotePayload)
                    throw new UnsupportedOperationException("Removal of blind vote data is not supported");
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We get called from the user thread at startup.
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onAddedProtectedStorageEntry(ProtectedStorageEntry protectedStorageEntry, boolean storeLocally) {
        final ProtectedStoragePayload protectedStoragePayload = protectedStorageEntry.getProtectedStoragePayload();
        if (protectedStoragePayload instanceof BlindVotePayload) {
            final BlindVotePayload blindVotePayload = (BlindVotePayload) protectedStoragePayload;
            if (blindVoteList.stream().noneMatch(e -> e.equals(blindVotePayload.getBlindVote()))) {
                // For adding a blindVotePayload we need to be before the last block in BREAK2 as in the last block at BREAK2
                // we write our blindVotes to the state.
                final int height = periodService.getChainHeight();
                if (isInToleratedBlockRange(height)) {
                    log.info("We received a BlindVotePayload from the P2P network. BlindVotePayload=" + blindVotePayload);
                    blindVoteList.add(blindVotePayload.getBlindVote());

                    if (storeLocally) {
                        persist();
                    }
                } else {
                    log.warn("We are not in the tolerated phase anymore and ignore that " +
                                    "blindVotePayload. blindVotePayload={}, height={}", blindVotePayload,
                            height);
                }
            } else {
                log.debug("We have that blindVotePayload already in our list. blindVotePayload={}", blindVotePayload);
            }
        }
    }

    private boolean isInToleratedBlockRange(int height) {
        return height < periodService.getLastBlockOfPhase(height, Phase.BREAK2);
    }

    private void persist() {
        storage.queueUpForSave();
    }
}
