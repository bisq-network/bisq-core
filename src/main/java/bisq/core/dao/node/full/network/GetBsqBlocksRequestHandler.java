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

package bisq.core.dao.node.full.network;

import bisq.core.dao.blockchain.ReadableBsqBlockChain;
import bisq.core.dao.blockchain.vo.BsqBlock;
import bisq.core.dao.node.messages.GetBsqBlocksRequest;
import bisq.core.dao.node.messages.GetBsqBlocksResponse;

import bisq.network.p2p.network.CloseConnectionReason;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.NetworkNode;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.app.Log;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;

import java.util.List;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

/**
 * Accepts a GetBsqBlocksRequest from a lite nodes and send back a corresponding GetBsqBlocksResponse.
 */
@Slf4j
class GetBsqBlocksRequestHandler {
    private static final long TIMEOUT = 120;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface Listener {
        void onComplete();

        void onFault(String errorMessage, Connection connection);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Class fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final NetworkNode networkNode;
    private final ReadableBsqBlockChain readableBsqBlockChain;
    private final Listener listener;
    private Timer timeoutTimer;
    private boolean stopped;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public GetBsqBlocksRequestHandler(NetworkNode networkNode, ReadableBsqBlockChain readableBsqBlockChain, Listener listener) {
        this.networkNode = networkNode;
        this.readableBsqBlockChain = readableBsqBlockChain;
        this.listener = listener;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onGetBsqBlocksRequest(GetBsqBlocksRequest getBsqBlocksRequest, final Connection connection) {
        Log.traceCall(getBsqBlocksRequest + "\n\tconnection=" + connection);
        List<BsqBlock> bsqBlocks = readableBsqBlockChain.getClonedBlocksFrom(getBsqBlocksRequest.getFromBlockHeight());
        final GetBsqBlocksResponse bsqBlocksResponse = new GetBsqBlocksResponse(bsqBlocks, getBsqBlocksRequest.getNonce());
        log.debug("bsqBlocksResponse " + bsqBlocksResponse.getRequestNonce());

        if (timeoutTimer == null) {
            timeoutTimer = UserThread.runAfter(() -> {  // setup before sending to avoid race conditions
                        String errorMessage = "A timeout occurred for bsqBlocksResponse:" + bsqBlocksResponse +
                                " on connection:" + connection;
                        handleFault(errorMessage, CloseConnectionReason.SEND_MSG_TIMEOUT, connection);
                    },
                    TIMEOUT, TimeUnit.SECONDS);
        }

        SettableFuture<Connection> future = networkNode.sendMessage(connection, bsqBlocksResponse);
        Futures.addCallback(future, new FutureCallback<Connection>() {
            @Override
            public void onSuccess(Connection connection) {
                if (!stopped) {
                    log.trace("Send DataResponse to {} succeeded. bsqBlocksResponse={}",
                            connection.getPeersNodeAddressOptional(), bsqBlocksResponse);
                    cleanup();
                    listener.onComplete();
                } else {
                    log.trace("We have stopped already. We ignore that networkNode.sendMessage.onSuccess call.");
                }
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                if (!stopped) {
                    String errorMessage = "Sending bsqBlocksResponse to " + connection +
                            " failed. That is expected if the peer is offline. bsqBlocksResponse=" + bsqBlocksResponse + "." +
                            "Exception: " + throwable.getMessage();
                    handleFault(errorMessage, CloseConnectionReason.SEND_MSG_FAILURE, connection);
                } else {
                    log.trace("We have stopped already. We ignore that networkNode.sendMessage.onFailure call.");
                }
            }
        });
    }

    public void stop() {
        cleanup();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handleFault(String errorMessage, CloseConnectionReason closeConnectionReason, Connection connection) {
        if (!stopped) {
            log.debug(errorMessage + "\n\tcloseConnectionReason=" + closeConnectionReason);
            cleanup();
            listener.onFault(errorMessage, connection);
        } else {
            log.warn("We have already stopped (handleFault)");
        }
    }

    private void cleanup() {
        stopped = true;
        if (timeoutTimer != null) {
            timeoutTimer.stop();
            timeoutTimer = null;
        }
    }
}
