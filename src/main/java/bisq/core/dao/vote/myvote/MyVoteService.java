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

package bisq.core.dao.vote.myvote;

import bisq.core.app.BisqEnvironment;
import bisq.core.dao.vote.PeriodService;
import bisq.core.dao.vote.blindvote.BlindVote;
import bisq.core.dao.vote.proposal.ProposalList;

import bisq.network.p2p.P2PService;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.crypto.Encryption;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;

import javax.inject.Inject;

import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import javax.crypto.SecretKey;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages my votes.
 */
@Slf4j
public class MyVoteService implements PersistedDataHost {
    private final PeriodService periodService;
    private final P2PService p2PService;
    private final Storage<MyVoteList> myVoteListStorage;

    @Getter
    private final ObservableList<MyVote> myVotesList = FXCollections.observableArrayList();
    private ChangeListener<Number> numConnectedPeersListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MyVoteService(PeriodService periodService,
                         P2PService p2PService,
                         Storage<MyVoteList> myVoteListStorage) {
        this.periodService = periodService;
        this.p2PService = p2PService;
        this.myVoteListStorage = myVoteListStorage;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted() {
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq()) {
            MyVoteList persisted = myVoteListStorage.initAndGetPersistedWithFileName("MyVoteList", 100);
            if (persisted != null) {
                this.myVotesList.clear();
                this.myVotesList.addAll(persisted.getList());
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        // Republish own active blindVotes once we are well connected
        numConnectedPeersListener = (observable, oldValue, newValue) -> {
            publishMyBlindVotesIfWellConnected();
        };
        p2PService.getNumConnectedPeers().addListener(numConnectedPeersListener);
        publishMyBlindVotesIfWellConnected();
    }

    @SuppressWarnings("EmptyMethod")
    public void shutDown() {
        // TODO keep for later, maybe we get resources to clean up later
    }

    public void addNewMyVote(ProposalList proposalList, SecretKey secretKey, BlindVote blindVote) {
        MyVote myVote = new MyVote(proposalList, Encryption.getSecretKeyBytes(secretKey), blindVote);
        myVotesList.add(myVote);
        persistMyVoteList();
    }

    public void applyRevealTxId(MyVote myVote, String voteRevealTxId) {
        log.info("applyStateChange myVote={}, voteRevealTxId={}", myVote, voteRevealTxId);
        myVote.setRevealTxId(voteRevealTxId);
        persistMyVoteList();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void publishMyBlindVotesIfWellConnected() {
        // Delay a bit for localhost testing to not fail as isBootstrapped is false. Also better for production version
        // to avoid activity peaks at startup
        UserThread.runAfter(() -> {
            if ((p2PService.getNumConnectedPeers().get() > 4 && p2PService.isBootstrapped()) || DevEnv.isDevMode()) {
                p2PService.getNumConnectedPeers().removeListener(numConnectedPeersListener);
                publishMyBlindVotes();
            }
        }, 2);
    }

    private void publishMyBlindVotes() {
        myVotesList.stream()
                .filter(vote -> periodService.isTxInPhase(vote.getTxId(), PeriodService.Phase.BLIND_VOTE))
                .forEach(vote -> addBlindVoteToP2PNetwork(vote.getBlindVote()));
    }

    private boolean addBlindVoteToP2PNetwork(BlindVote blindVote) {
        return p2PService.addProtectedStorageEntry(blindVote, true);
    }

    private void persistMyVoteList() {
        myVoteListStorage.queueUpForSave(new MyVoteList(myVotesList), 100);
    }
}
