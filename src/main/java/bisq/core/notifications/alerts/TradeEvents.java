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

package bisq.core.notifications.alerts;

import bisq.core.notifications.MobileMessage;
import bisq.core.notifications.MobileMessageType;
import bisq.core.notifications.MobileNotificationService;
import bisq.core.trade.Trade;
import bisq.core.trade.TradeManager;

import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;

import javax.inject.Inject;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

import javafx.collections.ListChangeListener;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TradeEvents {
    private final PubKeyRing pubKeyRing;
    private final TradeManager tradeManager;
    private final MobileNotificationService mobileNotificationService;

    @Inject
    public TradeEvents(TradeManager tradeManager, KeyRing keyRing, MobileNotificationService mobileNotificationService) {
        this.tradeManager = tradeManager;
        this.mobileNotificationService = mobileNotificationService;
        this.pubKeyRing = keyRing.getPubKeyRing();
    }

    public void onAllServicesInitialized() {
        tradeManager.getTradableList().addListener((ListChangeListener<Trade>) c -> {
            c.next();
            if (c.wasAdded()) {
                c.getAddedSubList().forEach(this::setTradePhaseListener);
            }
        });
        tradeManager.getTradableList().forEach(this::setTradePhaseListener);
    }

    private void setTradePhaseListener(Trade trade) {
        //TODO use weak ref or remove listener
        log.info("We got a new trade. id={}", trade.getId());
        trade.statePhaseProperty().addListener(new ChangeListener<Trade.Phase>() {
            @Override
            public void changed(ObservableValue<? extends Trade.Phase> observable, Trade.Phase oldValue, Trade.Phase newValue) {
                String msg = null;
                log.error("phase " + newValue);
                switch (newValue) {
                    case INIT:
                    case TAKER_FEE_PUBLISHED:
                    case DEPOSIT_PUBLISHED:
                        break;
                    case DEPOSIT_CONFIRMED:
                        if (trade.getContract() != null && pubKeyRing.equals(trade.getContract().getBuyerPubKeyRing()))
                            msg = "The trade with ID " + trade.getShortId() + " is confirmed.";
                        break;
                    case FIAT_SENT:
                        // We only notify the seller
                        if (trade.getContract() != null && pubKeyRing.equals(trade.getContract().getSellerPubKeyRing()))
                            msg = "The BTC buyer has started the payment for the trade with ID " + trade.getShortId() + ".";
                        break;
                    case FIAT_RECEIVED:
                        break;
                    case PAYOUT_PUBLISHED:
                        // We only notify the buyer
                        if (trade.getContract() != null && pubKeyRing.equals(trade.getContract().getBuyerPubKeyRing()))
                            msg = "The trade with ID " + trade.getShortId() + " is completed.";
                        break;
                    case WITHDRAWN:
                        break;
                }
                if (msg != null) {
                    MobileMessage message = new MobileMessage("Trade state event",
                            msg,
                            trade.getShortId(),
                            MobileMessageType.TRADE);
                    try {
                        mobileNotificationService.sendMessage(message);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }
}
