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
import bisq.core.offer.OpenOffer;
import bisq.core.offer.OpenOfferManager;

import javax.inject.Inject;

import javafx.collections.ListChangeListener;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OpenOfferTakenEvents {
    private MobileNotificationService mobileNotificationService;

    @Inject
    public OpenOfferTakenEvents(OpenOfferManager openOfferManager, MobileNotificationService mobileNotificationService) {
        this.mobileNotificationService = mobileNotificationService;
        openOfferManager.getObservableList().addListener((ListChangeListener<OpenOffer>) c -> {
            c.next();
            if (c.wasRemoved())
                c.getRemoved().forEach(this::onOpenOfferRemoved);
        });
        openOfferManager.getObservableList().forEach(this::onOpenOfferRemoved);
    }

    private void onOpenOfferRemoved(OpenOffer openOffer) {
        log.info("We got a offer removed. id={}, state={}", openOffer.getId(), openOffer.getState());
        if (openOffer.getState() == OpenOffer.State.RESERVED) {
            String msg = "A trader has taken your offer with ID " + openOffer.getShortId();
            MobileMessage message = new MobileMessage("Offer got taken",
                    msg,
                    openOffer.getShortId(),
                    MobileMessageType.TRADE);
            try {
                mobileNotificationService.sendMessage(message);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void onAllServicesInitialized() {

    }
}
