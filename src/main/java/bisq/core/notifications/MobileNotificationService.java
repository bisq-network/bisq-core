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

package bisq.core.notifications;

import bisq.core.arbitration.Dispute;
import bisq.core.arbitration.DisputeManager;
import bisq.core.arbitration.messages.DisputeCommunicationMessage;
import bisq.core.offer.OpenOffer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.trade.Trade;
import bisq.core.trade.TradeManager;
import bisq.core.user.Preferences;

import bisq.network.NetworkOptionKeys;
import bisq.network.http.HttpClient;
import bisq.network.p2p.P2PService;

import bisq.common.app.Version;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;

import com.google.gson.Gson;

import com.google.inject.Inject;

import javax.inject.Named;

import org.apache.commons.codec.binary.Hex;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;

import javafx.collections.ListChangeListener;

import java.util.UUID;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
public class MobileNotificationService {
    private static final String DEV_URL = "http://localhost:8888/";
    private static final String URL = "...onion url"; //TODO
    private static final String BISQ_MESSAGE_IOS_MAGIC = "BisqMessageiOS";
    private static final String BISQ_MESSAGE_ANDROID_MAGIC = "BisqMessageAndroid";

    private final Preferences preferences;
    private final MobileMessageEncryption mobileMessageEncryption;
    private final MobileNotificationValidator mobileNotificationValidator;
    private final HttpClient httpClient;
    private final MobileModel mobileModel;
    private final PubKeyRing pubKeyRing;
    private final P2PService p2PService;
    @Getter
    private boolean setupConfirmationSent;
    @Getter
    private BooleanProperty useSoundProperty = new SimpleBooleanProperty();
    @Getter
    private BooleanProperty useTradeNotificationsProperty = new SimpleBooleanProperty();

    @Inject
    public MobileNotificationService(Preferences preferences,
                                     MobileMessageEncryption mobileMessageEncryption,
                                     MobileNotificationValidator mobileNotificationValidator,
                                     MobileModel mobileModel,
                                     HttpClient httpClient,
                                     OpenOfferManager openOfferManager,
                                     TradeManager tradeManager,
                                     DisputeManager disputeManager,
                                     KeyRing keyRing,
                                     P2PService p2PService,
                                     @Named(NetworkOptionKeys.USE_LOCALHOST_FOR_P2P) Boolean useLocalHost) {
        this.preferences = preferences;
        this.mobileMessageEncryption = mobileMessageEncryption;
        this.mobileNotificationValidator = mobileNotificationValidator;
        this.httpClient = httpClient;
        this.mobileModel = mobileModel;
        this.pubKeyRing = keyRing.getPubKeyRing();
        this.p2PService = p2PService;

        httpClient.setBaseUrl(useLocalHost ? DEV_URL : URL);
        httpClient.setIgnoreSocks5Proxy(false);

        openOfferManager.getObservableList().addListener((ListChangeListener<OpenOffer>) c -> {
            c.next();
            if (c.wasRemoved()) {
                c.getRemoved().forEach(this::setOpenOfferListener);
            }
        });
        openOfferManager.getObservableList().forEach(this::setOpenOfferListener);

        tradeManager.getTradableList().addListener((ListChangeListener<Trade>) c -> {
            c.next();
            if (c.wasAdded()) {
                c.getAddedSubList().forEach(this::setTradePhaseListener);
            }
        });
        tradeManager.getTradableList().forEach(this::setTradePhaseListener);


        disputeManager.getDisputesAsObservableList().addListener((ListChangeListener<Dispute>) c -> {
            c.next();
            if (c.wasAdded()) {
                c.getAddedSubList().forEach(this::setDisputeListener);
            }
        });
        disputeManager.getDisputesAsObservableList().forEach(this::setDisputeListener);
    }

