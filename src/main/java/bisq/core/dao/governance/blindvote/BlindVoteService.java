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

package bisq.core.dao.governance.blindvote;

import bisq.core.dao.governance.blindvote.storage.BlindVotePayload;
import bisq.core.dao.governance.blindvote.storage.BlindVoteStorageService;
import bisq.core.dao.state.BsqStateListener;
import bisq.core.dao.state.BsqStateService;
import bisq.core.dao.state.blockchain.Block;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreListener;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;

import javax.inject.Inject;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * Listens for new BlindVotePayload and adds it to appendOnlyStoreList.
 */
@Slf4j
public class BlindVoteService implements AppendOnlyDataStoreListener, BsqStateListener {
    private final P2PService p2PService;
    private final BlindVoteValidator blindVoteValidator;

    private final ObservableList<BlindVotePayload> appendOnlyStoreList = FXCollections.observableArrayList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public BlindVoteService(BsqStateService bsqStateService,
                            P2PService p2PService,
                            BlindVoteStorageService blindVoteStorageService,
                            AppendOnlyDataStoreService appendOnlyDataStoreService,
                            BlindVoteValidator blindVoteValidator) {
        this.p2PService = p2PService;
        this.blindVoteValidator = blindVoteValidator;

        appendOnlyDataStoreService.addService(blindVoteStorageService);
        bsqStateService.addBsqStateListener(this);
        p2PService.getP2PDataStorage().addAppendOnlyDataStoreListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BsqStateListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onNewBlockHeight(int blockHeight) {
    }

    @Override
    public void onParseTxsComplete(Block block) {
    }

    @Override
    public void onParseBlockChainComplete() {
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
        fillListFromAppendOnlyDataStore();
    }

    public List<BlindVote> getVerifiedBlindVotes() {
        return appendOnlyStoreList.stream()
                .filter(blindVotePayload -> blindVoteValidator.isValidAndConfirmed(blindVotePayload.getBlindVote()))
                .map(BlindVotePayload::getBlindVote)
                .collect(Collectors.toList());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void fillListFromAppendOnlyDataStore() {
        p2PService.getP2PDataStorage().getAppendOnlyDataStoreMap().values().forEach(this::onAppendOnlyDataAdded);
    }

    private void onAppendOnlyDataAdded(PersistableNetworkPayload persistableNetworkPayload) {
        if (persistableNetworkPayload instanceof BlindVotePayload) {
            BlindVotePayload blindVotePayload = (BlindVotePayload) persistableNetworkPayload;

            // TODO verify? blindVoteValidator.isValidAndConfirmed(blindVotePayload.getBlindVote())
            if (!appendOnlyStoreList.contains(blindVotePayload)) {
                appendOnlyStoreList.add(blindVotePayload);
                log.info("We received a blindVotePayload. blindVoteTxId={}", blindVotePayload.getBlindVote().getTxId());
            }
        }
    }
}
