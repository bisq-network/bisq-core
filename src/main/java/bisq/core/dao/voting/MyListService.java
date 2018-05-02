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

package bisq.core.dao.voting;

import bisq.core.app.BisqEnvironment;
import bisq.core.btc.wallet.TxBroadcastException;
import bisq.core.btc.wallet.TxBroadcastTimeoutException;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.btc.wallet.TxMalleabilityException;
import bisq.core.btc.wallet.WalletsManager;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.period.PeriodService;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.UserThread;
import bisq.common.app.DevEnv;
import bisq.common.crypto.KeyRing;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.proto.persistable.PersistableList;
import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;

import org.bitcoinj.core.Transaction;

import com.google.inject.Inject;

import javafx.beans.value.ChangeListener;

import java.security.PublicKey;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * Base class for publishing a tx and a persistablePayload implementation to the p2p network.
 * Allows removal of the persistablePayload if in the right phase.
 * Maintains myList. Triggers republishing of my myList items at startup if in current cycle and right phase.
 */
@Slf4j
public abstract class MyListService<T extends PersistablePayload, R extends PersistableList<T>> implements PersistedDataHost {
    protected final P2PService p2PService;
    protected final StateService stateService;
    protected final PeriodService periodService;
    protected final WalletsManager walletsManager;
    protected final Storage<R> storage;
    protected final PublicKey signaturePubKey;

    protected final R myList = createMyList();

    protected final ChangeListener<Number> numConnectedPeersListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public MyListService(P2PService p2PService,
                         StateService stateService,
                         PeriodService periodService,
                         WalletsManager walletsManager,
                         Storage<R> storage,
                         KeyRing keyRing) {
        this.p2PService = p2PService;
        this.stateService = stateService;
        this.periodService = periodService;
        this.walletsManager = walletsManager;
        this.storage = storage;

        signaturePubKey = keyRing.getPubKeyRing().getSignaturePubKey();
        numConnectedPeersListener = (observable, oldValue, newValue) -> maybeRePublish();
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
            R persisted = storage.initAndGetPersisted(myList, getListName(), 100);
            if (persisted != null) {
                myList.clear();
                myList.addAll(persisted.getList());
            }
        }
    }

    protected abstract String getListName();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Broadcast tx and publish payload to P2P network
    public void publishTxAndPayload(T payload, Transaction transaction, ResultHandler resultHandler,
                                    ErrorMessageHandler errorMessageHandler) {
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
        // payload stored and broadcasted to the p2p network. The tx might get re-broadcasted at a restart and
        // in worst case if it does not succeed the payload will be ignored anyway.
        // Inconsistently propagated payloads in the p2p network could have potentially worse effects.
        addToP2PNetwork(payload, errorMessageHandler);
    }

    public boolean remove(T payload) {
        if (canRemovePayload(payload, stateService, periodService)) {
            boolean success = p2PService.removeData(createProtectedStoragePayload(payload), true);
            if (!success)
                log.warn("Removal of payload from p2p network failed. payload={}", payload);

            if (myList.remove(payload))
                persist();
            else
                log.warn("We called remove at a payload which was not in our list");

            return success;
        } else {
            final String msg = "remove called with a payload which is outside of the payload phase.";
            DevEnv.logErrorAndThrowIfDevMode(msg);
            return false;
        }
    }

    public boolean isMine(T payload) {
        return listContainsPayload(payload, getList());
    }

    public List<T> getList() {
        return myList.getList();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    abstract protected R createMyList();

    abstract protected void rePublish();

    abstract protected boolean listContainsPayload(T payload, List<T> list);

    abstract protected ProtectedStoragePayload createProtectedStoragePayload(T payload);

    abstract protected boolean canRemovePayload(T payload, StateService stateService, PeriodService periodService);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void addToP2PNetwork(T payload, ErrorMessageHandler errorMessageHandler) {
        final boolean success = addToP2PNetwork(payload);
        if (success) {
            log.debug("We added a payload to the P2P network. payload=" + payload);
        } else {
            final String msg = "Adding of payload to P2P network failed. payload=" + payload;
            log.error(msg);
            errorMessageHandler.handleErrorMessage(msg);
        }

        addToList(payload);
    }

    private void addToList(T payload) {
        if (!listContainsPayload(payload, getList())) {
            myList.add(payload);
            persist();
        }
    }

    protected void maybeRePublish() {
        // Delay a bit for localhost testing to not fail as isBootstrapped is false. Also better for production version
        // to avoid activity peaks at startup
        UserThread.runAfter(() -> {
            if ((p2PService.getNumConnectedPeers().get() > 4 && p2PService.isBootstrapped()) || DevEnv.isDevMode()) {
                p2PService.getNumConnectedPeers().removeListener(numConnectedPeersListener);
                rePublish();
            }
        }, 2);
    }

    protected boolean addToP2PNetwork(T payload) {
        return p2PService.addProtectedStorageEntry(createProtectedStoragePayload(payload), true);
    }

    protected void persist() {
        storage.queueUpForSave();
    }
}
