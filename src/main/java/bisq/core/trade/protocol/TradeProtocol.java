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

package bisq.core.trade.protocol;

import bisq.core.trade.MakerTrade;
import bisq.core.trade.Trade;
import bisq.core.trade.TradeManager;
import bisq.core.trade.messages.CounterCurrencyTransferStartedMessage;
import bisq.core.trade.messages.PayDepositRequest;
import bisq.core.trade.messages.TradeMessage;

import bisq.network.p2p.AckMessage;
import bisq.network.p2p.AckMessageSourceType;
import bisq.network.p2p.DecryptedDirectMessageListener;
import bisq.network.p2p.DecryptedMessageWithPubKey;
import bisq.network.p2p.MailboxMessage;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.SendMailboxMessageListener;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.crypto.PubKeyRing;
import bisq.common.proto.network.NetworkEnvelope;

import javafx.beans.value.ChangeListener;

import java.security.PublicKey;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static bisq.core.util.Validator.nonEmptyStringOf;

@Slf4j
public abstract class TradeProtocol {
    private static final long TIMEOUT = 120;

    protected final ProcessModel processModel;
    private final DecryptedDirectMessageListener decryptedDirectMessageListener;
    private final ChangeListener<Trade.State> stateChangeListener;
    protected Trade trade;
    private Timer timeoutTimer;

    public TradeProtocol(Trade trade) {
        this.trade = trade;
        this.processModel = trade.getProcessModel();

        decryptedDirectMessageListener = (decryptedMessageWithPubKey, peersNodeAddress) -> {
            // We check the sig only as soon we have stored the peers pubKeyRing.
            PubKeyRing tradingPeerPubKeyRing = processModel.getTradingPeer().getPubKeyRing();
            PublicKey signaturePubKey = decryptedMessageWithPubKey.getSignaturePubKey();
            if (tradingPeerPubKeyRing != null && signaturePubKey.equals(tradingPeerPubKeyRing.getSignaturePubKey())) {
                NetworkEnvelope networkEnvelope = decryptedMessageWithPubKey.getNetworkEnvelope();
                log.trace("handleNewMessage: message = " + networkEnvelope.getClass().getSimpleName() + " from " + peersNodeAddress);
                if (networkEnvelope instanceof TradeMessage) {
                    TradeMessage tradeMessage = (TradeMessage) networkEnvelope;
                    nonEmptyStringOf(tradeMessage.getTradeId());

                    if (tradeMessage.getTradeId().equals(processModel.getOfferId()))
                        doHandleDecryptedMessage(tradeMessage, peersNodeAddress);
                } else if (networkEnvelope instanceof AckMessage) {
                    AckMessage ackMessage = (AckMessage) networkEnvelope;
                    if (ackMessage.getSourceType() == AckMessageSourceType.TRADE_MESSAGE &&
                            ackMessage.getSourceId().equals(trade.getId())) {
                        // We only handle the ack for CounterCurrencyTransferStartedMessage
                        if (ackMessage.getSourceMsgClassName().equals(CounterCurrencyTransferStartedMessage.class.getSimpleName()))
                            processModel.setPaymentStartedAckMessage(ackMessage);

                        if (ackMessage.isSuccess()) {
                            log.info("Received AckMessage as directMessage with tradeId {} and uid={}",
                                    ackMessage.getSourceId(), ackMessage.getSourceUid());
                        } else {
                            log.warn("Received AckMessage as directMessage with error message for tradeId {}. ackMessage={}",
                                    ackMessage.getSourceId(), ackMessage);
                        }
                    }
                }
            }
        };
        processModel.getP2PService().addDecryptedDirectMessageListener(decryptedDirectMessageListener);

        stateChangeListener = (observable, oldValue, newValue) -> {
            if (newValue.getPhase() == Trade.Phase.TAKER_FEE_PUBLISHED && trade instanceof MakerTrade)
                processModel.getOpenOfferManager().closeOpenOffer(trade.getOffer());
        };
        trade.stateProperty().addListener(stateChangeListener);
    }

    public void completed() {
        cleanup();

        // We only removed earlier the listner here, but then we migth have dangling trades after faults...
        // so lets remove it at cleanup
        //processModel.getP2PService().removeDecryptedDirectMessageListener(decryptedDirectMessageListener);
    }

    private void cleanup() {
        log.debug("cleanup " + this);
        stopTimeout();
        trade.stateProperty().removeListener(stateChangeListener);
        // We removed that from here earlier as it broke the trade process in some non critical error cases.
        // But it should be actually removed...
        processModel.getP2PService().removeDecryptedDirectMessageListener(decryptedDirectMessageListener);
    }

