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


import bisq.core.trade.BuyerAsTakerTrade;
import bisq.core.trade.Trade;
import bisq.core.trade.messages.PayoutTxPublishedMessage;
import bisq.core.trade.messages.PublishDepositTxRequest;
import bisq.core.trade.messages.TradeMessage;
import bisq.core.trade.protocol.tasks.CheckIfPeerIsBanned;
import bisq.core.trade.protocol.tasks.PublishTradeStatistics;
import bisq.core.trade.protocol.tasks.VerifyPeersAccountAgeWitness;
import bisq.core.trade.protocol.tasks.buyer.BuyerProcessPayoutTxPublishedMessage;
import bisq.core.trade.protocol.tasks.buyer.BuyerSendCounterCurrencyTransferStartedMessage;
import bisq.core.trade.protocol.tasks.buyer.BuyerSetupPayoutTxListener;
import bisq.core.trade.protocol.tasks.buyer_as_maker.BuyerAsMakerSignPayoutTx;
import bisq.core.trade.protocol.tasks.buyer_as_taker.BuyerAsTakerCreatesDepositTxInputs;
import bisq.core.trade.protocol.tasks.buyer_as_taker.BuyerAsTakerSignAndPublishDepositTx;
import bisq.core.trade.protocol.tasks.taker.CreateTakerFeeTx;
import bisq.core.trade.protocol.tasks.taker.TakerProcessPublishDepositTxRequest;
import bisq.core.trade.protocol.tasks.taker.TakerSelectArbitrator;
import bisq.core.trade.protocol.tasks.taker.TakerSelectMediator;
import bisq.core.trade.protocol.tasks.taker.TakerSendDepositTxPublishedMessage;
import bisq.core.trade.protocol.tasks.taker.TakerSendPayDepositRequest;
import bisq.core.trade.protocol.tasks.taker.TakerVerifyAndSignContract;
import bisq.core.trade.protocol.tasks.taker.TakerVerifyMakerAccount;
import bisq.core.trade.protocol.tasks.taker.TakerVerifyMakerFeePayment;

import bisq.network.p2p.MailboxMessage;
import bisq.network.p2p.NodeAddress;

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.proto.network.NetworkEnvelope;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BuyerAsTakerProtocol extends TradeProtocol implements BuyerProtocol, TakerProtocol {
    private final BuyerAsTakerTrade buyerAsTakerTrade;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BuyerAsTakerProtocol(BuyerAsTakerTrade trade) {
        super(trade);

        this.buyerAsTakerTrade = trade;

        processModel.getTradingPeer().setPubKeyRing(trade.getOffer().getPubKeyRing());

        if (trade.isFiatSent() && !trade.isPayoutPublished()) {
            TradeTaskRunner taskRunner = new TradeTaskRunner(trade,
                    () -> handleTaskRunnerSuccess("BuyerSetupPayoutTxListener"),
                    this::handleTaskRunnerFault);

            taskRunner.addTasks(BuyerSetupPayoutTxListener.class);
            taskRunner.run();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Mailbox
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void doApplyMailboxMessage(NetworkEnvelope networkEnvelop, Trade trade) {
        this.trade = trade;
        final NodeAddress senderNodeAddress = ((MailboxMessage) networkEnvelop).getSenderNodeAddress();
        if (networkEnvelop instanceof PublishDepositTxRequest)
            handle((PublishDepositTxRequest) networkEnvelop, senderNodeAddress);
        else if (networkEnvelop instanceof PayoutTxPublishedMessage) {
            handle((PayoutTxPublishedMessage) networkEnvelop, senderNodeAddress);
        } else
            log.error("We received an unhandled MailboxMessage" + networkEnvelop.toString());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Start trade
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void takeAvailableOffer() {
        TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsTakerTrade,
                () -> handleTaskRunnerSuccess("takeAvailableOffer"),
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                TakerSelectArbitrator.class,
                TakerSelectMediator.class,
                TakerVerifyMakerAccount.class,
                TakerVerifyMakerFeePayment.class,
                CreateTakerFeeTx.class,
                BuyerAsTakerCreatesDepositTxInputs.class,
                TakerSendPayDepositRequest.class
        );

        //TODO if peer does get an error he does not respond and all we get is the timeout now knowing why it failed.
        // We should add an error message the peer sends us in such cases.
        startTimeout();
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(PublishDepositTxRequest tradeMessage, NodeAddress sender) {
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerNodeAddress(sender);

        TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsTakerTrade,
                () -> {
                    stopTimeout();
                    handleTaskRunnerSuccess("PublishDepositTxRequest");
                },
                this::handleTaskRunnerFault);
        taskRunner.addTasks(
                TakerProcessPublishDepositTxRequest.class,
                CheckIfPeerIsBanned.class,
                TakerVerifyMakerAccount.class,
                VerifyPeersAccountAgeWitness.class,
                TakerVerifyMakerFeePayment.class,
                TakerVerifyAndSignContract.class,
                BuyerAsTakerSignAndPublishDepositTx.class,
                TakerSendDepositTxPublishedMessage.class,
                PublishTradeStatistics.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Called from UI
    ///////////////////////////////////////////////////////////////////////////////////////////

    // User clicked the "bank transfer started" button
    @Override
    public void onFiatPaymentStarted(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        if (!trade.isFiatSent()) {
            buyerAsTakerTrade.setState(Trade.State.BUYER_CONFIRMED_IN_UI_FIAT_PAYMENT_INITIATED);

            TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsTakerTrade,
                    () -> {
                        resultHandler.handleResult();
                        handleTaskRunnerSuccess("onFiatPaymentStarted");
                    },
                    (errorMessage) -> {
                        errorMessageHandler.handleErrorMessage(errorMessage);
                        handleTaskRunnerFault(errorMessage);
                    });
            taskRunner.addTasks(
                    CheckIfPeerIsBanned.class,
                    TakerVerifyMakerAccount.class,
                    TakerVerifyMakerFeePayment.class,
                    BuyerAsMakerSignPayoutTx.class,
                    BuyerSendCounterCurrencyTransferStartedMessage.class,
                    BuyerSetupPayoutTxListener.class
            );
            taskRunner.run();
        } else {
            log.warn("onFiatPaymentStarted called twice. tradeState=" + trade.getState());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(PayoutTxPublishedMessage tradeMessage, NodeAddress peerNodeAddress) {
        log.debug("handle PayoutTxPublishedMessage called");
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerNodeAddress(peerNodeAddress);

        TradeTaskRunner taskRunner = new TradeTaskRunner(buyerAsTakerTrade,
                () -> handleTaskRunnerSuccess("handle PayoutTxPublishedMessage"),
                this::handleTaskRunnerFault);

        taskRunner.addTasks(
                BuyerProcessPayoutTxPublishedMessage.class
        );
        taskRunner.run();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Massage dispatcher
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void doHandleDecryptedMessage(TradeMessage tradeMessage, NodeAddress sender) {
        if (tradeMessage instanceof PublishDepositTxRequest) {
            handle((PublishDepositTxRequest) tradeMessage, sender);
        } else if (tradeMessage instanceof PayoutTxPublishedMessage) {
            handle((PayoutTxPublishedMessage) tradeMessage, sender);
        } else {
            log.error("Incoming message not supported. " + tradeMessage);
        }
    }
}
