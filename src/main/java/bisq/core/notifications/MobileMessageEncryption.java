package bisq.core.notifications;

import javax.inject.Inject;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.security.NoSuchAlgorithmException;

import lombok.extern.slf4j.Slf4j;



import com.sun.org.apache.xml.internal.security.utils.Base64;

@Slf4j
public class MobileMessageEncryption {

    private IvParameterSpec ivspec;
    private SecretKeySpec keyspec;
    private Cipher cipher;

    @Inject
    public MobileMessageEncryption() {
    }

    public void setKey(String key) {
        keyspec = new SecretKeySpec(key.getBytes(), "AES");
        try {
            cipher = Cipher.getInstance("AES/CBC/NOPadding");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
            e.printStackTrace();
        }
    }

    public String encrypt(String valueToEncrypt, String iv) throws Exception {
        while (valueToEncrypt.length() % 16 != 0) {
            valueToEncrypt = valueToEncrypt + " ";
        }

        if (iv.length() != 16) {
            throw new Exception("iv not 16 characters");
        }
        ivspec = new IvParameterSpec(iv.getBytes());
        byte[] encryptedBytes = doEncrypt(valueToEncrypt, ivspec);
        return Base64.encode(encryptedBytes);
    }

    private byte[] doEncrypt(String text, IvParameterSpec ivSpec) throws Exception {
        if (text == null || text.length() == 0) {
            throw new Exception("Empty string");
        }

        byte[] encrypted;
        try {
            cipher.init(Cipher.ENCRYPT_MODE, keyspec, ivSpec);
            encrypted = cipher.doFinal(text.getBytes());
        } catch (Exception e) {
            throw new Exception("[encrypt] " + e.getMessage());
        }
        return encrypted;
    }
}
