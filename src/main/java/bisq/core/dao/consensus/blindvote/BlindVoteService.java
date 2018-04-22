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

package bisq.core.dao.consensus.blindvote;

import bisq.core.app.BisqEnvironment;
import bisq.core.dao.consensus.period.PeriodService;
import bisq.core.dao.consensus.period.Phase;
import bisq.core.dao.consensus.state.StateService;
import bisq.core.dao.consensus.state.blockchain.Tx;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.HashMapChangedListener;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;

import javax.inject.Inject;

import java.util.List;
import java.util.Optional;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * We listen to blind votes arriving from the p2p network and maintain the blindVoteList.
 *
 * This class is designed to be used in userThread.
 */
@Slf4j
public class BlindVoteService implements PersistedDataHost {
    private final P2PService p2PService;
    private final PeriodService periodService;
    private final BlindVoteValidator blindVoteValidator;
    private final StateService stateService;
    private final Storage<BlindVoteList> storage;

    @Getter
    private final BlindVoteList blindVoteList = new BlindVoteList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BlindVoteService(P2PService p2PService,
                            PeriodService periodService,
                            BlindVoteValidator blindVoteValidator,
                            StateService stateService,
                            Storage<BlindVoteList> storage) {
        this.p2PService = p2PService;
        this.periodService = periodService;
        this.blindVoteValidator = blindVoteValidator;
        this.stateService = stateService;
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
        if (isBlindVoteValid(blindVote)) {
            blindVoteList.add(blindVote);
            persist();
        }
    }

    public Optional<BlindVote> findBlindVote(String blindVoteTxId) {
        return blindVoteList.stream()
                .filter(blindVote -> blindVote.getTxId().equals(blindVoteTxId))
                .findAny();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void onAddedProtectedStorageEntry(ProtectedStorageEntry protectedStorageEntry, boolean storeLocally) {
        final ProtectedStoragePayload protectedStoragePayload = protectedStorageEntry.getProtectedStoragePayload();
        if (protectedStoragePayload instanceof BlindVotePayload) {
            final BlindVotePayload blindVotePayload = (BlindVotePayload) protectedStoragePayload;
            final BlindVote blindVote = blindVotePayload.getBlindVote();
            if (blindVoteList.stream().noneMatch(e -> e.equals(blindVote))) {
                final int height = periodService.getChainHeight();

                if (isBlindVoteValid(blindVote)) {
                    log.info("We received a BlindVotePayload from the P2P network. BlindVotePayload=" + blindVotePayload);
                    blindVoteList.add(blindVote);

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

    private void persist() {
        storage.queueUpForSave();
    }

    public boolean isBlindVoteValid(BlindVote blindVote) {
        if (blindVoteListContains(blindVote, blindVoteList.getList())) {
            log.debug("We have that blindVote already in our list. blindVote={}", blindVote);
            return false;
        }

        if (!blindVoteValidator.isValid(blindVote)) {
            log.warn("blindVote is invalid. blindVote={}", blindVote);
            return false;
        }

        final String txId = blindVote.getTxId();
        Optional<Tx> optionalTx = stateService.getTx(txId);
        int chainHeight = stateService.getChainHeight();
        final boolean isTxConfirmed = optionalTx.isPresent();
        if (isTxConfirmed) {
            final int txHeight = optionalTx.get().getBlockHeight();
            if (!periodService.isTxInCorrectCycle(txHeight, chainHeight)) {
                log.warn("Tx is not in current cycle. blindVote={}", blindVote);
                return false;
            }
            if (!periodService.isInPhase(txHeight, Phase.BLIND_VOTE)) {
                log.warn("Tx is not in BLIND_VOTE phase. blindVote={}", blindVote);
                return false;
            }
        } else {
            if (!periodService.isInPhase(chainHeight, Phase.BLIND_VOTE)) {
                log.warn("We received an unconfirmed tx and are not in BLIND_VOTE phase anymore. blindVote={}", blindVote);
                return false;
            }
        }
        return true;
    }

    private boolean blindVoteListContains(BlindVote blindVote, List<BlindVote> blindVoteList) {
        return findBlindVoteInList(blindVote, blindVoteList).isPresent();
    }

    private Optional<BlindVote> findBlindVoteInList(BlindVote blindVote, List<BlindVote> blindVoteList) {
        return blindVoteList.stream()
                .filter(vote -> vote.equals(blindVote))
                .findAny();
    }
}
