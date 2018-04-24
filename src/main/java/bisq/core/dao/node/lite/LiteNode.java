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
import bisq.core.dao.node.blockchain.exceptions.BlockNotConnectingException;
import bisq.core.dao.node.lite.network.LiteNodeNetworkService;
import bisq.core.dao.node.messages.GetBlocksResponse;
import bisq.core.dao.node.messages.NewBlockBroadcastMessage;
import bisq.core.dao.period.PeriodService;
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
import java.util.stream.Collectors;

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
    private final LiteNodeParserFacade liteNodeParserFacade;
    private final LiteNodeNetworkService liteNodeNetworkService;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public LiteNode(StateService stateService,
                    PeriodService periodService,
                    SnapshotManager snapshotManager,
                    P2PService p2PService,
                    LiteNodeParserFacade liteNodeParserFacade,
                    LiteNodeNetworkService liteNodeNetworkService) {
        super(stateService, periodService, snapshotManager, p2PService);
        this.liteNodeParserFacade = liteNodeParserFacade;
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

        // delay a bit to not stress too much at startup
        UserThread.runAfter(this::startParseBlocks, 2);
    }

    // First we request the blocks from a full node
    @Override
    protected void startParseBlocks() {
        liteNodeNetworkService.requestBlocks(getStartBlockHeight());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We received the missing blocks
    private void onRequestedBlocksReceived(List<Block> blockList) {
        log.info("onRequestedBlocksReceived: blocks with {} items", blockList.size());
        if (blockList.size() > 0)
            log.info("block height of last item: {}", blockList.get(blockList.size() - 1).getHeight());
        // We clone with a reset of all mutable data in case the provider would not have done it.
        List<Block> clonedBlockList = blockList.stream()
                .map(Block::clone)
                .collect(Collectors.toList());
        liteNodeParserFacade.parseBlocks(clonedBlockList,
                this::onNewBlock,
                this::onParseBlockChainComplete,
                getErrorHandler());
    }

    // We received a new block
    private void onNewBlockReceived(Block block) {
        log.info("onNewBlockReceived: block={}", block.getHeight());

        // We clone with a reset of all mutable data in case the provider would not have done it.
        Block clonedBlock = Block.clone(block);
        if (!stateService.containsBlock(clonedBlock)) {
            //TODO check block height and prev block it it connects to existing blocks
            liteNodeParserFacade.parseBlock(clonedBlock, this::onNewBlock, getErrorHandler());
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
