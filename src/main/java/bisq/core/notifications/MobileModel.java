package bisq.core.notifications;

import javax.inject.Inject;

import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Data
@Slf4j
public class MobileModel {
    public static final String PHONE_SEPARATOR_ESCAPED = "\\|"; // see https://stackoverflow.com/questions/5675704/java-string-split-not-returning-the-right-values
    public static final String PHONE_SEPARATOR_WRITING = "|";

    public enum OS {
        UNDEFINED(""),
        IOS("BisqPhoneiOS"),
        IOS_DEV("BisqPhoneiOSDev"),
        ANDROID("BisqPhoneAndroid");

        @Getter
        private String magicString;

        OS(String magicString) {
            this.magicString = magicString;
        }
    }

    @Nullable
    private OS os;
    @Nullable
    private String key;
    @Nullable
    private String token;

    @Inject
    public MobileModel() {
    }

    public void reset() {
        os = null;
        key = null;
        token = null;
    }

    public void applyKeyAndToken(String keyAndToken) {
        log.info("phoneId={}", keyAndToken);

        String[] tokens = keyAndToken.split(PHONE_SEPARATOR_ESCAPED);
        String token0 = tokens[0];
        key = tokens[1];
        token = tokens[2];
        if (token0.equals(OS.IOS.getMagicString()))
            os = OS.IOS;
        else if (token0.equals(OS.IOS_DEV.getMagicString()))
            os = OS.IOS_DEV;
        else if (token0.equals(OS.ANDROID.getMagicString()))
            os = OS.ANDROID;
    }
}
