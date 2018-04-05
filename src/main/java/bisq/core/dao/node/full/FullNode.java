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

package bisq.core.dao.node.full;

import bisq.core.dao.blockchain.ReadableBsqBlockChain;
import bisq.core.dao.blockchain.SnapshotManager;
import bisq.core.dao.blockchain.exceptions.BlockNotConnectingException;
import bisq.core.dao.blockchain.json.JsonBlockChainExporter;
import bisq.core.dao.blockchain.vo.BsqBlock;
import bisq.core.dao.node.BsqNode;
import bisq.core.dao.node.full.network.FullNodeNetworkService;

import bisq.network.p2p.P2PService;

import bisq.common.UserThread;
import bisq.common.handlers.ErrorMessageHandler;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

/**
 * Main class for a full node which have Bitcoin Core with rpc running and does the blockchain lookup itself.
 * It also provides the BSQ transactions to lite nodes on request and broadcasts new BSQ blocks.
 */
@Slf4j
public class FullNode extends BsqNode {

    private final FullNodeExecutor bsqFullNodeExecutor;
    private final FullNodeNetworkService fullNodeNetworkService;
    private final JsonBlockChainExporter jsonBlockChainExporter;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public FullNode(ReadableBsqBlockChain readableBsqBlockChain,
                    SnapshotManager snapshotManager,
                    P2PService p2PService,
                    FullNodeExecutor bsqFullNodeExecutor,
                    JsonBlockChainExporter jsonBlockChainExporter,
                    FullNodeNetworkService fullNodeNetworkService) {
        super(readableBsqBlockChain,
                snapshotManager,
                p2PService);
        this.bsqFullNodeExecutor = bsqFullNodeExecutor;
        this.jsonBlockChainExporter = jsonBlockChainExporter;
        this.fullNodeNetworkService = fullNodeNetworkService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAllServicesInitialized(ErrorMessageHandler errorMessageHandler) {
        bsqFullNodeExecutor.setup(() -> {
                    super.onInitialized();
                    startParseBlocks();
                },
                throwable -> {
                    log.error(throwable.toString());
                    throwable.printStackTrace();
                    errorMessageHandler.handleErrorMessage("Initializing BsqFullNode failed: Error=" + throwable.toString());
                });
    }

    public void shutDown() {
        jsonBlockChainExporter.shutDown();
        fullNodeNetworkService.shutDown();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void startParseBlocks() {
        requestChainHeadHeightAndParseBlocks(getStartBlockHeight());
    }

    @Override
    protected void onP2PNetworkReady() {
        super.onP2PNetworkReady();

        if (parseBlockchainComplete)
            addBlockHandler();
    }

    private void onNewBsqBlock(BsqBlock bsqBlock) {
        jsonBlockChainExporter.maybeExport();
        if (parseBlockchainComplete && p2pNetworkReady)
            fullNodeNetworkService.publishNewBlock(bsqBlock);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addBlockHandler() {
        bsqFullNodeExecutor.addBlockHandler(btcdBlock -> bsqFullNodeExecutor.parseBtcdBlock(btcdBlock,
                this::onNewBsqBlock,
                throwable -> {
                    if (throwable instanceof BlockNotConnectingException) {
                        startReOrgFromLastSnapshot();
                    } else {
                        log.error(throwable.toString());
                        throwable.printStackTrace();
                    }
                }));
    }

    private void requestChainHeadHeightAndParseBlocks(int startBlockHeight) {
        log.info("parseBlocks startBlockHeight={}", startBlockHeight);
        bsqFullNodeExecutor.requestChainHeadHeight(chainHeadHeight -> parseBlocksOnHeadHeight(startBlockHeight, chainHeadHeight),
                throwable -> {
                    log.error(throwable.toString());
                    throwable.printStackTrace();
                });
    }

    private void parseBlocksOnHeadHeight(int startBlockHeight, Integer chainHeadHeight) {
        log.info("parseBlocks with from={} with chainHeadHeight={}", startBlockHeight, chainHeadHeight);
        if (chainHeadHeight != startBlockHeight) {
            if (startBlockHeight <= chainHeadHeight) {
                bsqFullNodeExecutor.parseBlocks(startBlockHeight,
                        chainHeadHeight,
                        this::onNewBsqBlock,
                        () -> {
                            // We are done but it might be that new blocks have arrived in the meantime,
                            // so we try again with startBlockHeight set to current chainHeadHeight
                            // We also set up the listener in the else main branch where we check
                            // if we are at chainTip, so do not include here another check as it would
                            // not trigger the listener registration.
                            requestChainHeadHeightAndParseBlocks(chainHeadHeight);
                        }, throwable -> {
                            if (throwable instanceof BlockNotConnectingException) {
                                startReOrgFromLastSnapshot();
                            } else {
                                log.error(throwable.toString());
                                throwable.printStackTrace();
                                //TODO write error to an errorProperty
                            }
                        });
            } else {
                log.warn("We are trying to start with a block which is above the chain height of bitcoin core. We need probably wait longer until bitcoin core has fully synced. We try again after a delay of 1 min.");
                UserThread.runAfter(() -> requestChainHeadHeightAndParseBlocks(startBlockHeight), 60);
            }
        } else {
            // We don't have received new blocks in the meantime so we are completed and we register our handler
            onParseBlockChainComplete();
        }
    }

    @Override
    protected void onParseBlockChainComplete() {
        super.onParseBlockChainComplete();

        if (p2pNetworkReady)
            addBlockHandler();
    }
}
