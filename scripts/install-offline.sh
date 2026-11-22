#!/bin/sh
# ==============================================================================
# Nexus Host Management Agent - Offline / Package Installer
#
# Designed to install Nexus completely offline using a pre-downloaded .deb
# package or standalone binary asset, bypassing all network access and authentication.
#
# Usage:
#   ./scripts/install-offline.sh [PACKAGE_OR_BINARY_FILE]
#
# Examples:
#   ./scripts/install-offline.sh nexus_1.0.0_amd64.deb
#   ./scripts/install-offline.sh nexus-linux-amd64
#   ./scripts/install-offline.sh                     # Auto-detects in current directory
#
# Options:
#   -y, --yes               Skip confirmation prompts
#   --extract-only          Extract binary from .deb without using system dpkg
#   --dry-run               Display actions without modifying the system
#
# Environment variables:
#   INSTALL_DIR="/path"     Target directory (default: /usr/local/bin or ~/.local/bin)
#   EXTRACT_ONLY=1          Extract binary without registering with dpkg
#   DRY_RUN=1               Dry run mode
# ==============================================================================

set -e

# Terminal colors
BOLD='\033[1m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[0;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

info() {
    printf "${BLUE}[INFO]${NC} %s\n" "$1"
}

success() {
    printf "${GREEN}[SUCCESS]${NC} %s\n" "$1"
}

warn() {
    printf "${YELLOW}[WARN]${NC} %s\n" "$1"
}

error() {
    printf "${RED}[ERROR]${NC} %s\n" "$1" >&2
    exit 1
}

command_exists() {
    command -v "$1" >/dev/null 2>&1
}

detect_os() {
    OS_RAW="$(uname -s)"
    case "$OS_RAW" in
        Linux*)   echo "linux" ;;
        Darwin*)  echo "darwin" ;;
        MINGW*|MSYS*|CYGWIN*) echo "windows" ;;
        *)
            error "Unsupported Operating System: $OS_RAW. Nexus currently supports Linux, macOS, and Windows."
            ;;
    esac
}

detect_arch() {
    ARCH_RAW="$(uname -m)"
    case "$ARCH_RAW" in
        x86_64|amd64)   echo "amd64" ;;
        arm64|aarch64)  echo "arm64" ;;
        *)
            error "Unsupported CPU architecture: $ARCH_RAW. Supported architectures: x86_64/amd64, arm64/aarch64."
            ;;
    esac
}

# Parse CLI arguments and flags
ASSET_FILE=""
EXTRACT_ONLY="${EXTRACT_ONLY:-0}"
DRY_RUN="${DRY_RUN:-0}"
ASSUME_YES="${ASSUME_YES:-0}"

for arg in "$@"; do
    case "$arg" in
        -y|--yes)          ASSUME_YES=1 ;;
        --extract-only)    EXTRACT_ONLY=1 ;;
        --dry-run)         DRY_RUN=1 ;;
        -*)                warn "Unrecognized option: $arg" ;;
        *)
            if [ -z "$ASSET_FILE" ]; then
                ASSET_FILE="$arg"
            fi
            ;;
    esac
done

printf "${BOLD}====================================================${NC}\n"
printf "${BOLD}   Nexus: Offline Host Agent Installer             ${NC}\n"
printf "${BOLD}====================================================${NC}\n\n"

OS="$(detect_os)"
ARCH="$(detect_arch)"
info "Detected platform: ${OS} (${ARCH})"

