package com.nexus.platform.backup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackupCryptoServiceTest {

    private final BackupCryptoService cryptoService = new BackupCryptoService();

    @Test
    @DisplayName("Successfully encrypts and decrypts payload using AES-256-GCM")
    void testEncryptDecryptSuccess() {
        byte[] original = "NexusEnterpriseBackupDataPayload-123456789".getBytes(StandardCharsets.UTF_8);
        char[] password = "MasterSecureSecretPassword2026!".toCharArray();

        byte[] encrypted = cryptoService.encrypt(original, password);

        assertThat(encrypted).isNotNull();
        assertThat(encrypted.length).isGreaterThan(original.length);
        assertThat(cryptoService.isEncrypted(new ByteArrayInputStream(encrypted))).isTrue();

        byte[] decrypted = cryptoService.decrypt(encrypted, password);
        assertThat(decrypted).isEqualTo(original);
        assertThat(new String(decrypted, StandardCharsets.UTF_8)).isEqualTo("NexusEnterpriseBackupDataPayload-123456789");
    }

    @Test
    @DisplayName("Throws SecurityException when decrypting with wrong password")
    void testDecryptWithWrongPassword() {
        byte[] original = "SecretNexusData".getBytes(StandardCharsets.UTF_8);
        char[] correctPassword = "CorrectPassword123".toCharArray();
        char[] wrongPassword = "WrongPassword456".toCharArray();

        byte[] encrypted = cryptoService.encrypt(original, correctPassword);

        assertThatThrownBy(() -> cryptoService.decrypt(encrypted, wrongPassword))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Decryption failed: Incorrect password");
    }

    @Test
    @DisplayName("Throws IllegalArgumentException when decrypting non-encrypted data")
    void testDecryptNonEncryptedData() {
        byte[] plainData = "PK\003\004ThisIsNotAnEncryptedNexusArchiveWithSufficientLengthToPassHeaderChecks".getBytes(StandardCharsets.UTF_8);
        char[] password = "AnyPassword".toCharArray();

        assertThatThrownBy(() -> cryptoService.decrypt(plainData, password))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing magic header");
    }

    @Test
    @DisplayName("Generates cryptographically random passwords with specified length")
    void testGenerateSecurePassword() {
        String pass1 = cryptoService.generateSecurePassword(16);
        String pass2 = cryptoService.generateSecurePassword(16);
        String pass3 = cryptoService.generateSecurePassword(24);

        assertThat(pass1).hasSize(16);
        assertThat(pass2).hasSize(16);
        assertThat(pass3).hasSize(24);
        assertThat(pass1).isNotEqualTo(pass2);
    }
}
