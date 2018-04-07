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

package bisq.core.dao.vote;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.dao.blockchain.BsqBlockChain;
import bisq.core.dao.blockchain.ReadableBsqBlockChain;
import bisq.core.dao.blockchain.vo.BsqBlock;
import bisq.core.dao.blockchain.vo.Tx;
import bisq.core.dao.vote.proposal.ValidationException;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.HashMapChangedListener;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.crypto.KeyRing;
import bisq.common.proto.persistable.PersistedDataHost;

import org.bitcoinj.core.TransactionConfidence;

import javafx.beans.value.ChangeListener;

import java.security.PublicKey;

import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class BaseService implements PersistedDataHost, HashMapChangedListener, BsqBlockChain.Listener {
    protected final P2PService p2PService;
    protected final WalletsManager walletsManager;
    protected final BsqWalletService bsqWalletService;
    protected final PeriodService periodService;
    protected final ReadableBsqBlockChain readableBsqBlockChain;
    protected final PublicKey signaturePubKey;
    protected ChangeListener<Number> numConnectedPeersListener;

    public BaseService(P2PService p2PService,
                       WalletsManager walletsManager,
                       BsqWalletService bsqWalletService,
                       PeriodService periodService,
                       ReadableBsqBlockChain readableBsqBlockChain,
                       KeyRing keyRing) {
        this.p2PService = p2PService;
        this.walletsManager = walletsManager;
        this.bsqWalletService = bsqWalletService;
        this.periodService = periodService;
        this.readableBsqBlockChain = readableBsqBlockChain;

        signaturePubKey = keyRing.getPubKeyRing().getSignaturePubKey();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        p2PService.addHashSetChangedListener(this);
        p2PService.getP2PDataStorage().getMap().values()
                .forEach(entry -> onProtectedStorageEntry(entry, false));

        // Republish own active proposals once we are well connected
        numConnectedPeersListener = (observable, oldValue, newValue) -> rePublishWhenWellConnected();
        p2PService.getNumConnectedPeers().addListener(numConnectedPeersListener);
        rePublishWhenWellConnected();

        readableBsqBlockChain.addListener(this);
    }

    public void shutDown() {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public abstract void readPersisted();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // BsqBlockChain.Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onBlockAdded(BsqBlock bsqBlock) {
        upDatePredicate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // HashMapChangedListener
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAdded(ProtectedStorageEntry entry) {
        onProtectedStorageEntry(entry, true);
    }

    @Override
    abstract public void onRemoved(ProtectedStorageEntry data);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    abstract protected void upDatePredicate();

    abstract protected void onProtectedStorageEntry(ProtectedStorageEntry entry, boolean isDataOwner);

    protected boolean isUnconfirmed(String txId) {
        final TransactionConfidence confidenceForTxId = bsqWalletService.getConfidenceForTxId(txId);
        return confidenceForTxId != null && confidenceForTxId.getConfidenceType() == TransactionConfidence.ConfidenceType.PENDING;
    }

    public boolean isMine(ProtectedStoragePayload protectedStoragePayload) {
        return signaturePubKey.equals(protectedStoragePayload.getOwnerPubKey());
    }

    protected boolean isInPhaseOrUnconfirmed(String txId, PeriodService.Phase phase) {
        return readableBsqBlockChain.getTxMap().get(txId) == null ||
                (periodService.isTxInPhase(txId, phase) &&
                        periodService.isTxInCurrentCycle(txId));
    }

    protected boolean isProposalValid(ValidationCandidate validationCandidate) {
        final String txId = validationCandidate.getTxId();

        Optional<Tx> optionalTx = readableBsqBlockChain.getTx(txId);
        if (optionalTx.isPresent() && periodService.isTxInCurrentCycle(txId)) {
            final Tx tx = optionalTx.get();
            try {
                validationCandidate.validateDataFields();
                validationCandidate.validateHashOfOpReturnData(tx);
                // All other tx validation is done in parser, so if type is correct we know it's a correct validationCandidate tx
                validationCandidate.validateCorrectTxType(tx);
                return true;
            } catch (ValidationException e) {
                log.warn("Validation failed. txId={}, validationCandidate={}, validationException={}", txId, validationCandidate, e.toString());
                return false;
            }
        } else {
            log.debug("Validation failed. txId={},optionalTx.isPresent()={}, isTxInCurrentCycle={}",
                    txId, optionalTx.isPresent(), periodService.isTxInCurrentCycle(txId));
            return false;
        }
    }

    protected boolean addToP2PNetwork(ProtectedStoragePayload protectedStoragePayload) {
        return p2PService.addProtectedStorageEntry(protectedStoragePayload, true);
    }

    abstract protected List<ProtectedStoragePayload> getProtectedStoragePayloadList();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void rePublishWhenWellConnected() {
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
        getProtectedStoragePayloadList().stream()
                .filter(this::isMine)
                .forEach(protectedStoragePayload -> {
                    if (!addToP2PNetwork(protectedStoragePayload))
                        log.warn("Adding of protectedStoragePayload to P2P network failed.\nprotectedStoragePayload=" + protectedStoragePayload);
                });
    }
}
