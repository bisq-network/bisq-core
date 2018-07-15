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

import bisq.core.locale.Res;
import bisq.core.notifications.MobileMessage;
import bisq.core.notifications.MobileMessageType;
import bisq.core.notifications.MobileNotificationService;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferBookService;
import bisq.core.user.User;

import javax.inject.Inject;

import java.util.UUID;

public class MarketAlerts {
    private final OfferBookService offerBookService;
    private final MobileNotificationService mobileNotificationService;

    @Inject
    public MarketAlerts(OfferBookService offerBookService, MobileNotificationService mobileNotificationService,
                        User user) {
        this.offerBookService = offerBookService;
        this.mobileNotificationService = mobileNotificationService;
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
    }

    private void onOfferAdded(Offer offer) {
        String shortId = offer.getShortId();
        MobileMessage message = new MobileMessage(Res.get("account.notifications.marketAlert.message.title"),
                Res.get("account.notifications.marketAlert.message.msg", offer.getPrice()),
                shortId,
                MobileMessageType.TRADE);
        try {
            mobileNotificationService.sendMessage(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static MobileMessage getTestMsg() {
        String shortId = UUID.randomUUID().toString().substring(0, 8);
        return new MobileMessage(Res.get("account.notifications.marketAlert.message.title"),
                Res.get("account.notifications.marketAlert.message.msg", "5644.34 BTC/USD"),
                shortId,
                MobileMessageType.MARKET);
    }
}
