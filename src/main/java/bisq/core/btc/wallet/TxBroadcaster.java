/*
 * This file is part of Bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.btc.wallet;

import bisq.common.Timer;
import bisq.common.UserThread;

import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.wallet.Wallet;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

@Slf4j
public class TxBroadcaster {
    public interface Callback {
        void onSuccess(Transaction transaction);

        default void onTimeout(TxBroadcastTimeoutException exception) {
            log.error("TxBroadcaster.onTimeout " + exception.toString());
            onFailure(exception);
        }

        default void onTxMalleability(TxMalleabilityException exception) {
            log.error("onTxMalleability.onTimeout " + exception.toString());
            onFailure(exception);
        }

        void onFailure(TxBroadcastException exception);
    }

    private static final int DEFAULT_BROADCAST_TIMEOUT = 8;
    private static Map<String, Timer> broadcastTimerMap = new HashMap<>();

    public static void broadcastTx(Wallet wallet, PeerGroup peerGroup, Transaction localTx, Callback callback) {
        broadcastTx(wallet, peerGroup, localTx, callback, DEFAULT_BROADCAST_TIMEOUT);
    }

    public static void broadcastTx(Wallet wallet, PeerGroup peerGroup, Transaction tx, Callback callback, int delayInSec) {
        Timer timeoutTimer;
        final String txId = tx.getHashAsString();
        if (!broadcastTimerMap.containsKey(txId)) {
            timeoutTimer = UserThread.runAfter(() -> {
                log.warn("Broadcast of tx {} not completed after {} sec. We optimistically assume that the tx " +
                        "broadcast succeeded and call onSuccess on the callback handler.", txId, delayInSec);
                broadcastTimerMap.remove(txId);
                stopAndRemoveTimer(txId);
                UserThread.execute(() -> callback.onTimeout(new TxBroadcastTimeoutException(tx, delayInSec)));
            }, delayInSec);

            broadcastTimerMap.put(txId, timeoutTimer);
        } else {
            stopAndRemoveTimer(txId);
            UserThread.execute(() -> callback.onFailure(new TxBroadcastException("We got broadcastTx called with a tx " +
                    "which has an open timeoutTimer. txId=" + txId, txId)));
        }

        Futures.addCallback(peerGroup.broadcastTransaction(tx).future(), new FutureCallback<Transaction>() {
            @Override
            public void onSuccess(@Nullable Transaction result) {
                if (result != null) {
                    if (txId.equals(result.getHashAsString())) {
                        // We expect that there is still a timeout in our map, otherwise the timeout got triggered
                        if (broadcastTimerMap.containsKey(txId)) {
                            wallet.maybeCommitTx(tx);
                            stopAndRemoveTimer(txId);
                            // At regtest we get called immediately back but we want to make sure that the handler is not called
                            // before the caller is finished.
                            UserThread.execute(() -> callback.onSuccess(tx));
                        } else {
                            stopAndRemoveTimer(txId);
                            UserThread.execute(() -> callback.onFailure(new TxBroadcastException("We got an onSuccess callback for " +
                                    "a broadcast which got already triggered the timeout.", txId)));
                        }
                    } else {
                        stopAndRemoveTimer(txId);
                        UserThread.execute(() -> callback.onTxMalleability(new TxMalleabilityException(tx, result)));
                    }
                } else {
                    stopAndRemoveTimer(txId);
                    UserThread.execute(() -> callback.onFailure(new TxBroadcastException("Transaction returned from the " +
                            "broadcastTransaction call back is null.", txId)));
                }
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                stopAndRemoveTimer(txId);
                UserThread.execute(() -> callback.onFailure(new TxBroadcastException("We got an onFailure from " +
                        "the peerGroup.broadcastTransaction callback.", throwable)));
            }
        });
    }

    private static void stopAndRemoveTimer(String txId) {
        Timer timer = broadcastTimerMap.get(txId);
        if (timer != null)
            timer.stop();

        broadcastTimerMap.remove(txId);
    }
}
