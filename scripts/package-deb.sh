#!/bin/sh
# ==============================================================================
# Nexus Host Management Agent - Debian (.deb) Package Builder
#
# Packages the compiled native binary (target/nexus) into a standalone Debian
# package (.deb) containing the binary, systemd service unit, and default config.
#
# Usage:
#   ./scripts/package-deb.sh [VERSION] [BINARY_PATH]
#
# Examples:
#   ./scripts/package-deb.sh
#   ./scripts/package-deb.sh 1.0.0
#   ./scripts/package-deb.sh 1.0.0 nexus-linux-amd64
# ==============================================================================

set -e

BOLD='\033[1m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[0;33m'
RED='\033[0;31m'
NC='\033[0m'

info() { printf "${BLUE}[INFO]${NC} %s\n" "$1"; }
success() { printf "${GREEN}[SUCCESS]${NC} %s\n" "$1"; }
warn() { printf "${YELLOW}[WARN]${NC} %s\n" "$1"; }
error() { printf "${RED}[ERROR]${NC} %s\n" "$1" >&2; exit 1; }

VERSION="${1:-1.0.0}"
ARCH="amd64"
PKG_NAME="nexus"

# Locate compiled native binary
BINARY_SRC=""
if [ -n "$2" ] && [ -f "$2" ]; then
    BINARY_SRC="$2"
elif [ -f "target/nexus" ]; then
    BINARY_SRC="target/nexus"
elif [ -f "nexus-linux-amd64" ]; then
    BINARY_SRC="nexus-linux-amd64"
elif [ -f "./nexus" ]; then
    BINARY_SRC="./nexus"
else
    error "Compiled binary not found at target/nexus. Build first using: ./mvnw -Pnative package -DskipTests or pass binary path: ./scripts/package-deb.sh 1.0.0 <path/to/binary>"
fi

BUILD_DIR="$(mktemp -d 2>/dev/null || mktemp -d -t 'nexus-deb-build')"
trap 'rm -rf "$BUILD_DIR"' EXIT

info "Preparing Debian package structure for ${PKG_NAME}_${VERSION}_${ARCH}.deb..."

chmod 755 "$BUILD_DIR"

# 1. Directory layout
mkdir -p "$BUILD_DIR/DEBIAN"
chmod 755 "$BUILD_DIR/DEBIAN"
mkdir -p "$BUILD_DIR/usr/local/bin"
mkdir -p "$BUILD_DIR/lib/systemd/system"
mkdir -p "$BUILD_DIR/etc/nexus"

# 2. Copy binary
cp "$BINARY_SRC" "$BUILD_DIR/usr/local/bin/nexus"
chmod 755 "$BUILD_DIR/usr/local/bin/nexus"

# 3. Create default configuration template
cat <<'EOF' > "$BUILD_DIR/etc/nexus/config.yml"
# ==============================================================================
# Nexus Host Management Agent Configuration
# ==============================================================================

nexus:
  # Operational mode: cli or daemon
  mode: daemon

  # Default timeout for capabilities (e.g. 30s, 60s)
  default-timeout: 30s

  # Enable structured file audit logging (logs/nexus-audit.log)
  audit-enabled: true

  # Remote bi-directional Telegram management
  telegram:
    enabled: false
    bot-token: ""
    allowed-chat-id: 0
EOF
chmod 644 "$BUILD_DIR/etc/nexus/config.yml"

# 4. Create systemd service unit
cat <<'EOF' > "$BUILD_DIR/lib/systemd/system/nexus.service"
[Unit]
Description=Nexus Host Management Agent Daemon
After=network.target network-online.target
Wants=network-online.target

[Service]
Type=simple
ExecStart=/usr/local/bin/nexus start
ExecStop=/usr/local/bin/nexus stop
Restart=always
RestartSec=5s
TimeoutStopSec=15s
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF
chmod 644 "$BUILD_DIR/lib/systemd/system/nexus.service"

# 5. Control metadata
cat <<EOF > "$BUILD_DIR/DEBIAN/control"
Package: ${PKG_NAME}
Version: ${VERSION}
Section: admin
Priority: optional
Architecture: ${ARCH}
Maintainer: Shriko <dev@shriko.in>
Description: Nexus Host Management Agent
 Lightweight, cross-platform host management agent compiled ahead-of-time
 with GraalVM Native Image for sub-50ms startup and low memory footprint.
EOF

# 6. Post-install script
cat <<'EOF' > "$BUILD_DIR/DEBIAN/postinst"
#!/bin/sh
set -e
chmod +x /usr/local/bin/nexus 2>/dev/null || true
if command -v systemctl >/dev/null 2>&1; then
    systemctl daemon-reload 2>/dev/null || true
fi
exit 0
EOF
chmod 755 "$BUILD_DIR/DEBIAN/postinst"

# 7. Pre-removal script
cat <<'EOF' > "$BUILD_DIR/DEBIAN/prerm"
#!/bin/sh
set -e
if command -v systemctl >/dev/null 2>&1; then
    systemctl stop nexus.service 2>/dev/null || true
    systemctl disable nexus.service 2>/dev/null || true
fi
exit 0
EOF
chmod 755 "$BUILD_DIR/DEBIAN/prerm"

# 8. Build .deb package
OUTPUT_DIR="target"
mkdir -p "$OUTPUT_DIR"
DEB_FILE="${OUTPUT_DIR}/${PKG_NAME}_${VERSION}_${ARCH}.deb"

info "Building package with dpkg-deb..."
dpkg-deb --build --root-owner-group "$BUILD_DIR" "$DEB_FILE"

# Also symlink/copy as nexus-linux-amd64.deb for release asset naming consistency
cp "$DEB_FILE" "${OUTPUT_DIR}/nexus-linux-${ARCH}.deb"

# Generate SHA256 checksums
if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$DEB_FILE" > "${DEB_FILE}.sha256"
    sha256sum "${OUTPUT_DIR}/nexus-linux-${ARCH}.deb" > "${OUTPUT_DIR}/nexus-linux-${ARCH}.deb.sha256"
fi

success "Successfully built Debian package: $DEB_FILE"
success "Also generated release asset : ${OUTPUT_DIR}/nexus-linux-${ARCH}.deb"
printf "\nTo install this package on any host offline:\n"
printf "  ${BOLD}sudo dpkg -i %s${NC}\n" "$DEB_FILE"
printf "or using the offline installer script:\n"
printf "  ${BOLD}./scripts/install-offline.sh %s${NC}\n\n" "$DEB_FILE"
