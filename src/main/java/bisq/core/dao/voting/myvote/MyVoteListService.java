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

package bisq.core.dao.voting.myvote;

import bisq.core.app.BisqEnvironment;
import bisq.core.dao.state.StateService;
import bisq.core.dao.voting.ballot.BallotList;
import bisq.core.dao.voting.blindvote.BlindVote;
import bisq.core.dao.voting.blindvote.BlindVotePayload;
import bisq.core.dao.voting.blindvote.BlindVoteValidator;

import bisq.network.p2p.P2PService;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.crypto.Encryption;
import bisq.common.crypto.KeyRing;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;

import javax.inject.Inject;

import javafx.beans.value.ChangeListener;

import javax.crypto.SecretKey;

import java.security.PublicKey;

import lombok.extern.slf4j.Slf4j;

/**
 * Maintains myVoteList. Republishes all my active myVotes at startup.
 */
@Slf4j
public class MyVoteListService implements PersistedDataHost {
    private final StateService stateService;
    private final BlindVoteValidator blindVoteValidator;
    private final P2PService p2PService;
    private final Storage<MyVoteList> storage;
    private final PublicKey signaturePubKey;

    private final MyVoteList myVoteList = new MyVoteList();
    private final ChangeListener<Number> numConnectedPeersListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MyVoteListService(StateService stateService,
                             BlindVoteValidator blindVoteValidator,
                             P2PService p2PService,
                             KeyRing keyRing,
                             Storage<MyVoteList> storage) {
        this.stateService = stateService;
        this.blindVoteValidator = blindVoteValidator;
        this.p2PService = p2PService;
        this.storage = storage;

        signaturePubKey = keyRing.getPubKeyRing().getSignaturePubKey();

        numConnectedPeersListener = (observable, oldValue, newValue) -> rePublishMyBlindVotesIfWellConnected();
        p2PService.getNumConnectedPeers().addListener(numConnectedPeersListener);
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

    public void start() {
        rePublishMyBlindVotesIfWellConnected();
    }

    public void createAndAddNewMyVote(BallotList sortedBallotListForCycle, SecretKey secretKey, BlindVote blindVote) {
        final byte[] secretKeyBytes = Encryption.getSecretKeyBytes(secretKey);
        MyVote myVote = new MyVote(stateService.getChainHeight(), sortedBallotListForCycle, secretKeyBytes, blindVote);
        log.info("Add new MyVote to myVotesList list.\nMyVote=" + myVote);
        myVoteList.add(myVote);
        persist();
    }

    public void applyRevealTxId(MyVote myVote, String voteRevealTxId) {
        myVote.setRevealTxId(voteRevealTxId);
        log.info("Applied revealTxId to myVote.\nmyVote={}\nvoteRevealTxId={}", myVote, voteRevealTxId);
        persist();
    }

    public MyVoteList getMyVoteList() {
        return myVoteList;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////


    private void rePublishMyBlindVotesIfWellConnected() {
        // Delay a bit for localhost testing to not fail as isBootstrapped is false. Also better for production version
        // to avoid activity peaks at startup
        UserThread.runAfter(() -> {
            if ((p2PService.getNumConnectedPeers().get() > 4 && p2PService.isBootstrapped()) || DevEnv.isDevMode()) {
                p2PService.getNumConnectedPeers().removeListener(numConnectedPeersListener);
                publishMyBlindVotes();
            }
        }, 2);
    }

    // Only republish if valid as well that reveal tx is not yet set.
    private void publishMyBlindVotes() {
        getMyVoteList().stream()
                .filter(myVote -> blindVoteValidator.isValid(myVote.getBlindVote()))
                .forEach(myVote -> {
                    if (myVote.getRevealTxId() == null) {
                        BlindVotePayload blindVotePayload = new BlindVotePayload(myVote.getBlindVote(), signaturePubKey);
                        if (addBlindVoteToP2PNetwork(blindVotePayload)) {
                            log.info("Added BlindVotePayload to P2P network.\nBlindVotePayload={}", myVote.getBlindVote());
                        } else {
                            log.warn("Adding of BlindVotePayload to P2P network failed.\nBlindVotePayload={}", myVote.getBlindVote());
                        }
                    } else {
                        final String msg = "revealTxId have to be null at publishMyBlindVotes.\nmyVote=" + myVote;
                        log.error(msg);
                        //DevEnv.logErrorAndThrowIfDevMode(msg);
                    }
                });
    }

    private boolean addBlindVoteToP2PNetwork(BlindVotePayload blindVotePayload) {
        return p2PService.addProtectedStorageEntry(blindVotePayload, true);
    }

    private void persist() {
        storage.queueUpForSave();
    }
}
