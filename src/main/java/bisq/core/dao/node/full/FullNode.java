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

import bisq.core.dao.node.BsqNode;
import bisq.core.dao.node.full.network.FullNodeNetworkService;
import bisq.core.dao.node.json.JsonBlockChainExporter;
import bisq.core.dao.node.validation.BlockNotConnectingException;
import bisq.core.dao.state.SnapshotManager;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.Block;

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

    private final FullNodeParserFacade fullNodeParserFacade;
    private final FullNodeNetworkService fullNodeNetworkService;
    private final JsonBlockChainExporter jsonBlockChainExporter;
    private boolean blockHandlerAdded;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public FullNode(StateService stateService,
                    SnapshotManager snapshotManager,
                    P2PService p2PService,
                    FullNodeParserFacade fullNodeParserFacade,
                    JsonBlockChainExporter jsonBlockChainExporter,
                    FullNodeNetworkService fullNodeNetworkService) {
        super(stateService, snapshotManager, p2PService);
        this.fullNodeParserFacade = fullNodeParserFacade;
        this.jsonBlockChainExporter = jsonBlockChainExporter;
        this.fullNodeNetworkService = fullNodeNetworkService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void start(ErrorMessageHandler errorMessageHandler) {
        fullNodeParserFacade.setupAsync(() -> {
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

    //TODO we get that called multiple times if we have only one seed node (to be fixed in p2p network library)
    // To prevent that we get our handler registered multiple times we use a flag.
    @Override
    protected void onP2PNetworkReady() {
        super.onP2PNetworkReady();

        if (parseBlockchainComplete && !blockHandlerAdded) {
            final int lastBlockHeight = stateService.getLastBlock().getHeight();
            log.info("onP2PNetworkReady: We run parseBlocksIfNewBlockAvailable with latest block height {}.", lastBlockHeight);
            parseBlocksIfNewBlockAvailable(lastBlockHeight);
            addBlockHandler();
        }
    }

    private void onNewBlock(Block block) {
        jsonBlockChainExporter.maybeExport();
        if (parseBlockchainComplete && p2pNetworkReady)
            fullNodeNetworkService.publishNewBlock(block);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addBlockHandler() {
        if (!blockHandlerAdded) {
            blockHandlerAdded = true;
            fullNodeParserFacade.addBlockHandler(btcdBlock -> fullNodeParserFacade.parseBtcdBlockAsync(btcdBlock,
                    this::onNewBlock,
                    throwable -> {
                        if (throwable instanceof BlockNotConnectingException) {
                            startReOrgFromLastSnapshot();
                        } else {
                            log.error(throwable.toString());
                            throwable.printStackTrace();
                        }
                    }));
        }
    }

    private void requestChainHeadHeightAndParseBlocks(int startBlockHeight) {
        log.info("parseBlocks startBlockHeight={}", startBlockHeight);
        fullNodeParserFacade.requestChainHeadHeightAsync(chainHeadHeight -> parseBlocksOnHeadHeight(startBlockHeight, chainHeadHeight),
                throwable -> {
                    log.error(throwable.toString());
                    throwable.printStackTrace();
                });
    }

    private void parseBlocksOnHeadHeight(int startBlockHeight, Integer chainHeadHeight) {
        log.info("parseBlocks with startBlockHeight={} and chainHeadHeight={}", startBlockHeight, chainHeadHeight);
        if (startBlockHeight <= chainHeadHeight) {
            fullNodeParserFacade.parseBlocksAsync(startBlockHeight,
                    chainHeadHeight,
                    this::onNewBlock,
                    () -> {
                        // We are done but it might be that new blocks have arrived in the meantime,
                        // so we try again with startBlockHeight set to current chainHeadHeight
                        // We also set up the listener in the else main branch where we check
                        // if we are at chainTip, so do not include here another check as it would
                        // not trigger the listener registration.
                        parseBlocksIfNewBlockAvailable(chainHeadHeight);
                    }, throwable -> {
                        if (throwable instanceof BlockNotConnectingException) {
                            BlockNotConnectingException blockNotConnectingException = (BlockNotConnectingException) throwable;
                            final int lastBlockHeight = stateService.getLastBlock().getHeight();
                            final int receivedBlockHeight = blockNotConnectingException.getBlock().getHeight();
                            if (receivedBlockHeight > lastBlockHeight + 1) {
                                // If we missed a block we request missing ones
                                parseBlocksOnHeadHeight(lastBlockHeight, receivedBlockHeight);
                            } else {
                                startReOrgFromLastSnapshot();
                            }
                        } else {
                            log.error(throwable.toString());
                            throwable.printStackTrace();
                            //TODO write error to an errorProperty
                        }
                    });
        } else {
            log.warn("We are trying to start with a block which is above the chain height of bitcoin core. " +
                    "We need probably wait longer until bitcoin core has fully synced. " +
                    "We try again after a delay of 1 min.");
            UserThread.runAfter(() -> requestChainHeadHeightAndParseBlocks(startBlockHeight), 60);
        }
    }

    private void parseBlocksIfNewBlockAvailable(Integer chainHeadHeight) {
        fullNodeParserFacade.requestChainHeadHeightAsync(newChainHeadHeight -> {
                    if (newChainHeadHeight > chainHeadHeight) {
                        log.info("While parsing new blocks arrived. Parse again with those missing blocks." +
                                "ChainHeadHeight={}, newChainHeadHeight={}", chainHeadHeight, newChainHeadHeight);
                        parseBlocksOnHeadHeight(chainHeadHeight, newChainHeadHeight);
                    } else
                        log.info("parseBlocksIfNewBlockAvailable did not result in a new block, so we complete.");
                    onParseBlockChainComplete();
                },
                throwable -> {
                    log.error(throwable.toString());
                    throwable.printStackTrace();
                });
    }

    @Override
    protected void onParseBlockChainComplete() {
        super.onParseBlockChainComplete();
        if (p2pNetworkReady && !blockHandlerAdded) {
            addBlockHandler();
        } else {
            log.info("onParseBlockChainComplete but P2P network is not ready yet.");
        }
    }
}
