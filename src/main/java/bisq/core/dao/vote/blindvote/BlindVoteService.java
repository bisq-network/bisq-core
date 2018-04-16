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

package bisq.core.dao.vote.blindvote;

import bisq.core.app.BisqEnvironment;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.events.AddBlindVoteEvent;
import bisq.core.dao.state.events.StateChangeEvent;
import bisq.core.dao.vote.period.PeriodService;
import bisq.core.dao.vote.period.Phase;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.HashMapChangedListener;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;

import javax.inject.Inject;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * We listen to blind votes arriving from the p2p network as well as on new txBlocks.
 * In case we are in the last block of the break after the blind vote period we pass over our collected
 * blind votes to be stored in the state and remove those items from our openBlindVoteList.
 * <p>
 * All methods in that class are executed on the parser thread.
 */
@Slf4j
public class BlindVoteService implements PersistedDataHost {
    private final P2PService p2PService;
    private final PeriodService periodService;
    private final StateService stateService;
    private final BlindVoteValidator blindVoteValidator;
    private final Storage<BlindVoteList> storage;

    @Getter
    private final BlindVoteList openBlindVoteList = new BlindVoteList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BlindVoteService(P2PService p2PService,
                            PeriodService periodService,
                            StateService stateService,
                            BlindVoteValidator blindVoteValidator,
                            Storage<BlindVoteList> storage) {
        this.p2PService = p2PService;
        this.periodService = periodService;
        this.stateService = stateService;
        this.blindVoteValidator = blindVoteValidator;
        this.storage = storage;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We get called from the user thread at startup.
    @Override
    public void readPersisted() {
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq()) {
            BlindVoteList persisted = storage.initAndGetPersisted(openBlindVoteList, 20);
            if (persisted != null) {
                openBlindVoteList.clear();
                openBlindVoteList.addAll(persisted.getList());
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We get called from DaoSetup in the parser thread
    public void onAllServicesInitialized() {
        // We get called from stateService in the parser thread
        stateService.registerStateChangeEventsProvider(txBlock -> {
            Set<StateChangeEvent> stateChangeEvents = new HashSet<>();
            Set<BlindVote> toRemove = new HashSet<>();

            openBlindVoteList.stream()
                    .map(blindVote -> {
                        final Optional<StateChangeEvent> optional = getAddBlindVoteEvent(blindVote, txBlock.getHeight());

                        // If we are in the correct block and we add a AddBlindVoteEvent to the state we remove
                        // the blindVote from our list after we have completed iteration.
                        //TODO activate once we persist state
                      /*  if (optional.isPresent())
                            toRemove.add(blindVote);*/

                        return optional;
                    })
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .forEach(stateChangeEvents::add);

            // We remove those blindVotes we have just added as stateChangeEvent.
            toRemove.forEach(blindVote -> {
                if (openBlindVoteList.remove(blindVote))
                    persist();
                else
                    log.warn("We called removeBlindVoteFromList at a blindVote which was not in our list");
            });

            return stateChangeEvents;
        });

        p2PService.getP2PDataStorage().addHashMapChangedListener(new HashMapChangedListener() {
            @Override
            public boolean executeOnUserThread() {
                return false;
            }

            @Override
            public void onAdded(ProtectedStorageEntry entry) {
                onAddedProtectedStorageEntry(entry, true);
            }

            @Override
            public void onRemoved(ProtectedStorageEntry entry) {
                if (entry.getProtectedStoragePayload() instanceof BlindVote)
                    throw new UnsupportedOperationException("Removal of blind vote data is not supported");
            }
        });

        // We apply already existing protectedStorageEntries
        p2PService.getP2PDataStorage().getMap().values()
                .forEach(entry -> onAddedProtectedStorageEntry(entry, false));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onAddedProtectedStorageEntry(ProtectedStorageEntry protectedStorageEntry, boolean storeLocally) {
        final ProtectedStoragePayload protectedStoragePayload = protectedStorageEntry.getProtectedStoragePayload();
        if (protectedStoragePayload instanceof BlindVote) {
            final BlindVote blindVote = (BlindVote) protectedStoragePayload;
            if (openBlindVoteList.stream().noneMatch(e -> e.equals(blindVote))) {
                // For adding a blindVote we need to be before the last block in BREAK2 as in the last block at BREAK2
                // we write our blindVotes to the state.
                final int height = stateService.getChainHeight();
                if (isInToleratedBlockRange(height)) {
                    log.info("We received a BlindVote from the P2P network. BlindVote=" + blindVote);
                    openBlindVoteList.add(blindVote);

                    if (storeLocally) {
                        persist();
                    }
                } else {
                    log.warn("We are not in the tolerated phase anymore and ignore that " +
                                    "blindVote. blindVote={}, height={}", blindVote,
                            height);
                }
            } else {
                log.debug("We have that blindVote already in our list. blindVote={}", blindVote);
            }
        }
    }

    // We add a AddBlindVoteEvent if the tx is already available and blindVote and tx are valid.
    // We only add it after the blindVote phase.
    // We use the last block in the BREAK2 phase to set all blindVote for that cycle.
    // If a blindVote would arrive later it will be ignored.
    private Optional<StateChangeEvent> getAddBlindVoteEvent(BlindVote blindVote, int height) {
        return stateService.getTx(blindVote.getTxId())
                .filter(tx -> isLastToleratedBlock(height))
                .filter(tx -> periodService.isTxInCorrectCycle(tx.getBlockHeight(), height))
                .filter(tx -> periodService.isInPhase(tx.getBlockHeight(), Phase.BLIND_VOTE))
                .filter(tx -> blindVoteValidator.isValid(blindVote))
                .map(tx -> new AddBlindVoteEvent(blindVote, height));
    }

    private boolean isLastToleratedBlock(int height) {
        return height == periodService.getLastBlockOfPhase(height, Phase.BREAK2);
    }

    private boolean isInToleratedBlockRange(int height) {
        return height < periodService.getLastBlockOfPhase(height, Phase.BREAK2);
    }

    private void persist() {
        storage.queueUpForSave();
    }
}
