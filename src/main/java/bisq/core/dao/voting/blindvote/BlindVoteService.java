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
import bisq.core.dao.state.ChainHeightListener;
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

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Maintains preliminaryBlindVotes and confirmedBlindVotes. Republishes preliminaryBlindVotes to append-only data store
 * when entering the vote reveal phase.
 */
@Slf4j
public class BlindVoteService implements ChainHeightListener, HashMapChangedListener, AppendOnlyDataStoreListener,
        BlockListener {
    private final P2PService p2PService;
    private PeriodService periodService;
    private BlindVoteValidator blindVoteValidator;

    // BlindVotes we receive in the blind vote phase. They could be theoretically removed (we might add a feature to
    // remove a published blind vote in future)in that phase and that list must not be used for consensus critical code.
    // Another reason why we want to maintain preliminaryBlindVotes is because we want to show items arrived from the
    // p2p network even if they are not confirmed.
    @Getter
    private final List<BlindVote> preliminaryBlindVotes = new ArrayList<>();

    // BlindVotes which got added to the append-only data store at the beginning of the vote reveal phase.
    // They cannot be removed anymore. This list is used for consensus critical code.
    @Getter
    private final List<BlindVote> confirmedBlindVotes = new ArrayList<>();


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
        this.p2PService = p2PService;
        this.periodService = periodService;
        this.blindVoteValidator = blindVoteValidator;

        appendOnlyDataStoreService.addService(blindVoteAppendOnlyStorageService);
        protectedDataStoreService.addService(blindVoteStorageService);

        stateService.addChainHeightListener(this);
        p2PService.addHashSetChangedListener(this);
        p2PService.getP2PDataStorage().addAppendOnlyDataStoreListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ChainHeightListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onChainHeightChanged(int blockHeight) {
        // When we enter the vote reveal phase we re-publish all blindVotes we have received from the p2p network to the
        // append only data store.
        // From now on we will access blindVotes only from that data store.
        if (periodService.getFirstBlockOfPhase(blockHeight, DaoPhase.Phase.VOTE_REVEAL) == blockHeight) {
            rePublishBlindVotesToAppendOnlyDataStore();

            // At republish we get set out local map synchronously so we can use that to fill our confirmed list.
            fillConfirmedBlindVotes();
        } else if (periodService.isFirstBlockInCycle()) {
            // Cycle has changed, we reset the lists.
            fillConfirmedBlindVotes();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BlockListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onBlockAdded(Block block) {
        // We use the block listener here as we need to get the tx fully parsed when checking if the proposal is valid
        fillPreliminaryBlindVotes();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // HashMapChangedListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAdded(ProtectedStorageEntry entry) {
        onAddedProtectedData(entry);
    }

    @Override
    public void onRemoved(ProtectedStorageEntry entry) {
        onRemovedProtectedData(entry);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // AppendOnlyDataStoreListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAdded(PersistableNetworkPayload payload) {
        // We only accept data from the network if we are in the correct phase.
        // If we would accept data during the vote reveal phase a malicious node could withhold
        // data and send it at the end of the vote reveal phase. Voters who have already revealed their vote would
        // miss that data and it could lead to the situation that they will end up in a minority data view
        // rendering their vote invalid. That attack i snot very likely as the attacker would need to have a lot of
        // stake to have influence. But he could still disrupt other voters with higher stake and create more
        // fragmented data views.
        if (periodService.isInPhase(periodService.getChainHeight(), DaoPhase.Phase.BLIND_VOTE))
            onAddedAppendOnlyData(payload);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
        fillPreliminaryBlindVotes();
        fillConfirmedBlindVotes();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void fillPreliminaryBlindVotes() {
        preliminaryBlindVotes.clear();
        p2PService.getDataMap().values().forEach(this::onAddedProtectedData);
    }

    private void fillConfirmedBlindVotes() {
        confirmedBlindVotes.clear();
        p2PService.getP2PDataStorage().getAppendOnlyDataStoreMap().values().forEach(this::onAddedAppendOnlyData);
    }

    private void rePublishBlindVotesToAppendOnlyDataStore() {
        preliminaryBlindVotes.stream()
                .filter(blindVoteValidator::isValidAndConfirmed)
                .map(BlindVoteAppendOnlyPayload::new)
                .forEach(appendOnlyPayload -> {
                    boolean success = p2PService.addPersistableNetworkPayload(appendOnlyPayload, true);
                    if (!success)
                        log.warn("rePublishBlindVotesToAppendOnlyDataStore failed for blindVote " +
                                appendOnlyPayload.getBlindVote());
                });
    }

    private void onAddedProtectedData(ProtectedStorageEntry entry) {
        final ProtectedStoragePayload protectedStoragePayload = entry.getProtectedStoragePayload();
        if (protectedStoragePayload instanceof BlindVotePayload) {
            final BlindVote blindVote = ((BlindVotePayload) protectedStoragePayload).getBlindVote();
            if (!BlindVoteUtils.containsBlindVote(blindVote, preliminaryBlindVotes)) {
                if (blindVoteValidator.isValidAndConfirmed(blindVote)) {
                    log.info("We received a new blindVote from the P2P network. BlindVote.txId={}", blindVote.getTxId());
                    preliminaryBlindVotes.add(blindVote);
                } else {
                    log.warn("We received a invalid blindVote from the P2P network. BlindVote={}", blindVote);

                }
            } else {
                log.debug("We received a new blindVote from the P2P network but we have it already in out list. " +
                        "We ignore that blindVote. BlindVote.txId={}", blindVote.getTxId());
            }
        }
    }

    private void onRemovedProtectedData(ProtectedStorageEntry entry) {
        if (entry.getProtectedStoragePayload() instanceof BlindVotePayload) {
            String msg = "We do not support removal of blind votes. ProtectedStoragePayload=" +
                    entry.getProtectedStoragePayload();
            log.error(msg);
            throw new UnsupportedOperationException(msg);
        }
    }

    private void onAddedAppendOnlyData(PersistableNetworkPayload payload) {
        if (payload instanceof BlindVoteAppendOnlyPayload) {
            final BlindVote blindVote = ((BlindVoteAppendOnlyPayload) payload).getBlindVote();
            if (!BlindVoteUtils.containsBlindVote(blindVote, confirmedBlindVotes)) {
                if (blindVoteValidator.isValidAndConfirmed(blindVote)) {
                    log.info("We received a new append-only blindVote from the P2P network. BlindVote.txId={}",
                            blindVote.getTxId());
                    confirmedBlindVotes.add(blindVote);
                } else {
                    log.warn("We received a invalid append-only blindVote from the P2P network. BlindVote={}", blindVote);
                }
            } else {
                log.debug("We received a new append-only blindVote from the P2P network but we have it already in out list. " +
                        "We ignore that blindVote. BlindVote.txId={}", blindVote.getTxId());
            }
        }
    }
}
