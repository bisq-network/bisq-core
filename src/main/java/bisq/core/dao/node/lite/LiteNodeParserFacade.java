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

import bisq.core.dao.node.blockchain.exceptions.BlockNotConnectingException;
import bisq.core.dao.state.blockchain.Block;

import bisq.common.handlers.ResultHandler;

import javax.inject.Inject;

import java.util.List;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

/**
 * Hides implementation details how we access parser. For lite node there are just synchronous calls to parser.
 */
@Slf4j
public class LiteNodeParserFacade {

    private final LiteNodeParser liteNodeParser;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess")
    @Inject
    public LiteNodeParserFacade(LiteNodeParser liteNodeParser) {
        this.liteNodeParser = liteNodeParser;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Package private
    ///////////////////////////////////////////////////////////////////////////////////////////

    void parseBlocks(List<Block> blockList,
                     Consumer<Block> newBlockHandler,
                     ResultHandler resultHandler,
                     Consumer<Throwable> errorHandler) {

        for (Block block : blockList) {
            try {
                liteNodeParser.parseBlock(block);
                newBlockHandler.accept(block);
            } catch (BlockNotConnectingException e) {
                errorHandler.accept(e);
            }
        }
        resultHandler.handleResult();
    }

    void parseBlock(Block block,
                    Consumer<Block> newBlockHandler,
                    Consumer<Throwable> errorHandler) {
        try {
            liteNodeParser.parseBlock(block);
            newBlockHandler.accept(block);
        } catch (BlockNotConnectingException e) {
            errorHandler.accept(e);
        }
    }
}
