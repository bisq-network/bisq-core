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

package bisq.core.dao.voting.ballot.proposal;

import bisq.core.btc.wallet.TxBroadcastException;
import bisq.core.btc.wallet.TxBroadcastTimeoutException;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.btc.wallet.TxMalleabilityException;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.period.PeriodService;
import bisq.core.dao.voting.ballot.Ballot;
import bisq.core.dao.voting.ballot.BallotUtils;
import bisq.core.dao.voting.ballot.MyBallotListService;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.AppendOnlyDataStoreService;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.crypto.KeyRing;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import org.bitcoinj.core.Transaction;

import com.google.inject.Inject;

import javafx.beans.value.ChangeListener;

import java.security.PublicKey;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * Creates proposal tx and broadcasts proposal to network. Republish own proposals at startup.
 */
@Slf4j
public class ProposalService {
    private final P2PService p2PService;
    private final WalletsManager walletsManager;
    private final PeriodService periodService;
    private final StateService stateService;
    private final MyBallotListService myBallotListService;
    private final PublicKey signaturePubKey;

    private ChangeListener<Number> numConnectedPeersListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public ProposalService(P2PService p2PService,
                           WalletsManager walletsManager,
                           PeriodService periodService,
                           ProposalStorageService proposalStorageService,
                           AppendOnlyDataStoreService appendOnlyDataStoreService,
                           StateService stateService,
                           MyBallotListService myBallotListService,
                           KeyRing keyRing) {
        this.p2PService = p2PService;
        this.walletsManager = walletsManager;
        this.periodService = periodService;
        this.stateService = stateService;
        this.myBallotListService = myBallotListService;
        signaturePubKey = keyRing.getPubKeyRing().getSignaturePubKey();

        numConnectedPeersListener = (observable, oldValue, newValue) -> maybeRePublish();
        p2PService.getNumConnectedPeers().addListener(numConnectedPeersListener);

        appendOnlyDataStoreService.addService(proposalStorageService);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start() {
        maybeRePublish();
    }

    // Broadcast proposalTx and publish proposal to P2P network
    public void publishBallot(Ballot ballot, Transaction transaction, ResultHandler resultHandler,
                              ErrorMessageHandler errorMessageHandler) {
        Proposal proposal = ballot.getProposal();
        walletsManager.publishAndCommitBsqTx(transaction, new TxBroadcaster.Callback() {
            @Override
            public void onSuccess(Transaction transaction) {
                resultHandler.handleResult();
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

        // We prefer to not wait for the tx broadcast as if the tx broadcast would fail we still prefer to have our
        // proposal stored and broadcasted to the p2p network. The tx might get re-broadcasted at a restart and
        // in worst case if it does not succeed the proposal will be ignored anyway.
        // Inconsistently propagated proposals in the p2p network could have potentially worse effects.
        final boolean success = addToP2PNetwork(proposal);
        if (success) {
            log.info("We added a proposal to the P2P network. Proposal.uid=" + proposal.getUid());
        } else {
            final String msg = "Adding of proposal to P2P network failed. proposal=" + proposal;
            log.error(msg);
            errorMessageHandler.handleErrorMessage(msg);
        }

        myBallotListService.storeBallot(ballot);
    }

    public boolean removeMyProposal(Ballot ballot) {
        final Proposal proposal = ballot.getProposal();
        if (BallotUtils.canRemoveProposal(proposal, stateService, periodService)) {
            boolean success = p2PService.removeData(createProposalPayload(proposal), true);
            if (!success)
                log.warn("Removal of ballot from p2p network failed. ballot={}", ballot);

            return success;
        } else {
            final String msg = "removeProposal called with a Ballot which is outside of the Ballot phase.";
            DevEnv.logErrorAndThrowIfDevMode(msg);
            return false;
        }
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
                rePublishProposals(myBallotListService.getProposals());
            }
        }, 2);
    }

    private void rePublishProposals(List<Proposal> proposals) {
        proposals.forEach(proposal -> {
            if (!addToP2PNetwork(proposal))
                log.warn("Adding of proposal to P2P network failed.\nproposal=" + proposal);
        });
    }

    private boolean addToP2PNetwork(Proposal proposal) {
        return p2PService.addProtectedStorageEntry(createProposalPayload(proposal), true);
    }

    private ProposalProtectedStoragePayload createProposalPayload(Proposal proposal) {
        return new ProposalProtectedStoragePayload(proposal, signaturePubKey);
    }
}
