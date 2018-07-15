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

package bisq.core.notifications.presentation;

import bisq.core.offer.Offer;
import bisq.core.offer.OfferBookService;

import javax.inject.Inject;

public class MarketAlerts {
    @Inject
    public MarketAlerts(
            OfferBookService offerBookService) {
        offerBookService.addOfferBookChangedListener(new OfferBookService.OfferBookChangedListener() {
            @Override
            public void onAdded(Offer offer) {
                onOfferAdded(offer);
            }

            @Override
            public void onRemoved(Offer offer) {
                onOfferRemoved(offer);
            }
        });
        offerBookService.getOffers().forEach(this::onOfferAdded);
    }

    private void onOfferAdded(Offer offer) {
       /* String msg = "A new offer arrived which matches your filter criteria" + offer.getPrice();
        MobileMessage message = new MobileMessage("Offer got taken",
                msg,
                offer.getShortId(),
                MobileMessageType.TRADE);
        try {
            sendMessage(message, useSoundProperty.get());
        } catch (Exception e) {
            e.printStackTrace();
        }*/
    }

    private void onOfferRemoved(Offer offer) {

    }
}