    public void applyMailboxMessage(DecryptedMessageWithPubKey decryptedMessageWithPubKey, Trade trade) {
        log.debug("applyMailboxMessage " + decryptedMessageWithPubKey.getNetworkEnvelope());
        if (processModel.getTradingPeer().getPubKeyRing() != null &&
                decryptedMessageWithPubKey.getSignaturePubKey().equals(processModel.getTradingPeer().getPubKeyRing().getSignaturePubKey())) {
            processModel.setDecryptedMessageWithPubKey(decryptedMessageWithPubKey);
            doApplyMailboxMessage(decryptedMessageWithPubKey.getNetworkEnvelope(), trade);
        } else {
            log.error("SignaturePubKey in message does not match the SignaturePubKey we have stored to that trading peer.");
        }
    }

    protected abstract void doApplyMailboxMessage(NetworkEnvelope networkEnvelop, Trade trade);

    protected abstract void doHandleDecryptedMessage(TradeMessage tradeMessage, NodeAddress peerNodeAddress);

    protected void startTimeout() {
        stopTimeout();

        timeoutTimer = UserThread.runAfter(() -> {
            log.error("Timeout reached. TradeID={}, state={}", trade.getId(), trade.stateProperty().get());
            trade.setErrorMessage("A timeout occurred.");
            cleanupTradableOnFault();
            cleanup();
        }, TIMEOUT);
    }

    protected void stopTimeout() {
        if (timeoutTimer != null) {
            timeoutTimer.stop();
            timeoutTimer = null;
        }
    }

    protected void handleTaskRunnerSuccess(String info) {
        log.debug("handleTaskRunnerSuccess " + info);

        sendAckMessage(true, null);
    }

    protected void handleTaskRunnerFault(String errorMessage) {
        log.error(errorMessage);

        sendAckMessage(false, errorMessage);

        cleanupTradableOnFault();
        cleanup();
    }

    private void sendAckMessage(boolean result, @Nullable String errorMessage) {
        TradeMessage tradeMessage = processModel.getTradeMessage();
        // We complete at initial protocol setup with the setup listener tasks.
        // Other cases are if we start from an UI event the task runner (payment started, confirmed).
        // In such cases we have not set any tradeMessage and we ignore the sendAckMessage call.
        if (tradeMessage == null)
            return;

        String tradeId = tradeMessage.getTradeId();
        String sourceUid = "";
        if (tradeMessage instanceof MailboxMessage) {
            sourceUid = ((MailboxMessage) tradeMessage).getUid();
        } else {
            // For direct msg we don't have a mandatory uid so we need to cast to get it
            if (tradeMessage instanceof PayDepositRequest) {
                sourceUid = ((PayDepositRequest) tradeMessage).getUid();
            }
        }
        AckMessage ackMessage = new AckMessage(processModel.getMyNodeAddress(),
                AckMessageSourceType.TRADE_MESSAGE,
                tradeMessage.getClass().getSimpleName(),
                sourceUid,
                tradeId,
                result,
                errorMessage);

        processModel.getP2PService().sendEncryptedMailboxMessage(
                trade.getTradingPeerNodeAddress(),
                processModel.getTradingPeer().getPubKeyRing(),
                ackMessage,
                new SendMailboxMessageListener() {
                    @Override
                    public void onArrived() {
                        log.info("AckMessage arrived at peer {}. tradeId={}, uid={}",
                                trade.getTradingPeerNodeAddress(), tradeId, ackMessage.getSourceUid());
                    }

                    @Override
                    public void onStoredInMailbox() {
                        log.info("AckMessage stored in mailbox for peer {}. tradeId={}, uid={}",
                                trade.getTradingPeerNodeAddress(), tradeId, ackMessage.getSourceUid());
                    }

                    @Override
                    public void onFault(String errorMessage) {
                        log.error("sendEncryptedMailboxMessage failed. AckMessage={}, peer={}", ackMessage, trade.getTradingPeerNodeAddress());
                    }
                }
        );
    }

    private void cleanupTradableOnFault() {
        final Trade.State state = trade.getState();
        log.warn("cleanupTradableOnFault tradeState=" + state);
        TradeManager tradeManager = processModel.getTradeManager();
        if (trade.isInPreparation()) {
            // no funds left. we just clean up the trade list
            tradeManager.removePreparedTrade(trade);
        } else if (!trade.isFundsLockedIn()) {
            if (processModel.getPreparedDepositTx() == null) {
                if (trade.isTakerFeePublished())
                    tradeManager.addTradeToFailedTrades(trade);
                else
                    tradeManager.addTradeToClosedTrades(trade);
            } else {
                log.error("We have already sent the prepared deposit tx to the peer but we did not received the reply " +
                        "about the deposit tx nor saw it in the network. tradeId={}, tradeState={}", trade.getId(), trade.getState());
            }
        }
    }
}
