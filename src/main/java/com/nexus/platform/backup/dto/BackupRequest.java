package com.nexus.platform.backup.dto;

import java.nio.file.Path;
import java.util.List;

public record BackupRequest(
        Path outputPath,
        boolean encrypt,
        char[] password,
        boolean includeSystem,
        boolean includeUser,
        boolean overwrite
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Path outputPath;
        private boolean encrypt;
        private char[] password;
        private boolean includeSystem = true;
        private boolean includeUser = true;
        private boolean overwrite = false;

        public Builder outputPath(Path outputPath) {
            this.outputPath = outputPath;
            return this;
        }

        public Builder encrypt(boolean encrypt) {
            this.encrypt = encrypt;
            return this;
        }

        public Builder password(char[] password) {
            this.password = password;
            return this;
        }

        public Builder includeSystem(boolean includeSystem) {
            this.includeSystem = includeSystem;
            return this;
        }

        public Builder includeUser(boolean includeUser) {
            this.includeUser = includeUser;
            return this;
        }

        public Builder overwrite(boolean overwrite) {
            this.overwrite = overwrite;
            return this;
        }

        public BackupRequest build() {
            return new BackupRequest(outputPath, encrypt, password, includeSystem, includeUser, overwrite);
        }
    }
}
