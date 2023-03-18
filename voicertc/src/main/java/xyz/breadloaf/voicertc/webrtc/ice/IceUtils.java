package xyz.breadloaf.voicertc.webrtc.ice;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;

public class IceUtils {
    public static String ServerPWD;
    public static String ServerUfrag;

    public static void generateServerCredentials() throws NoSuchAlgorithmException {
        String chrs = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        SecureRandom secureRandom = SecureRandom.getInstanceStrong();
        ServerUfrag = secureRandom.ints(8, 0, chrs.length()).mapToObj(chrs::charAt).collect(StringBuilder::new, StringBuilder::append, StringBuilder::append).toString();
        ServerPWD = secureRandom.ints(32, 0, chrs.length()).mapToObj(chrs::charAt).collect(StringBuilder::new, StringBuilder::append, StringBuilder::append).toString();
    }

}
