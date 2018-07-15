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

import bisq.core.arbitration.Dispute;
import bisq.core.arbitration.DisputeManager;
import bisq.core.arbitration.messages.DisputeCommunicationMessage;
import bisq.core.notifications.MobileMessage;
import bisq.core.notifications.MobileMessageType;
import bisq.core.notifications.MobileNotificationService;

import bisq.network.p2p.P2PService;

import javax.inject.Inject;

import javafx.collections.ListChangeListener;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DisputeMsgEvents {
    private DisputeManager disputeManager;
    private final P2PService p2PService;
    private final MobileNotificationService mobileNotificationService;

    @Inject
    public DisputeMsgEvents(DisputeManager disputeManager, P2PService p2PService, MobileNotificationService mobileNotificationService) {
        this.disputeManager = disputeManager;
        this.p2PService = p2PService;
        this.mobileNotificationService = mobileNotificationService;
    }

    public void onAllServicesInitialized() {
        disputeManager.getDisputesAsObservableList().addListener((ListChangeListener<Dispute>) c -> {
            c.next();
            if (c.wasAdded()) {
                c.getAddedSubList().forEach(this::setDisputeListener);
            }
        });
        disputeManager.getDisputesAsObservableList().forEach(this::setDisputeListener);
    }

    private void setDisputeListener(Dispute dispute) {
        //TODO use weak ref or remove listener
        log.info("We got a dispute added. id={}, tradeId={}", dispute.getId(), dispute.getTradeId());
        dispute.getDisputeCommunicationMessages().addListener(new ListChangeListener<DisputeCommunicationMessage>() {
            @Override
            public void onChanged(Change<? extends DisputeCommunicationMessage> c) {
                log.info("We got a DisputeCommunicationMessage added. id={}, tradeId={}", dispute.getId(), dispute.getTradeId());
                c.next();
                if (c.wasAdded()) {
                    c.getAddedSubList().forEach(e -> setDisputeCommunicationMessage(e));
                }
            }
        });
        //TODO test
        //  if (dispute.getDisputeCommunicationMessages().size() == 1)
        //     setDisputeCommunicationMessage(dispute.getDisputeCommunicationMessages().get(0));
    }

    public void setDisputeCommunicationMessage(DisputeCommunicationMessage disputeMsg) {
        // TODO we need to prevent to send msg for old dispute messages again at restart
        // Maybe we need a new property in DisputeCommunicationMessage
        // As key is not set in initial iterations it seems we dont need an extra handling.
        // the mailbox msg is set a bit later so that triggers a notification, but not the old messages.

        // We only send msg in case we are not the sender
        if (!disputeMsg.getSenderNodeAddress().equals(p2PService.getAddress())) {
            String msg = "A dispute message for trade with ID " + disputeMsg.getShortId() + " arrived";
            MobileMessage message = new MobileMessage("Offer got taken",
                    msg,
                    disputeMsg.getShortId(),
                    MobileMessageType.DISPUTE);
            try {
                mobileNotificationService.sendMessage(message);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
