package br.com.lcmtech.contentguard;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Local parental PIN. A random salt and PBKDF2 hash are stored, never the plaintext PIN. */
public final class PinStore {
    private static final int ITERATIONS = 120_000;
    private final SharedPreferences prefs;
    public PinStore(Context context) {
        prefs = context.getSharedPreferences("content_guard_parent_auth", Context.MODE_PRIVATE);
    }
    public boolean hasPin() { return prefs.contains("digest"); }
    public boolean isValidFormat(String pin) { return pin != null && pin.matches("[0-9]{4,8}"); }

    public boolean createPin(String pin) {
        if (hasPin() || !isValidFormat(pin)) return false;
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        try {
            byte[] result = calculate(pin, salt);
            return prefs.edit()
                    .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
                    .putString("digest", Base64.encodeToString(result, Base64.NO_WRAP))
                    .commit();
        } catch (Exception ignored) { return false; }
    }

    public boolean verify(String pin) {
        if (!isValidFormat(pin)) return false;
        try {
            byte[] salt = Base64.decode(prefs.getString("salt", ""), Base64.NO_WRAP);
            byte[] saved = Base64.decode(prefs.getString("digest", ""), Base64.NO_WRAP);
            return java.security.MessageDigest.isEqual(saved, calculate(pin, salt));
        } catch (Exception ignored) { return false; }
    }

    private static byte[] calculate(String pin, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }
}
