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

package bisq.core.dao.consensus.node.lite;

import bisq.core.dao.consensus.node.blockchain.exceptions.BlockNotConnectingException;
import bisq.core.dao.consensus.state.blockchain.TxBlock;

import bisq.common.handlers.ResultHandler;

import javax.inject.Inject;

import java.util.List;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

/**
 * Hides implementation details how we access parser. For lite node there are just synchronous calls to parser.
 * The parser runs in user thread.
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

    void parseBlocks(List<TxBlock> txBlockList,
                     Consumer<TxBlock> newBlockHandler,
                     ResultHandler resultHandler,
                     Consumer<Throwable> errorHandler) {

        for (TxBlock txBlock : txBlockList) {
            try {
                liteNodeParser.parseBsqBlock(txBlock);
                newBlockHandler.accept(txBlock);
            } catch (BlockNotConnectingException e) {
                errorHandler.accept(e);
            }
        }
        resultHandler.handleResult();
    }

    void parseBlock(TxBlock txBlock,
                    Consumer<TxBlock> newBlockHandler,
                    Consumer<Throwable> errorHandler) {
        try {
            liteNodeParser.parseBsqBlock(txBlock);
            newBlockHandler.accept(txBlock);
        } catch (BlockNotConnectingException e) {
            errorHandler.accept(e);
        }
    }
}