    private void setOpenOfferListener(OpenOffer openOffer) {
        //TODO use weak ref or remove listener
        log.info("We got a offer removed. id={}, state={}", openOffer.getId(), openOffer.getState());
        if (openOffer.getState() == OpenOffer.State.RESERVED) {
            String msg = "A trader has taken your open offer with ID " + openOffer.getShortId();
            MobileMessage message = new MobileMessage("Offer got taken",
                    msg,
                    openOffer.getShortId(),
                    MobileMessageType.TRADE);
            try {
                sendMessage(message, useSoundProperty.get());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
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
                        sendMessage(message, useSoundProperty.get());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
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
        if (!dispute.getDisputeCommunicationMessages().isEmpty())
            setDisputeCommunicationMessage(dispute.getDisputeCommunicationMessages().get(0));
    }

    private void setDisputeCommunicationMessage(DisputeCommunicationMessage disputeMsg) {
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
                sendMessage(message, useSoundProperty.get());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void init() {
        String keyAndToken = preferences.getPhoneKeyAndToken();
        if (mobileNotificationValidator.isValid(keyAndToken)) {
            setupConfirmationSent = true;
            mobileModel.applyKeyAndToken(keyAndToken);
            mobileMessageEncryption.setKey(mobileModel.getKey());
        }
        useTradeNotificationsProperty.set(preferences.isUseTradeNotifications());
        useSoundProperty.set(preferences.isUseSoundForMobileNotifications());
    }

    public void applyKeyAndToken(String keyAndToken) {
        if (mobileNotificationValidator.isValid(keyAndToken)) {
            mobileModel.applyKeyAndToken(keyAndToken);
            mobileMessageEncryption.setKey(mobileModel.getKey());
            preferences.setPhoneKeyAndToken(keyAndToken);
            if (!setupConfirmationSent) {
                try {
                    sendConfirmationMessage(useSoundProperty.get());
                    setupConfirmationSent = true;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void reset() {
        mobileModel.reset();
        preferences.setPhoneKeyAndToken(null);
        setupConfirmationSent = false;
    }

    public void sendMessage(MobileMessage message, boolean useSound) throws Exception {
        if (mobileModel.getKey() != null) {
            boolean doSend = false;
            switch (message.getMsgType()) {
                case SETUP_CONFIRMATION:
                    doSend = true;
                    break;
                case TRADE:
                case DISPUTE:
                    doSend = useTradeNotificationsProperty.get();
                    break;
                case FINANCIAL:
                    // TODO not impl
                    break;
                case ERASE:
                    doSend = true;
                    break;
                default:
                    doSend = false;
            }
            if (doSend) {
                log.info("sendMessage message={}", message);
                Gson gson = new Gson();
                String json = gson.toJson(message);
                log.error("json " + json);

                // What is ptext? bom byte? https://en.wikipedia.org/wiki/Byte_order_mark
                byte[] ptext = json.getBytes(ISO_8859_1);
                json = new String(ptext, UTF_8);

                StringBuilder padded = new StringBuilder(json);
                while (padded.length() % 16 != 0) {
                    padded.append(" ");
                }
                json = padded.toString();

                // generate 16 random characters for iv
                String uuid = UUID.randomUUID().toString();
                uuid = uuid.replace("-", "");
                String iv = uuid.substring(0, 16);

                String cipher = mobileMessageEncryption.encrypt(json, iv);
                log.info("key = " + mobileModel.getKey());
                log.info("iv = " + iv);
                log.info("encryptedJson = " + cipher);
                doSendMessage(iv, cipher, useSound);
            }
        }
    }

    public void sendWipeOutMessage() throws Exception {
        MobileMessage message = new MobileMessage("",
                "",
                MobileMessageType.ERASE);
        sendMessage(message, false);
    }

    private void sendConfirmationMessage(boolean useSound) throws Exception {
        MobileMessage message = new MobileMessage("",
                "",
                MobileMessageType.SETUP_CONFIRMATION);
        sendMessage(message, false);
    }

    private void doSendMessage(String iv, String cipher, boolean useSound) throws Exception {
        String msg;
        if (mobileModel.getOs() == null)
            throw new RuntimeException("No mobileModel OS set");

        switch (mobileModel.getOs()) {
            case IOS:
                msg = BISQ_MESSAGE_IOS_MAGIC;
                break;
            case IOS_DEV:
                msg = BISQ_MESSAGE_IOS_MAGIC;
                break;
            case ANDROID:
                msg = BISQ_MESSAGE_ANDROID_MAGIC;
                break;
            case UNDEFINED:
            default:
                throw new RuntimeException("No mobileModel OS set");
        }
        msg = msg + MobileModel.PHONE_SEPARATOR_WRITING + iv + MobileModel.PHONE_SEPARATOR_WRITING + cipher;
        boolean isAndroid = mobileModel.getOs() == MobileModel.OS.ANDROID;
        String tokenAsHex = Hex.encodeHexString(mobileModel.getToken().getBytes("UTF-8"));
        String msgAsHex = Hex.encodeHexString(msg.getBytes("UTF-8"));
        String param = "relay?" +
                "isAndroid=" + isAndroid +
                "&snd=" + useSound +
                "&token=" + tokenAsHex + "&" +
                "msg=" + msgAsHex;

        log.info("Send: token={}", mobileModel.getToken());
        log.info("Send: msg={}", msg);
        log.info("Send: isAndroid={}\nuseSound={}\ntokenAsHex={}\nmsgAsHex={}",
                isAndroid, useSound, tokenAsHex, msgAsHex);

        String result = httpClient.requestWithGET(param, "User-Agent", "bisq/" +
                Version.VERSION + ", uid:" + httpClient.getUid());
        log.info("result: " + result);
    }
}
