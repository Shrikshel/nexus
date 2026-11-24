package com.nexus.platform.backup;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Robust, authenticated AES-256-GCM encryption service for Nexus backup archives.
 * Uses PBKDF2WithHmacSHA256 key derivation with 65,536 iterations and 128-bit authentication tags.
 */
@Service
public class BackupCryptoService {

    public static final byte[] MAGIC_HEADER = "NEXUS_ENC_V1".getBytes(StandardCharsets.US_ASCII);
    public static final int SALT_LENGTH = 16;
    public static final int IV_LENGTH = 12;
    public static final int GCM_TAG_LENGTH = 128;
    public static final int PBKDF2_ITERATIONS = 65536;
    public static final int KEY_LENGTH_BITS = 256;

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final String KDF_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Checks whether a file starts with the Nexus encryption magic header.
     */
    public boolean isEncrypted(Path path) {
        if (path == null || !Files.exists(path) || Files.isDirectory(path)) {
            return false;
        }
        try (InputStream is = Files.newInputStream(path)) {
            return isEncrypted(is);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks whether an input stream starts with the Nexus encryption magic header.
     */
    public boolean isEncrypted(InputStream is) {
        if (is == null) {
            return false;
        }
        try {
            byte[] header = is.readNBytes(MAGIC_HEADER.length);
            return Arrays.equals(header, MAGIC_HEADER);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Encrypts plaintext bytes using AES-256-GCM with the provided password.
     */
    public byte[] encrypt(byte[] plaintext, char[] password) {
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Encryption password must not be empty");
        }
        try {
            byte[] salt = new byte[SALT_LENGTH];
            SECURE_RANDOM.nextBytes(salt);

            byte[] iv = new byte[IV_LENGTH];
            SECURE_RANDOM.nextBytes(iv);

            SecretKey key = deriveKey(password, salt);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec);

            byte[] ciphertext = cipher.doFinal(plaintext);

            ByteArrayOutputStream baos = new ByteArrayOutputStream(MAGIC_HEADER.length + SALT_LENGTH + IV_LENGTH + ciphertext.length);
            baos.write(MAGIC_HEADER);
            baos.write(salt);
            baos.write(iv);
            baos.write(ciphertext);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt backup archive: " + e.getMessage(), e);
        }
    }

    /**
     * Decrypts an encrypted Nexus backup archive using AES-256-GCM.
     */
    public byte[] decrypt(byte[] encryptedData, char[] password) {
        if (password == null || password.length == 0) {
            throw new IllegalArgumentException("Decryption password must not be empty");
        }
        if (encryptedData == null || encryptedData.length < MAGIC_HEADER.length + SALT_LENGTH + IV_LENGTH) {
            throw new IllegalArgumentException("Invalid encrypted backup archive (file is truncated or empty)");
        }

        byte[] header = Arrays.copyOfRange(encryptedData, 0, MAGIC_HEADER.length);
        if (!Arrays.equals(header, MAGIC_HEADER)) {
            throw new IllegalArgumentException("Backup archive is not in encrypted Nexus format (missing magic header)");
        }

        int offset = MAGIC_HEADER.length;
        byte[] salt = Arrays.copyOfRange(encryptedData, offset, offset + SALT_LENGTH);
        offset += SALT_LENGTH;

        byte[] iv = Arrays.copyOfRange(encryptedData, offset, offset + IV_LENGTH);
        offset += IV_LENGTH;

        byte[] ciphertext = Arrays.copyOfRange(encryptedData, offset, encryptedData.length);

        try {
            SecretKey key = deriveKey(password, salt);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);

            return cipher.doFinal(ciphertext);
        } catch (javax.crypto.AEADBadTagException e) {
            throw new SecurityException("Decryption failed: Incorrect password or backup archive is corrupted/tampered", e);
        } catch (Exception e) {
            throw new SecurityException("Failed to decrypt backup archive: " + e.getMessage(), e);
        }
    }

    /**
     * Generates a cryptographically strong, human-readable random passphrase (e.g. for Telegram backups).
     */
    public String generateSecurePassword(int length) {
        final String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%&*";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(SECURE_RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private SecretKey deriveKey(char[] password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
        SecretKeyFactory factory = SecretKeyFactory.getInstance(KDF_ALGORITHM);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }
}