# Auto-discover local asset if not passed explicitly
if [ -z "$ASSET_FILE" ]; then
    info "No asset specified on command line. Auto-detecting in current directory..."
    
    # Priority 1: Check for .deb files
    for CANDIDATE in nexus*.deb *.deb target/nexus*.deb target/*.deb; do
        if [ -f "$CANDIDATE" ]; then
            ASSET_FILE="$CANDIDATE"
            break
        fi
    done

    # Priority 2: Check for standalone binary files
    if [ -z "$ASSET_FILE" ]; then
        for CANDIDATE in "nexus-${OS}-${ARCH}" "nexus-${OS}-${ARCH}.exe" "target/nexus" "./nexus"; do
            if [ -f "$CANDIDATE" ]; then
                ASSET_FILE="$CANDIDATE"
                break
            fi
        done
    fi
fi

if [ -z "$ASSET_FILE" ] || [ ! -f "$ASSET_FILE" ]; then
    error "No valid offline package or binary asset found.
Please provide the path to your downloaded .deb file or binary:
  ./scripts/install-offline.sh <path/to/nexus.deb or path/to/binary>

Examples:
  ./scripts/install-offline.sh nexus_1.0.0_amd64.deb
  ./scripts/install-offline.sh nexus-linux-amd64"
fi

info "Using offline asset: $ASSET_FILE"

# Determine target installation directory
EXT=""
if [ "$OS" = "windows" ]; then
    EXT=".exe"
fi
TARGET_NAME="nexus${EXT}"

if [ -n "${INSTALL_DIR:-}" ]; then
    TARGET_DIR="$INSTALL_DIR"
elif [ "$(id -u)" -eq 0 ] || [ -w "/usr/local/bin" ]; then
    TARGET_DIR="/usr/local/bin"
elif command_exists sudo && (sudo -n true 2>/dev/null || [ -t 0 ]); then
    TARGET_DIR="/usr/local/bin"
else
    TARGET_DIR="$HOME/.local/bin"
fi

INSTALL_TARGET="$TARGET_DIR/$TARGET_NAME"

if [ "$DRY_RUN" = "1" ]; then
    info "[DRY-RUN] Source Asset    : $ASSET_FILE"
    info "[DRY-RUN] Target Location : $INSTALL_TARGET"
    info "[DRY-RUN] Extract Only    : $EXTRACT_ONLY"
    success "[DRY-RUN] Pre-flight validation passed successfully."
    exit 0
fi

# Stop any running Nexus daemon before replacing binary
if command_exists nexus; then
    info "Stopping running Nexus daemon if active..."
    nexus daemon stop >/dev/null 2>&1 || nexus stop >/dev/null 2>&1 || true
fi

# Check PID files
for PID_FILE in "$HOME/.nexus/nexus.pid" "/tmp/nexus-$USER/nexus.pid" /tmp/nexus-*/nexus.pid; do
    if [ -f "$PID_FILE" ]; then
        PID="$(cat "$PID_FILE" 2>/dev/null || true)"
        if [ -n "$PID" ] && kill -0 "$PID" 2>/dev/null; then
            info "Stopping running daemon process (PID: $PID)..."
            kill "$PID" 2>/dev/null || true
            sleep 0.5
        fi
    fi
done

TMP_DIR="$(mktemp -d 2>/dev/null || mktemp -d -t 'nexus-offline-install')"
trap 'rm -rf "$TMP_DIR"' EXIT

IS_DEB=0
case "$ASSET_FILE" in
    *.deb) IS_DEB=1 ;;
esac

