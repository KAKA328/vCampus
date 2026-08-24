package cn.vcampus.user;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** PBKDF2 password hashing for the demo repository; replace only with a reviewed equivalent. */
final class PasswordHasher {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;
    private final SecureRandom random = new SecureRandom();

    String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        return encode(salt) + ":" + encode(derive(password, salt));
    }

    boolean matches(String password, String encoded) {
        String[] parts = encoded == null ? new String[0] : encoded.split(":", 2);
        if (parts.length != 2) return false;
        try {
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] expected = Base64.getDecoder().decode(parts[1]);
            return MessageDigest.isEqual(expected, derive(password, salt));
        } catch (IllegalArgumentException invalidEncoding) {
            return false;
        }
    }

    private byte[] derive(String password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("password hashing is unavailable", failure);
        } finally {
            spec.clearPassword();
        }
    }

    private static String encode(byte[] bytes) { return Base64.getEncoder().encodeToString(bytes); }
}
