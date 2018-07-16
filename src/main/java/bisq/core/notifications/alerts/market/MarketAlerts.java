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

package bisq.core.notifications.alerts.market;

import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.monetary.Altcoin;
import bisq.core.monetary.Price;
import bisq.core.notifications.MobileMessage;
import bisq.core.notifications.MobileMessageType;
import bisq.core.notifications.MobileNotificationService;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferBookService;
import bisq.core.offer.OfferPayload;
import bisq.core.payment.payload.PaymentMethod;
import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.user.User;
import bisq.core.util.BSFormatter;

import bisq.common.util.MathUtils;

import org.bitcoinj.utils.Fiat;

import javax.inject.Inject;

import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MarketAlerts {
    private final OfferBookService offerBookService;
    private final MobileNotificationService mobileNotificationService;
    private final User user;
    private final PriceFeedService priceFeedService;
    private BSFormatter formatter;

    @Inject
    public MarketAlerts(OfferBookService offerBookService, MobileNotificationService mobileNotificationService,
                        User user, PriceFeedService priceFeedService, BSFormatter formatter) {
        this.offerBookService = offerBookService;
        this.mobileNotificationService = mobileNotificationService;
        this.user = user;
        this.priceFeedService = priceFeedService;
        this.formatter = formatter;
    }

    public void onAllServicesInitialized() {
        offerBookService.addOfferBookChangedListener(new OfferBookService.OfferBookChangedListener() {
            @Override
            public void onAdded(Offer offer) {
                onOfferAdded(offer);
            }

            @Override
            public void onRemoved(Offer offer) {
            }
        });
        offerBookService.getOffers().forEach(this::onOfferAdded);

        // TODO for dev testing
        /*user.getPaymentAccounts().forEach(e -> {
            user.addMarketAlertFilter(new MarketAlertFilter(e, 200, 500));
        });*/
    }

    private void onOfferAdded(Offer offer) {
        String currencyCode = offer.getCurrencyCode();
        MarketPrice marketPrice = priceFeedService.getMarketPrice(currencyCode);
        Price offerPrice = offer.getPrice();
        if (marketPrice != null && offerPrice != null) {
            boolean isSellOffer = offer.getDirection() == OfferPayload.Direction.SELL;
            String shortId = offer.getShortId();
            boolean isFiatCurrency = CurrencyUtil.isFiatCurrency(currencyCode);
            user.getMarketAlertFilters().stream()
                    .filter(filter -> {
                        PaymentMethod paymentMethod = filter.getPaymentAccount().getPaymentMethod();
                        return offer.getPaymentMethod().equals(paymentMethod);
                    })
                    .forEach(filter -> {
                        int highPercentage = filter.getHighPercentage();
                        int lowPercentage = -1 * filter.getLowPercentage();
                        double marketPriceAsDouble1 = marketPrice.getPrice();
                        int precision = CurrencyUtil.isCryptoCurrency(currencyCode) ?
                                Altcoin.SMALLEST_UNIT_EXPONENT :
                                Fiat.SMALLEST_UNIT_EXPONENT;
                        double marketPriceAsDouble = MathUtils.scaleUpByPowerOf10(marketPriceAsDouble1, precision);
                        double offerPriceValue = offerPrice.getValue();
                        double ratio = offerPriceValue / marketPriceAsDouble;
                        ratio = 1 - ratio;
                        if (isFiatCurrency && isSellOffer)
                            ratio *= -1;
                        else if (!isFiatCurrency && !isSellOffer)
                            ratio *= -1;

                        ratio = ratio * 10000;
                        if (ratio > highPercentage || ratio < lowPercentage) {
                            String direction = isSellOffer ? Res.get("shared.sell") : Res.get("shared.buy");
                            String marketDir;
                            if (isFiatCurrency) {
                                if (isSellOffer) {
                                    marketDir = ratio > 0 ?
                                            Res.get("account.notifications.marketAlert.message.msg.above") :
                                            Res.get("account.notifications.marketAlert.message.msg.below");
                                } else {
                                    marketDir = ratio < 0 ?
                                            Res.get("account.notifications.marketAlert.message.msg.above") :
                                            Res.get("account.notifications.marketAlert.message.msg.below");
                                }
                            } else {
                                if (isSellOffer) {
                                    marketDir = ratio < 0 ?
                                            Res.get("account.notifications.marketAlert.message.msg.above") :
                                            Res.get("account.notifications.marketAlert.message.msg.below");
                                } else {
                                    marketDir = ratio > 0 ?
                                            Res.get("account.notifications.marketAlert.message.msg.above") :
                                            Res.get("account.notifications.marketAlert.message.msg.below");
                                }
                            }

                            ratio = Math.abs(ratio);
                            String msg = Res.get("account.notifications.marketAlert.message.msg",
                                    direction,
                                    formatter.getCurrencyPair(currencyCode),
                                    formatter.formatPrice(offerPrice),
                                    formatter.formatToPercentWithSymbol(ratio / 10000d),
                                    marketDir,
                                    Res.get(offer.getPaymentMethod().getId()),
                                    shortId);
                            MobileMessage message = new MobileMessage(Res.get("account.notifications.marketAlert.message.title"),
                                    msg,
                                    shortId,
                                    MobileMessageType.MARKET);
                            try {
                                mobileNotificationService.sendMessage(message);
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
        }
    }

    public static MobileMessage getTestMsg() {
        String shortId = UUID.randomUUID().toString().substring(0, 8);
        return new MobileMessage(Res.get("account.notifications.marketAlert.message.title"),
                "A new 'sell BTC/USD' offer with price 6019.2744 (5.36% below market price) and payment method " +
                        "'Perfect Money' was published to the Bisq offerbook.\n" +
                        "Offer ID: wygiaw.",
                shortId,
                MobileMessageType.MARKET);
    }
}