# ------------------------------------------------------------------------------
# Installation Case 1: Debian/Ubuntu .deb package
# ------------------------------------------------------------------------------
if [ "$IS_DEB" = "1" ]; then
    info "Processing Debian package: $ASSET_FILE"

    # If system has dpkg and EXTRACT_ONLY is false, use dpkg -i
    if command_exists dpkg && [ "$EXTRACT_ONLY" != "1" ]; then
        info "Installing package via dpkg..."
        if [ "$(id -u)" -eq 0 ]; then
            dpkg -i "$ASSET_FILE"
        elif command_exists sudo; then
            sudo dpkg -i "$ASSET_FILE"
        else
            warn "No root/sudo privileges available to run dpkg. Falling back to archive extraction..."
            EXTRACT_ONLY=1
        fi
    fi

    # Fallback or explicit extraction mode (works on ANY Linux distribution without dpkg)
    if [ "$EXTRACT_ONLY" = "1" ] || ! command_exists dpkg; then
        info "Extracting Nexus binary directly from .deb package..."
        if command_exists dpkg-deb; then
            dpkg-deb -x "$ASSET_FILE" "$TMP_DIR"
        elif command_exists ar && command_exists tar; then
            (cd "$TMP_DIR" && ar -x "$(cd - >/dev/null && realpath "$ASSET_FILE")")
            for TAR_ARCHIVE in "$TMP_DIR"/data.tar*; do
                if [ -f "$TAR_ARCHIVE" ]; then
                    tar -xf "$TAR_ARCHIVE" -C "$TMP_DIR"
                fi
            done
        else
            error "Cannot extract .deb archive: neither dpkg-deb nor ar/tar was found on this system."
        fi

        # Locate extracted binary
        EXTRACTED_BIN=""
        for SEARCH_PATH in "$TMP_DIR/usr/local/bin/nexus" "$TMP_DIR/usr/bin/nexus" "$TMP_DIR/bin/nexus"; do
            if [ -f "$SEARCH_PATH" ]; then
                EXTRACTED_BIN="$SEARCH_PATH"
                break
            fi
        done

        if [ -z "$EXTRACTED_BIN" ]; then
            # Deep search
            EXTRACTED_BIN="$(find "$TMP_DIR" -type f -name "nexus" 2>/dev/null | head -n 1 || true)"
        fi

        if [ -z "$EXTRACTED_BIN" ] || [ ! -f "$EXTRACTED_BIN" ]; then
            error "Could not locate 'nexus' executable inside package $ASSET_FILE"
        fi

        mkdir -p "$TARGET_DIR" 2>/dev/null || true
        info "Installing extracted binary to $INSTALL_TARGET..."

        if [ -w "$TARGET_DIR" ]; then
            rm -f "$INSTALL_TARGET"
            cp "$EXTRACTED_BIN" "$INSTALL_TARGET"
            chmod +x "$INSTALL_TARGET"
        else
            if command_exists sudo; then
                sudo rm -f "$INSTALL_TARGET"
                sudo cp "$EXTRACTED_BIN" "$INSTALL_TARGET"
                sudo chmod +x "$INSTALL_TARGET"
            else
                error "Cannot write to $TARGET_DIR and sudo is not available. Run with: INSTALL_DIR=$HOME/.local/bin"
            fi
        fi

        # Also extract systemd unit if present and writable/sudoable
        if [ -f "$TMP_DIR/lib/systemd/system/nexus.service" ] || [ -f "$TMP_DIR/etc/systemd/system/nexus.service" ]; then
            SRC_SVC="$TMP_DIR/lib/systemd/system/nexus.service"
            [ ! -f "$SRC_SVC" ] && SRC_SVC="$TMP_DIR/etc/systemd/system/nexus.service"
            if [ -d "/etc/systemd/system" ]; then
                if [ "$(id -u)" -eq 0 ]; then
                    cp "$SRC_SVC" "/etc/systemd/system/nexus.service"
                    systemctl daemon-reload 2>/dev/null || true
                elif command_exists sudo; then
                    sudo cp "$SRC_SVC" "/etc/systemd/system/nexus.service" 2>/dev/null || true
                    sudo systemctl daemon-reload 2>/dev/null || true
                fi
            fi
        fi
    fi

# ------------------------------------------------------------------------------
# Installation Case 2: Standalone binary asset
# ------------------------------------------------------------------------------
else
    info "Installing standalone binary: $ASSET_FILE"
    mkdir -p "$TARGET_DIR" 2>/dev/null || true

    if [ -w "$TARGET_DIR" ]; then
        rm -f "$INSTALL_TARGET"
        cp "$ASSET_FILE" "$INSTALL_TARGET"
        chmod +x "$INSTALL_TARGET"
    else
        if command_exists sudo; then
            sudo rm -f "$INSTALL_TARGET"
            sudo cp "$ASSET_FILE" "$INSTALL_TARGET"
            sudo chmod +x "$INSTALL_TARGET"
        else
            error "Cannot write to $TARGET_DIR and sudo is not available. Run with: INSTALL_DIR=$HOME/.local/bin"
        fi
    fi
