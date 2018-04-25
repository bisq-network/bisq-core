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

package bisq.core.dao.node.lite;

import bisq.core.dao.node.BsqNode;
import bisq.core.dao.node.lite.network.LiteNodeNetworkService;
import bisq.core.dao.node.messages.GetBlocksResponse;
import bisq.core.dao.node.messages.NewBlockBroadcastMessage;
import bisq.core.dao.node.validation.BlockNotConnectingException;
import bisq.core.dao.node.validation.InvalidBlockException;
import bisq.core.dao.state.SnapshotManager;
import bisq.core.dao.state.StateService;
import bisq.core.dao.state.blockchain.Block;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.network.Connection;

import bisq.common.UserThread;
import bisq.common.handlers.ErrorMessageHandler;

import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Main class for lite nodes which receive the BSQ transactions from a full node (e.g. seed nodes).
 * <p>
 * Verification of BSQ transactions is done also by the lite node.
 */
@Slf4j
public class LiteNode extends BsqNode {
    private final LiteNodeParser liteNodeParser;
    private final LiteNodeNetworkService liteNodeNetworkService;
    private boolean startParseBlocksRequested;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public LiteNode(StateService stateService,
                    SnapshotManager snapshotManager,
                    P2PService p2PService,
                    LiteNodeParser liteNodeParser,
                    LiteNodeNetworkService liteNodeNetworkService) {
        super(stateService, snapshotManager, p2PService);
        this.liteNodeParser = liteNodeParser;
        this.liteNodeNetworkService = liteNodeNetworkService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void start(ErrorMessageHandler errorMessageHandler) {
        super.onInitialized();
    }

    public void shutDown() {
        liteNodeNetworkService.shutDown();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protected
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void onP2PNetworkReady() {
        super.onP2PNetworkReady();

        liteNodeNetworkService.addListener(new LiteNodeNetworkService.Listener() {
            @Override
            public void onRequestedBlocksReceived(GetBlocksResponse getBlocksResponse) {
                LiteNode.this.onRequestedBlocksReceived(new ArrayList<>(getBlocksResponse.getBlocks()));
            }

            @Override
            public void onNewBlockReceived(NewBlockBroadcastMessage newBlockBroadcastMessage) {
                LiteNode.this.onNewBlockReceived(newBlockBroadcastMessage.getBlock());
            }

            @Override
            public void onNoSeedNodeAvailable() {
            }

            @Override
            public void onFault(String errorMessage, @Nullable Connection connection) {
            }
        });

        // We might get the onP2PNetworkReady called multiple times due bugs in the hanlding in the p2p network
        // To protect us requesting several times we use a flag
        // delay a bit to not stress too much at startup
        if (!parseBlockchainComplete && !startParseBlocksRequested)
            UserThread.runAfter(this::startParseBlocks, 2);
    }

    // First we request the blocks from a full node
    @Override
    protected void startParseBlocks() {
        if (!startParseBlocksRequested) {
            log.info("startParseBlocks");
            startParseBlocksRequested = true;
            liteNodeNetworkService.requestBlocks(getStartBlockHeight());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We received the missing blocks
    private void onRequestedBlocksReceived(List<Block> blockList) {
        log.info("onRequestedBlocksReceived: blocks with {} items", blockList.size());
        if (blockList.size() > 0)
            log.info("block height of last item: {}", blockList.get(blockList.size() - 1).getHeight());

        // 4000 blocks take about 3 seconds if DAO UI is not displayed or 7 sec. if it is displayed.
        // The updates at block height change are not much optimized yet, so that can be for sure improved
        // 144 blocks a day would result in about 4000 in a month, so if a user downloads the app after 1 months latest
        // release it will be a bit of a performance hit. It is a one time event as the snapshots gets created and be
        // used at next startup.
        long startTs = System.currentTimeMillis();
        for (Block block : blockList) {
            try {
                liteNodeParser.parseBlock(block);
                onNewBlock(block);
            } catch (BlockNotConnectingException | InvalidBlockException e) {
                getErrorHandler().accept(e);
            }
        }
        log.info("Parsing of {} blocks took {} sec.", blockList.size(), (System.currentTimeMillis() - startTs) / 1000D);
        onParseBlockChainComplete();
    }

    // We received a new block
    private void onNewBlockReceived(Block block) {
        log.info("onNewBlockReceived: block at height {}", block.getHeight());
        if (!stateService.containsBlock(block)) {
            try {
                liteNodeParser.parseBlock(block);
                onNewBlock(block);
            } catch (BlockNotConnectingException | InvalidBlockException e) {
                getErrorHandler().accept(e);
            }
        }
    }

    private void onNewBlock(Block block) {
        log.debug("new block parsed: " + block);
    }

    @NotNull
    private Consumer<Throwable> getErrorHandler() {
        return throwable -> {
            if (throwable instanceof BlockNotConnectingException) {
                startReOrgFromLastSnapshot();
            } else {
                log.error(throwable.toString());
                throwable.printStackTrace();
            }
        };
    }
}
