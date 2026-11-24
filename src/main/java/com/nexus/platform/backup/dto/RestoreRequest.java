package com.nexus.platform.backup.dto;

import java.nio.file.Path;

public record RestoreRequest(
        Path archivePath,
        char[] password,
        boolean dryRun,
        boolean force,
        boolean rearm,
        boolean restoreSystem,
        boolean restoreUser
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Path archivePath;
        private char[] password;
        private boolean dryRun = false;
        private boolean force = false;
        private boolean rearm = false;
        private boolean restoreSystem = true;
        private boolean restoreUser = true;

        public Builder archivePath(Path archivePath) {
            this.archivePath = archivePath;
            return this;
        }

        public Builder password(char[] password) {
            this.password = password;
            return this;
        }

        public Builder dryRun(boolean dryRun) {
            this.dryRun = dryRun;
            return this;
        }

        public Builder force(boolean force) {
            this.force = force;
            return this;
        }

        public Builder rearm(boolean rearm) {
            this.rearm = rearm;
            return this;
        }

        public Builder restoreSystem(boolean restoreSystem) {
            this.restoreSystem = restoreSystem;
            return this;
        }

        public Builder restoreUser(boolean restoreUser) {
            this.restoreUser = restoreUser;
            return this;
        }

        public RestoreRequest build() {
            return new RestoreRequest(archivePath, password, dryRun, force, rearm, restoreSystem, restoreUser);
        }
    }
}