fi

# ------------------------------------------------------------------------------
# Idempotence: Clean duplicate binaries across alternate paths
# ------------------------------------------------------------------------------
ALL_LOCATIONS="/usr/local/bin/nexus /usr/bin/nexus $HOME/.local/bin/nexus $HOME/bin/nexus"
if command_exists which; then
    ALL_LOCATIONS="$ALL_LOCATIONS $(which -a nexus 2>/dev/null || true)"
fi

CLEANED_DUPLICATES=0
for CANDIDATE in $ALL_LOCATIONS; do
    if [ -f "$CANDIDATE" ] || [ -L "$CANDIDATE" ]; then
        REAL_CANDIDATE="$(readlink -f "$CANDIDATE" 2>/dev/null || echo "$CANDIDATE")"
        REAL_TARGET="$(readlink -f "$INSTALL_TARGET" 2>/dev/null || echo "$INSTALL_TARGET")"
        if [ "$REAL_CANDIDATE" != "$REAL_TARGET" ] && [ "$CANDIDATE" != "$INSTALL_TARGET" ]; then
            info "Removing duplicate binary at $CANDIDATE to prevent multiple versions side-by-side..."
            if [ -w "$CANDIDATE" ] || [ -w "$(dirname "$CANDIDATE")" ]; then
                rm -f "$CANDIDATE"
                CLEANED_DUPLICATES=$((CLEANED_DUPLICATES + 1))
            elif command_exists sudo; then
                sudo rm -f "$CANDIDATE"
                CLEANED_DUPLICATES=$((CLEANED_DUPLICATES + 1))
            fi
        fi
    fi
done

if [ "$CLEANED_DUPLICATES" -gt 0 ]; then
    success "Removed $CLEANED_DUPLICATES duplicate binary instance(s). Only $INSTALL_TARGET is active."
fi

# Verify executable works
if [ -x "$INSTALL_TARGET" ]; then
    info "Verifying binary execution..."
    if "$INSTALL_TARGET" --version >/dev/null 2>&1; then
        INSTALLED_VER="$("$INSTALL_TARGET" --version 2>&1 | head -n 1 || true)"
        success "Binary verification passed: $INSTALLED_VER"
    else
        warn "Binary installed at $INSTALL_TARGET, but smoke verification returned non-zero."
    fi
fi

# Create convenience symlinks for command aliases (nx, n, nxs, nexux)
for ALIAS in nx n nxs nexux; do
    ALIAS_TARGET="$TARGET_DIR/$ALIAS"
    if [ -w "$TARGET_DIR" ]; then
        ln -sf "$INSTALL_TARGET" "$ALIAS_TARGET"
    elif command_exists sudo; then
        sudo ln -sf "$INSTALL_TARGET" "$ALIAS_TARGET"
    fi
done
success "Configured command aliases: nx, n, nxs, nexux -> $INSTALL_TARGET"

# Check PATH
case ":$PATH:" in
    *":$TARGET_DIR:"*) ;;
    *)
        warn "$TARGET_DIR is not currently in your system PATH."
        warn "Add it to your shell configuration (e.g. ~/.bashrc or ~/.zshrc):"
        warn "  export PATH=\"$TARGET_DIR:\$PATH\""
        ;;
esac

printf "\n"
printf "${GREEN}${BOLD}Nexus is ready to use! (Installed offline from $(basename "$ASSET_FILE"))${NC}\n\n"
printf "Quick Start Commands:\n"
printf "  ${BOLD}nexus system info${NC}     Display host hardware & platform telemetry\n"
printf "  ${BOLD}nexus system disk${NC}     Inspect mounted filesystems and capacity\n"
printf "  ${BOLD}nexus config edit${NC}     Edit or view agent configuration\n"
printf "  ${BOLD}nexus config reload${NC}   Reload configuration on running daemon\n"
printf "  ${BOLD}nexus daemon start${NC}    Launch persistent daemon (Telegram + IPC)\n"
printf "  ${BOLD}nexus --help${NC}          Explore all subcommands\n\n"
