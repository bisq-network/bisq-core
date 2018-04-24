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

import bisq.core.dao.node.blockchain.exceptions.BlockNotConnectingException;
import bisq.core.dao.state.blockchain.Block;
import bisq.core.dao.state.blockchain.Tx;

import bisq.common.handlers.ResultHandler;

import javax.inject.Inject;

import java.util.List;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

/**
 * Hides implementation details how we access parser. For full node there are asynchronous calls vai the rp service
 * to the parser. The rpc service runs in a separate thread. The parser runs in user thread.
 */
@Slf4j
public class FullNodeParserFacade {

    private final FullNodeParser fullNodeParser;
    private final RpcService rpcService;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public FullNodeParserFacade(RpcService rpcService, FullNodeParser fullNodeParser) {
        this.rpcService = rpcService;
        this.fullNodeParser = fullNodeParser;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Package private
    ///////////////////////////////////////////////////////////////////////////////////////////

    void setupAsync(ResultHandler resultHandler, Consumer<Throwable> errorHandler) {
        rpcService.setup(resultHandler, errorHandler);
    }

    void requestChainHeadHeightAsync(Consumer<Integer> resultHandler, Consumer<Throwable> errorHandler) {
        rpcService.requestChainHeadHeight(resultHandler, errorHandler);
    }

    void parseBlocksAsync(int startBlockHeight,
                          int chainHeadHeight,
                          Consumer<Block> newBlockHandler,
                          ResultHandler resultHandler,
                          Consumer<Throwable> errorHandler) {
        parseBlockAsync(startBlockHeight, chainHeadHeight, newBlockHandler, errorHandler);
        resultHandler.handleResult();
    }

    private void parseBlockAsync(int blockHeight, int chainHeadHeight, Consumer<Block> newBlockHandler, Consumer<Throwable> errorHandler) {
        rpcService.requestBlockWithAllTransactions(blockHeight,
                resultTuple -> {
                    try {
                        List<Tx> txList = resultTuple.second;
                        Block block = fullNodeParser.parseBlock(resultTuple.first, txList);
                        newBlockHandler.accept(block);
                        if (blockHeight < chainHeadHeight) {
                            final int newBlockHeight = blockHeight + 1;
                            parseBlockAsync(newBlockHeight, chainHeadHeight, newBlockHandler, errorHandler);
                        }
                    } catch (BlockNotConnectingException e) {
                        errorHandler.accept(e);
                    }
                },
                errorHandler);
    }

    void parseBtcdBlockAsync(com.neemre.btcdcli4j.core.domain.Block btcdBlock,
                             Consumer<Block> resultHandler,
                             Consumer<Throwable> errorHandler) {
        rpcService.requestAllTransactionsOfBlock(btcdBlock,
                txList -> {
                    try {
                        Block block = fullNodeParser.parseBlock(btcdBlock, txList);
                        resultHandler.accept(block);
                    } catch (BlockNotConnectingException e) {
                        errorHandler.accept(e);
                    }
                },
                errorHandler);
    }

    void addBlockHandler(Consumer<com.neemre.btcdcli4j.core.domain.Block> blockHandler) {
        rpcService.registerBlockHandler(blockHandler);
    }
}
