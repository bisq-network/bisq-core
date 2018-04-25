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

package bisq.core.dao.node;

import bisq.core.dao.state.SnapshotManager;
import bisq.core.dao.state.StateService;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.P2PServiceListener;

import bisq.common.handlers.ErrorMessageHandler;

import com.google.inject.Inject;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Base class for the lite and full node.
 * <p>
 * We are in UserThread context. We get callbacks from threaded classes which are already mapped to the UserThread.
 */
@Slf4j
public abstract class BsqNode {

    protected final P2PService p2PService;
    protected final StateService stateService;
    private final String genesisTxId;
    private final int genesisBlockHeight;
    private final SnapshotManager snapshotManager;
    @Getter
    protected boolean parseBlockchainComplete;
    protected boolean p2pNetworkReady;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public BsqNode(StateService stateService,
                   SnapshotManager snapshotManager,
                   P2PService p2PService) {

        this.p2PService = p2PService;
        this.stateService = stateService;

        genesisTxId = stateService.getGenesisTxId();
        genesisBlockHeight = stateService.getGenesisBlockHeight();
        this.snapshotManager = snapshotManager;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public abstract void start(ErrorMessageHandler errorMessageHandler);

    public abstract void shutDown();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    protected void onInitialized() {
        applySnapshot();
        log.info("onAllServicesInitialized");
        if (p2PService.isBootstrapped()) {
            log.info("onAllServicesInitialized: isBootstrapped");
            onP2PNetworkReady();
        } else {
            p2PService.addP2PServiceListener(new P2PServiceListener() {
                @Override
                public void onTorNodeReady() {
                }

                @Override
                public void onHiddenServicePublished() {
                }

                @Override
                public void onSetupFailed(Throwable throwable) {
                }

                @Override
                public void onRequestCustomBridges() {
                }

                @Override
                public void onDataReceived() {
                }

                @Override
                public void onNoSeedNodeAvailable() {
                    log.info("onAllServicesInitialized: onNoSeedNodeAvailable");
                    onP2PNetworkReady();
                }

                @Override
                public void onNoPeersAvailable() {
                }

                @Override
                public void onUpdatedDataReceived() {
                    log.info("onAllServicesInitialized: onBootstrapComplete");
                    onP2PNetworkReady();
                }
            });
        }
    }

    @SuppressWarnings("WeakerAccess")
    protected void onP2PNetworkReady() {
        p2pNetworkReady = true;
    }

    @SuppressWarnings("WeakerAccess")
    protected int getStartBlockHeight() {
        final int startBlockHeight = Math.max(genesisBlockHeight, stateService.getChainHeight());
        log.info("Start parse blocks:\n" +
                        "   Start block height={}\n" +
                        "   Genesis txId={}\n" +
                        "   Genesis block height={}\n" +
                        "   Block height={}\n",
                startBlockHeight,
                genesisTxId,
                genesisBlockHeight,
                stateService.getChainHeight());

        return startBlockHeight;
    }

    abstract protected void startParseBlocks();

    protected void onParseBlockChainComplete() {
        parseBlockchainComplete = true;
    }

    @SuppressWarnings("WeakerAccess")
    protected void startReOrgFromLastSnapshot() {
        applySnapshot();
        startParseBlocks();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void applySnapshot() {
        snapshotManager.applySnapshot();
    }
}
