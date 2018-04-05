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

import javax.crypto.SecretKey;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * Manages list of my votes, creates new MyVote, republished all my active myVotes at startup and applies
 * revealTx ID to MyVote once the reveal tx is published.
 */
@Slf4j
public class MyVoteService implements PersistedDataHost {
    private final PeriodService periodService;
    private final P2PService p2PService;
    private final Storage<MyVoteList> storage;

    // MyVoteList is wrapper for persistence. From outside we access only list inside of wrapper.
    private final MyVoteList myVoteList = new MyVoteList();

    private ChangeListener<Number> numConnectedPeersListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MyVoteService(PeriodService periodService,
                         P2PService p2PService,
                         Storage<MyVoteList> storage) {
        this.periodService = periodService;
        this.p2PService = p2PService;
        this.storage = storage;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted() {
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq()) {
            MyVoteList persisted = storage.initAndGetPersisted(myVoteList, 20);
            if (persisted != null) {
                this.myVoteList.clear();
                this.myVoteList.addAll(persisted.getList());
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        // Republish own active blindVotes once we are well connected
        numConnectedPeersListener = (observable, oldValue, newValue) -> publishMyBlindVotesIfWellConnected();
        p2PService.getNumConnectedPeers().addListener(numConnectedPeersListener);
        publishMyBlindVotesIfWellConnected();
    }

    @SuppressWarnings("EmptyMethod")
    public void shutDown() {
        // TODO keep for later, maybe we get resources to clean up later
    }

    public void applyNewBlindVote(ProposalList proposalList, SecretKey secretKey, BlindVote blindVote) {
        MyVote myVote = new MyVote(proposalList, Encryption.getSecretKeyBytes(secretKey), blindVote);
        log.info("Add new MyVote to myVotesList list.\nMyVote={}" + myVote);
        myVoteList.add(myVote);
        persist();
    }

    public void applyRevealTxId(MyVote myVote, String voteRevealTxId) {
        log.info("apply revealTxId to myVote.\nmyVote={}\nvoteRevealTxId={}", myVote, voteRevealTxId);
        myVote.setRevealTxId(voteRevealTxId);
        persist();
    }

    public List<MyVote> getMyVoteList() {
        return myVoteList.getList();
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
        getMyVoteList().stream()
                .filter(myVote -> periodService.isTxInCurrentCycle(myVote.getTxId()))
                .filter(myVote -> periodService.isTxInPhase(myVote.getTxId(), PeriodService.Phase.BLIND_VOTE))
                .forEach(myVote -> {
                    if (myVote.getRevealTxId() == null) {
                        if (addBlindVoteToP2PNetwork(myVote.getBlindVote())) {
                            log.info("Added BlindVote to P2P network.\nBlindVote={}", myVote.getBlindVote());
                        } else {
                            log.warn("Adding of BlindVote to P2P network failed.\nBlindVote={}", myVote.getBlindVote());
                        }
                    } else {
                        final String msg = "revealTxId must be null at publishMyBlindVotes.\nmyVote=" + myVote;
                        DevEnv.logErrorAndThrowIfDevMode(msg);
                    }
                });
    }

    private boolean addBlindVoteToP2PNetwork(BlindVote blindVote) {
        return p2PService.addProtectedStorageEntry(blindVote, true);
    }

    private void persist() {
        storage.queueUpForSave();
    }
}
