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

import bisq.core.user.Preferences;

import bisq.network.NetworkOptionKeys;
import bisq.network.http.HttpClient;

import bisq.common.app.Version;

import com.google.gson.Gson;

import com.google.inject.Inject;

import javax.inject.Named;

import org.apache.commons.codec.binary.Hex;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.util.UUID;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;

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
    @Getter
    private final MobileModel mobileModel;

    @Getter
    private boolean setupConfirmationSent;
    @Getter
    private BooleanProperty useSoundProperty = new SimpleBooleanProperty();
    @Getter
    private BooleanProperty useTradeNotificationsProperty = new SimpleBooleanProperty();
    @Getter
    private BooleanProperty useMarketNotificationsProperty = new SimpleBooleanProperty();
    @Getter
    private BooleanProperty usePriceNotificationsProperty = new SimpleBooleanProperty();

    @Inject
    public MobileNotificationService(Preferences preferences,
                                     MobileMessageEncryption mobileMessageEncryption,
                                     MobileNotificationValidator mobileNotificationValidator,
                                     MobileModel mobileModel,
                                     HttpClient httpClient,
                                     @Named(NetworkOptionKeys.USE_LOCALHOST_FOR_P2P) Boolean useLocalHost) {
        this.preferences = preferences;
        this.mobileMessageEncryption = mobileMessageEncryption;
        this.mobileNotificationValidator = mobileNotificationValidator;
        this.httpClient = httpClient;
        this.mobileModel = mobileModel;

        httpClient.setBaseUrl(useLocalHost ? DEV_URL : URL);
        httpClient.setIgnoreSocks5Proxy(false);
    }

    public void onAllServicesInitialized() {
        String keyAndToken = preferences.getPhoneKeyAndToken();
        if (mobileNotificationValidator.isValid(keyAndToken)) {
            setupConfirmationSent = true;
            mobileModel.applyKeyAndToken(keyAndToken);
            mobileMessageEncryption.setKey(mobileModel.getKey());
        }
        useTradeNotificationsProperty.set(preferences.isUseTradeNotifications());
        useMarketNotificationsProperty.set(preferences.isUseMarketNotifications());
        usePriceNotificationsProperty.set(preferences.isUsePriceNotifications());
        useSoundProperty.set(preferences.isUseSoundForMobileNotifications());
    }

    public void sendMessage(MobileMessage message) throws Exception {
        sendMessage(message, useSoundProperty.get());
    }

    public void applyKeyAndToken(String keyAndToken) {
        if (mobileNotificationValidator.isValid(keyAndToken)) {
            mobileModel.applyKeyAndToken(keyAndToken);
            mobileMessageEncryption.setKey(mobileModel.getKey());
            preferences.setPhoneKeyAndToken(keyAndToken);
            if (!setupConfirmationSent) {
                try {
                    sendConfirmationMessage();
                    setupConfirmationSent = true;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void sendMessage(MobileMessage message, boolean useSound) throws Exception {
        log.error("sendMessage\n" +
                "Title: " + message.getTitle() + "\nMessage: " + message.getMessage());
        if (mobileModel.getKey() != null) {
            boolean doSend;
            switch (message.getMobileMessageType()) {
                case SETUP_CONFIRMATION:
                    doSend = true;
                    break;
                case OFFER:
                case TRADE:
                case DISPUTE:
                    doSend = useTradeNotificationsProperty.get();
                    break;
                case PRICE:
                    doSend = usePriceNotificationsProperty.get();
                    break;
                case MARKET:
                    doSend = useMarketNotificationsProperty.get();
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
                log.info("json " + json);

                // What is ptext? bom byte? https://en.wikipedia.org/wiki/Byte_order_mark
              /*  byte[] ptext = json.getBytes(ISO_8859_1);
                json = new String(ptext, UTF_8);*/

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

    public void sendEraseMessage() throws Exception {
        MobileMessage message = new MobileMessage("",
                "",
                MobileMessageType.ERASE);
        sendMessage(message, false);
    }

    public void reset() {
        mobileModel.reset();
        preferences.setPhoneKeyAndToken(null);
        setupConfirmationSent = false;
    }


    private void sendConfirmationMessage() throws Exception {
        MobileMessage message = new MobileMessage("",
                "",
                MobileMessageType.SETUP_CONFIRMATION);
        sendMessage(message, true);
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
        msg += MobileModel.PHONE_SEPARATOR_WRITING + iv + MobileModel.PHONE_SEPARATOR_WRITING + cipher;
        boolean isAndroid = mobileModel.getOs() == MobileModel.OS.ANDROID;
        boolean isProduction = mobileModel.getOs() == MobileModel.OS.IOS;

        checkNotNull(mobileModel.getToken(), "mobileModel.getToken() must not be null");
        String tokenAsHex = Hex.encodeHexString(mobileModel.getToken().getBytes("UTF-8"));
        String msgAsHex = Hex.encodeHexString(msg.getBytes("UTF-8"));
        String param = "relay?" +
                "isAndroid=" + isAndroid +
                "&isProduction=" + isProduction +
                "&isContentAvailable=" + mobileModel.isContentAvailable() +
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

    private boolean isContentAvailable() {
        // phone descriptors
        /*
        iPod Touch 5
        iPod Touch 6
        iPhone 4
        iPhone 4s
        iPhone 5
        iPhone 5c
        iPhone 5s
        iPhone 6
        iPhone 6 Plus
        iPhone 6s
        iPhone 6s Plus
        iPhone 7
        iPhone 7 Plus
        iPhone SE
        iPhone 8
        iPhone 8 Plus
        iPhone X
        iPad 2
        iPad 3
        iPad 4
        iPad Air
        iPad Air 2
        iPad 5
        iPad 6
        iPad Mini
        iPad Mini 2
        iPad Mini 3
        iPad Mini 4
        iPad Pro 9.7 Inch
        iPad Pro 12.9 Inch
        iPad Pro 12.9 Inch 2. Generation
        iPad Pro 10.5 Inch
        */
        if (mobileModel.getDescriptor() != null) {

            String[] tokens = mobileModel.getDescriptor().split(" ");
            if (tokens.length >= 1) {
                String model = tokens[0];
                if ((model.equals("iPhone"))) {
                    String versionString = tokens[1];
                    versionString = versionString.substring(0, 1);
                    try {
                        int version = Integer.parseInt(versionString);
                        // iPhone 6 does not support isContentAvailable, iPhone 7 does.
                        // We don't know for other versions, but lets assume all below iPhone 7 are failing.
                        // SE we don't know as well
                        return version > 6;
                    } catch (Throwable ignore) {
                    }
                } else {
                    return (model.equals("iPad")) && tokens[1].equals("Pro");
                }
            }
        }
        return false;
    }
}
