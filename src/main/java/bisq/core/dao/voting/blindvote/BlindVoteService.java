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

import bisq.core.dao.state.BlockListener;
import bisq.core.dao.state.ParseBlockChainListener;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.Block;
import bisq.core.dao.state.period.DaoPhase;
import bisq.core.dao.state.period.PeriodService;
import bisq.core.dao.voting.blindvote.storage.appendonly.BlindVoteAppendOnlyPayload;
import bisq.core.dao.voting.blindvote.storage.appendonly.BlindVoteAppendOnlyStorageService;
import bisq.core.dao.voting.blindvote.storage.protectedstorage.BlindVotePayload;
import bisq.core.dao.voting.blindvote.storage.protectedstorage.BlindVoteStorageService;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.HashMapChangedListener;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreListener;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;
import bisq.network.p2p.storage.persistence.ProtectedDataStoreService;

import javax.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

import java.util.List;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Maintains preliminaryBlindVotes and confirmedBlindVotes. Republishes preliminaryBlindVotes to append-only data store
 * when entering the vote reveal phase.
 */
@Slf4j
public class BlindVoteService implements ParseBlockChainListener, HashMapChangedListener,
        AppendOnlyDataStoreListener, BlockListener {
    private final StateService stateService;
    private final P2PService p2PService;
    private final PeriodService periodService;
    private final BlindVoteValidator blindVoteValidator;

  /*  // BlindVotes we receive in the blind vote phase. They could be theoretically removed (we might add a feature to
    // remove a published blind vote in future)in that phase and that list must not be used for consensus critical code.
    // Another reason why we want to maintain preliminaryBlindVotes is because we want to show items arrived from the
    // p2p network even if they are not confirmed.
    @Getter
    private final List<BlindVote> preliminaryBlindVotes = new ArrayList<>();

    // BlindVotes which got added to the append-only data store at the beginning of the vote reveal phase.
    // They cannot be removed anymore. This list is used for consensus critical code.
    @Getter
    private final List<BlindVote> confirmedBlindVotes = new ArrayList<>();*/

    // BlindVotes we receive in the blindVote phase. They can be removed in that phase and that list must not be used for
    // consensus critical code.
    @Getter
    private final ObservableList<BlindVote> protectedStoreList = FXCollections.observableArrayList();
    @Getter
    private final FilteredList<BlindVote> preliminaryList = new FilteredList<>(protectedStoreList);

    // BlindVotes which got added to the append-only data store at the beginning of the blind vote phase.
    // They cannot be removed anymore. This list is used for consensus critical code.
    @Getter
    private final ObservableList<BlindVoteAppendOnlyPayload> appendOnlyStoreList = FXCollections.observableArrayList();
    @Getter
    private final FilteredList<BlindVoteAppendOnlyPayload> verifiedList = new FilteredList<>(appendOnlyStoreList);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BlindVoteService(StateService stateService,
                            P2PService p2PService,
                            PeriodService periodService,
                            BlindVoteAppendOnlyStorageService blindVoteAppendOnlyStorageService,
                            BlindVoteStorageService blindVoteStorageService,
                            AppendOnlyDataStoreService appendOnlyDataStoreService,
                            ProtectedDataStoreService protectedDataStoreService,
                            BlindVoteValidator blindVoteValidator) {
        this.stateService = stateService;
        this.p2PService = p2PService;
        this.periodService = periodService;
        this.blindVoteValidator = blindVoteValidator;

        appendOnlyDataStoreService.addService(blindVoteAppendOnlyStorageService);
        protectedDataStoreService.addService(blindVoteStorageService);


        stateService.addParseBlockChainListener(this);
        stateService.addBlockListener(this);

        p2PService.addHashSetChangedListener(this);
        p2PService.getP2PDataStorage().addAppendOnlyDataStoreListener(this);

        preliminaryList.setPredicate(proposal -> {
            // We do not validate phase, cycle and confirmation yet.
            if (blindVoteValidator.areDataFieldsValid(proposal)) {
                log.debug("We received a new proposal from the P2P network. Proposal.txId={}, blockHeight={}",
                        proposal.getTxId(), stateService.getChainHeight());
                return true;
            } else {
                log.debug("We received a invalid proposal from the P2P network. Proposal.txId={}, blockHeight={}",
                        proposal.getTxId(), stateService.getChainHeight());
                return false;
            }
        });

        setPredicateForAppendOnlyPayload();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ParseBlockChainListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onComplete() {
        stateService.removeParseBlockChainListener(this);

        // Fill the lists with the data we have collected in out stores.
        fillListFromProtectedStore();
        fillListFromAppendOnlyDataStore();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BlockListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onBlockAdded(Block block) {
        // We need to do that after parsing the block!
        if (block.getHeight() == getTriggerHeight()) {
            publishToAppendOnlyDataStore(block.getHash());

            // We need to update out predicate as we might not get called the onAppendOnlyDataAdded methods in case
            // we have the data already.
            fillListFromAppendOnlyDataStore();
            // In case the list has not changed the filter predicate would not be applied,
            // so we trigger a re-evaluation.
            setPredicateForAppendOnlyPayload();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // HashMapChangedListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAdded(ProtectedStorageEntry entry) {
        onProtectedDataAdded(entry);
    }

    @Override
    public void onRemoved(ProtectedStorageEntry entry) {
        onProtectedDataRemoved(entry);
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
        fillListFromProtectedStore();
    }

    public List<BlindVote> getVerifiedBlindVotes() {
        return verifiedList.stream()
                .map(BlindVoteAppendOnlyPayload::getBlindVote)
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
    private void setPredicateForAppendOnlyPayload() {
        verifiedList.setPredicate(proposalAppendOnlyPayload -> {
            if (!blindVoteValidator.isAppendOnlyPayloadValid(proposalAppendOnlyPayload,
                    getTriggerHeight(), stateService)) {
                log.debug("We received an invalid proposalAppendOnlyPayload. payload={}, blockHeight={}",
                        proposalAppendOnlyPayload, stateService.getChainHeight());
                return false;
            }

            if (blindVoteValidator.isValidAndConfirmed(proposalAppendOnlyPayload.getBlindVote())) {
                return true;
            } else {
                log.debug("We received a invalid append-only proposal from the P2P network. " +
                                "Proposal.txId={}, blockHeight={}",
                        proposalAppendOnlyPayload.getBlindVote().getTxId(), stateService.getChainHeight());
                return false;
            }
        });
    } // The blockHeight when we do the publishing of the proposals to the append only data store

    private void fillListFromProtectedStore() {
        p2PService.getDataMap().values().forEach(this::onProtectedDataAdded);
    }

    private void fillListFromAppendOnlyDataStore() {
        p2PService.getP2PDataStorage().getAppendOnlyDataStoreMap().values().forEach(this::onAppendOnlyDataAdded);
    }

    private void publishToAppendOnlyDataStore(String blockHash) {
        preliminaryList.stream()
                .filter(blindVoteValidator::isValidAndConfirmed)
                .map(blindVote -> new BlindVoteAppendOnlyPayload(blindVote, blockHash))
                .forEach(appendOnlyPayload -> {
                    boolean success = p2PService.addPersistableNetworkPayload(appendOnlyPayload, true);
                    if (!success)
                        log.warn("publishToAppendOnlyDataStore failed for blindVote " + appendOnlyPayload.getBlindVote());
                });
    }

    private void onProtectedDataAdded(ProtectedStorageEntry entry) {
        final ProtectedStoragePayload protectedStoragePayload = entry.getProtectedStoragePayload();
        if (protectedStoragePayload instanceof BlindVotePayload) {
            final BlindVote blindVote = ((BlindVotePayload) protectedStoragePayload).getBlindVote();
            if (!protectedStoreList.contains(blindVote))
                protectedStoreList.add(blindVote);
        }
    }

    private void onProtectedDataRemoved(ProtectedStorageEntry entry) {
        final ProtectedStoragePayload protectedStoragePayload = entry.getProtectedStoragePayload();
        if (protectedStoragePayload instanceof BlindVotePayload) {
            final BlindVote blindVote = ((BlindVotePayload) protectedStoragePayload).getBlindVote();
            if (protectedStoreList.contains(blindVote))
                protectedStoreList.remove(blindVote);
        }
    }

    private void onAppendOnlyDataAdded(PersistableNetworkPayload persistableNetworkPayload) {
        if (persistableNetworkPayload instanceof BlindVoteAppendOnlyPayload) {
            BlindVoteAppendOnlyPayload blindVoteAppendOnlyPayload = (BlindVoteAppendOnlyPayload) persistableNetworkPayload;
            if (!appendOnlyStoreList.contains(blindVoteAppendOnlyPayload))
                appendOnlyStoreList.add(blindVoteAppendOnlyPayload);
        }
    }
}
