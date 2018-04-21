/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.presentation.ballot;

import bisq.core.app.BisqEnvironment;
import bisq.core.btc.wallet.TxBroadcastException;
import bisq.core.btc.wallet.TxBroadcastTimeoutException;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.btc.wallet.TxMalleabilityException;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.dao.consensus.ballot.Ballot;
import bisq.core.dao.consensus.ballot.BallotList;
import bisq.core.dao.consensus.proposal.Proposal;
import bisq.core.dao.consensus.proposal.ProposalPayload;
import bisq.core.dao.presentation.PresentationService;

import bisq.network.p2p.P2PService;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.crypto.KeyRing;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;

import org.bitcoinj.core.Transaction;

import com.google.inject.Inject;

import javafx.beans.value.ChangeListener;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.security.PublicKey;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Persists, publishes and republishes own proposals.
 *
 * Designed for user thread.
 */
@Slf4j
public class MyBallotListService implements PersistedDataHost, PresentationService {
    private final P2PService p2PService;
    private final WalletsManager walletsManager;
    private final BallotListService ballotListService;
    private final Storage<BallotList> storage;
    private final PublicKey signaturePubKey;
    private ChangeListener<Number> numConnectedPeersListener;

    @Getter
    private final ObservableList<Ballot> myObservableBallotList = FXCollections.observableArrayList();
    private final BallotList myBallotList = new BallotList(myObservableBallotList);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MyBallotListService(P2PService p2PService,
                               WalletsManager walletsManager,
                               BallotListService ballotListService,
                               KeyRing keyRing,
                               Storage<BallotList> storage) {
        this.p2PService = p2PService;
        this.walletsManager = walletsManager;
        this.ballotListService = ballotListService;
        signaturePubKey = keyRing.getPubKeyRing().getSignaturePubKey();
        this.storage = storage;

        numConnectedPeersListener = (observable, oldValue, newValue) -> maybeRePublish();
        p2PService.getNumConnectedPeers().addListener(numConnectedPeersListener);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
        maybeRePublish();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted() {
        if (BisqEnvironment.isDAOActivatedAndBaseCurrencySupportingBsq()) {
            BallotList persisted = storage.initAndGetPersisted(myBallotList, "MyBallotList", 20);
            if (persisted != null) {
                myObservableBallotList.clear();
                myObservableBallotList.addAll(persisted.getList());
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void publishProposalAndStoreBallot(Ballot ballot, Transaction transaction, ResultHandler resultHandler,
                                              ErrorMessageHandler errorMessageHandler) {
        Proposal proposal = ballot.getProposal();
        walletsManager.publishAndCommitBsqTx(transaction, new TxBroadcaster.Callback() {
            @Override
            public void onSuccess(Transaction transaction) {
                if (addToP2PNetwork(proposal)) {
                    log.info("We added a proposal to the P2P network. Proposal.uid=" + proposal.getUid());

                    // Store to list
                    if (!ballotListService.ballotListContainsProposal(ballot.getProposal(), myObservableBallotList)) {
                        myObservableBallotList.add(ballot);
                        persist();
                    }

                    resultHandler.handleResult();
                } else {
                    final String msg = "Adding of proposal to P2P network failed.\n" +
                            "proposal=" + proposal;
                    log.error(msg);
                    errorMessageHandler.handleErrorMessage(msg);
                }
            }

            @Override
            public void onTimeout(TxBroadcastTimeoutException exception) {
                // TODO handle
                errorMessageHandler.handleErrorMessage(exception.getMessage());
            }

            @Override
            public void onTxMalleability(TxMalleabilityException exception) {
                // TODO handle
                errorMessageHandler.handleErrorMessage(exception.getMessage());
            }

            @Override
            public void onFailure(TxBroadcastException exception) {
                // TODO handle
                errorMessageHandler.handleErrorMessage(exception.getMessage());
            }
        });
    }

    public boolean removeProposal(Ballot ballot) {
        final Proposal proposal = ballot.getProposal();
        if (ballotListService.canRemoveProposal(proposal)) {
            boolean success = p2PService.removeData(createProposalPayload(proposal), true);
            if (success) {
                if (myObservableBallotList.remove(ballot))
                    persist();
                else
                    log.warn("We called removeProposalFromList at a ballot which was not in our list");
            } else {
                log.warn("Removal of ballot from p2p network failed. ballot={}", ballot);
            }
            return success;
        } else {
            final String msg = "removeProposal called with a Ballot which is outside of the Ballot phase.";
            DevEnv.logErrorAndThrowIfDevMode(msg);
            return false;
        }
    }

    public boolean isMine(Proposal proposal) {
        return ballotListService.ballotListContainsProposal(proposal, myObservableBallotList);
    }

    public void persist() {
        storage.queueUpForSave();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void maybeRePublish() {
        // Delay a bit for localhost testing to not fail as isBootstrapped is false. Also better for production version
        // to avoid activity peaks at startup
        UserThread.runAfter(() -> {
            if ((p2PService.getNumConnectedPeers().get() > 4 && p2PService.isBootstrapped()) || DevEnv.isDevMode()) {
                p2PService.getNumConnectedPeers().removeListener(numConnectedPeersListener);
                rePublish();
            }
        }, 2);
    }

    private void rePublish() {
        myObservableBallotList.stream()
                .map(Ballot::getProposal)
                .forEach(proposal -> {
                    if (!addToP2PNetwork(proposal))
                        log.warn("Adding of proposal to P2P network failed.\nproposal=" + proposal);
                });
    }

    private boolean addToP2PNetwork(Proposal proposal) {
        return p2PService.addProtectedStorageEntry(createProposalPayload(proposal), true);
    }

    private ProposalPayload createProposalPayload(Proposal proposal) {
        return new ProposalPayload(proposal, signaturePubKey);
    }
}
