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
    @Getter
    private boolean setupConfirmationSent;
    @Getter
    private BooleanProperty useSoundProperty = new SimpleBooleanProperty();

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

    public void init() {
        String keyAndToken = preferences.getPhoneKeyAndToken();
        if (mobileNotificationValidator.isValid(keyAndToken)) {
            setupConfirmationSent = true;
            mobileModel.applyKeyAndToken(keyAndToken);
            mobileMessageEncryption.setKey(mobileModel.getKey());
        }
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
        log.info("sendMessage message={}", message);
        Gson gson = new Gson();
        String json = gson.toJson(message);

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
