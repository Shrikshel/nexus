#!/bin/sh
# ==============================================================================
# Nexus Host Management Agent - One-Line Installer
#
# Usage:
#   curl -fsSL https://raw.githubusercontent.com/Shrikshel/nexus/main/scripts/install.sh | bash
#
# Options via environment variables:
#   REPO="owner/repo"       GitHub repository (default: Shrikshel/nexus)
#   VERSION="v1.0.0"        Specific release tag (default: latest)
#   INSTALL_DIR="/path"     Installation target directory (default: /usr/local/bin or ~/.local/bin)
#   SKIP_CHECKSUM=1         Skip SHA256 integrity verification
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

# Ensure standard utilities are present
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Parse CLI flags
USE_LOCAL="${LOCAL:-0}"
for arg in "$@"; do
    case "$arg" in
        --local|-l) USE_LOCAL=1 ;;
        --dry-run)  DRY_RUN=1 ;;
        -y|--yes)   ASSUME_YES=1 ;;
    esac
done

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

download_file() {
    URL="$1"
    OUTPUT="$2"
    if command_exists curl; then
        curl -fsSL "$URL" -o "$OUTPUT"
    elif command_exists wget; then
        wget -q "$URL" -O "$OUTPUT"
    else
        error "Neither curl nor wget was found. Please install curl or wget."
    fi
}

main() {
    printf "${BOLD}====================================================${NC}\n"
    printf "${BOLD}   Nexus: Cross-Platform Host Management Agent     ${NC}\n"
    printf "${BOLD}====================================================${NC}\n\n"

    REPO="${REPO:-Shrikshel/nexus}"
    VERSION="${VERSION:-latest}"

    OS="$(detect_os)"
    ARCH="$(detect_arch)"

    info "Detected platform: ${OS} (${ARCH})"

    # Determine binary artifact name
    EXT=""
    if [ "$OS" = "windows" ]; then
        EXT=".exe"
    fi
    BINARY_NAME="nexus-${OS}-${ARCH}${EXT}"
    TARGET_NAME="nexus${EXT}"

    # Check for local build if --local is requested or if local target/nexus exists
    LOCAL_CANDIDATE=""
    if [ "$USE_LOCAL" = "1" ]; then
        if [ -f "./target/nexus${EXT}" ]; then
            LOCAL_CANDIDATE="./target/nexus${EXT}"
        elif [ -f "../target/nexus${EXT}" ]; then
            LOCAL_CANDIDATE="../target/nexus${EXT}"
        elif [ -f "./nexus${EXT}" ]; then
            LOCAL_CANDIDATE="./nexus${EXT}"
        else
            error "Local build not found at ./target/nexus${EXT}. Build first using: ./mvnw -Pnative package -DskipTests"
        fi
        info "Installing from local build: $LOCAL_CANDIDATE"
    fi

    # Determine target installation directory
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

    if [ "${DRY_RUN:-0}" = "1" ]; then
        info "[DRY-RUN] Target binary   : $TARGET_NAME"
        info "[DRY-RUN] Target location : $INSTALL_TARGET"
        info "[DRY-RUN] Source          : ${LOCAL_CANDIDATE:-$REPO ($VERSION)}"
        success "[DRY-RUN] Pre-flight checks passed successfully."
        exit 0
    fi

    TMP_DIR="$(mktemp -d 2>/dev/null || mktemp -d -t 'nexus-install')"
    trap 'rm -rf "$TMP_DIR"' EXIT

    DOWNLOAD_PATH="$TMP_DIR/$BINARY_NAME"
    CHECKSUM_PATH="$TMP_DIR/SHA256SUMS.txt"

    GITHUB_TOKEN="${GITHUB_TOKEN:-${TOKEN:-}}"

    if [ -n "$LOCAL_CANDIDATE" ]; then
        cp "$LOCAL_CANDIDATE" "$DOWNLOAD_PATH"
        chmod +x "$DOWNLOAD_PATH"
    else
        DOWNLOADED_VIA_API=0
        # If GITHUB_TOKEN is set (e.g. for private repositories), query GitHub API for release assets
        if [ -n "$GITHUB_TOKEN" ] && command_exists curl; then
            AUTH_HEADER="Authorization: Bearer $GITHUB_TOKEN"
            API_RELEASE_URL="https://api.github.com/repos/${REPO}/releases/latest"
            if [ "$VERSION" != "latest" ]; then
                case "$VERSION" in
                    v*) TAG="$VERSION" ;;
                    *)  TAG="v$VERSION" ;;
                esac
                API_RELEASE_URL="https://api.github.com/repos/${REPO}/releases/tags/${TAG}"
            fi

            RELEASE_JSON="$TMP_DIR/release.json"
            if curl -fsSL -H "$AUTH_HEADER" -H "Accept: application/vnd.github+json" "$API_RELEASE_URL" -o "$RELEASE_JSON" 2>/dev/null; then
                ASSET_ID="$(grep -B 2 -A 5 "\"name\": \"$BINARY_NAME\"" "$RELEASE_JSON" | grep '"id":' | head -n 1 | tr -dc '0-9')"
                CHECKSUM_ASSET_ID="$(grep -B 2 -A 5 '"name": "SHA256SUMS.txt"' "$RELEASE_JSON" | grep '"id":' | head -n 1 | tr -dc '0-9')"

                if [ -n "$ASSET_ID" ]; then
                    info "Downloading $BINARY_NAME via GitHub API (authenticated)..."
                    if curl -fsSL -H "$AUTH_HEADER" -H "Accept: application/octet-stream" "https://api.github.com/repos/${REPO}/releases/assets/$ASSET_ID" -o "$DOWNLOAD_PATH"; then
                        DOWNLOADED_VIA_API=1
                        if [ -n "$CHECKSUM_ASSET_ID" ]; then
                            curl -fsSL -H "$AUTH_HEADER" -H "Accept: application/octet-stream" "https://api.github.com/repos/${REPO}/releases/assets/$CHECKSUM_ASSET_ID" -o "$CHECKSUM_PATH" 2>/dev/null || true
                        fi
                    fi
                fi
            fi
        fi

        if [ "$DOWNLOADED_VIA_API" != "1" ]; then
            # Determine public download URL
            if [ "$VERSION" = "latest" ]; then
                DOWNLOAD_URL="https://github.com/${REPO}/releases/latest/download/${BINARY_NAME}"
                CHECKSUM_URL="https://github.com/${REPO}/releases/latest/download/SHA256SUMS.txt"
            else
                case "$VERSION" in
                    v*) TAG="$VERSION" ;;
                    *)  TAG="v$VERSION" ;;
                esac
                DOWNLOAD_URL="https://github.com/${REPO}/releases/download/${TAG}/${BINARY_NAME}"
                CHECKSUM_URL="https://github.com/${REPO}/releases/download/${TAG}/SHA256SUMS.txt"
            fi

            info "Downloading $BINARY_NAME from GitHub repository '$REPO'..."
            DOWNLOAD_SUCCESS=0
            if download_file "$DOWNLOAD_URL" "$DOWNLOAD_PATH" 2>/dev/null; then
                DOWNLOAD_SUCCESS=1
            else
                # Fallback: Query GitHub public releases API to resolve the newest tag (including pre-releases/snapshots)
                DISCOVERED_TAG="$(curl -fsSL "https://api.github.com/repos/${REPO}/releases" 2>/dev/null | grep '"tag_name":' | head -n 1 | tr -d ' ",' | cut -d: -f2 || true)"
                if [ -n "$DISCOVERED_TAG" ]; then
                    FALLBACK_URL="https://github.com/${REPO}/releases/download/${DISCOVERED_TAG}/${BINARY_NAME}"
                    CHECKSUM_URL="https://github.com/${REPO}/releases/download/${DISCOVERED_TAG}/SHA256SUMS.txt"
                    info "Discovered release tag '$DISCOVERED_TAG'. Retrying download from $FALLBACK_URL..."
                    if download_file "$FALLBACK_URL" "$DOWNLOAD_PATH" 2>/dev/null; then
                        DOWNLOAD_SUCCESS=1
                    fi
                fi
            fi

            if [ "$DOWNLOAD_SUCCESS" != "1" ]; then
                # If download fails, check if a local build exists as fallback
                if [ -f "./target/nexus${EXT}" ]; then
                    warn "Remote download failed, but local build found at ./target/nexus${EXT}. Falling back to local build..."
                    cp "./target/nexus${EXT}" "$DOWNLOAD_PATH"
                else
                    error "Failed to download $BINARY_NAME from $DOWNLOAD_URL.
Possible reasons:
1. GitHub Actions is still building the release assets for the latest commit (GraalVM native compilation takes ~4-6 minutes). Check status: https://github.com/${REPO}/actions
2. No releases have been published yet on https://github.com/${REPO}/releases
3. For local installations, run: ./mvnw -Pnative package -DskipTests && ./scripts/install.sh --local"
                fi
            fi
        fi

        # Optional Checksum verification (for downloaded files)
        if [ -z "$LOCAL_CANDIDATE" ] && [ "${SKIP_CHECKSUM:-0}" != "1" ]; then
            if download_file "$CHECKSUM_URL" "$CHECKSUM_PATH" 2>/dev/null; then
                info "Verifying SHA256 checksum..."
                EXPECTED_HASH="$(awk -v fname="$BINARY_NAME" '$2 == fname || $2 == ("*" fname) {print $1; exit}' "$CHECKSUM_PATH" 2>/dev/null || true)"
                if [ -z "$EXPECTED_HASH" ]; then
                    EXPECTED_HASH="$(grep -E "[[:space:]]\*?${BINARY_NAME}$" "$CHECKSUM_PATH" 2>/dev/null | awk '{print $1}' | head -n 1 || true)"
                fi
                if [ -n "$EXPECTED_HASH" ]; then
                    if command_exists sha256sum; then
                        ACTUAL_HASH="$(sha256sum "$DOWNLOAD_PATH" | awk '{print $1}')"
                    elif command_exists shasum; then
                        ACTUAL_HASH="$(shasum -a 256 "$DOWNLOAD_PATH" | awk '{print $1}')"
                    else
                        ACTUAL_HASH=""
                        warn "Neither sha256sum nor shasum is available. Skipping checksum verification."
                    fi

                    if [ -n "$ACTUAL_HASH" ]; then
                        if [ "$EXPECTED_HASH" = "$ACTUAL_HASH" ]; then
                            success "SHA256 integrity verified successfully ($ACTUAL_HASH)"
                        else
                            error "SHA256 verification failed!\nExpected: $EXPECTED_HASH\nActual:   $ACTUAL_HASH"
                        fi
                    fi
                fi
            fi
        fi
    fi

    chmod +x "$DOWNLOAD_PATH"

    # Stop running daemon before in-place upgrade
    if command_exists nexus; then
        info "Stopping running Nexus daemon if active..."
        nexus stop >/dev/null 2>&1 || true
    fi

    mkdir -p "$TARGET_DIR" 2>/dev/null || true

    info "Installing binary to $INSTALL_TARGET..."

    if [ -w "$TARGET_DIR" ]; then
        rm -f "$INSTALL_TARGET"
        mv "$DOWNLOAD_PATH" "$INSTALL_TARGET"
        chmod +x "$INSTALL_TARGET"
    else
        if command_exists sudo; then
            info "Requesting sudo privileges to install into $TARGET_DIR..."
            sudo rm -f "$INSTALL_TARGET"
            sudo mv "$DOWNLOAD_PATH" "$INSTALL_TARGET"
            sudo chmod +x "$INSTALL_TARGET"
        else
            error "Cannot write to $TARGET_DIR and sudo is not available. Try running with INSTALL_DIR=$HOME/.local/bin"
        fi
    fi

    # Idempotent cleanup: Remove duplicate binaries from alternate standard locations
    # to prevent multiple versions existing side-by-side.
    ALL_LOCATIONS="/usr/local/bin/nexus /usr/bin/nexus $HOME/.local/bin/nexus $HOME/bin/nexus"
    if command_exists which; then
        WHICH_BINARIES="$(which -a nexus 2>/dev/null || true)"
        ALL_LOCATIONS="$ALL_LOCATIONS $WHICH_BINARIES"
    fi

    CLEANED_DUPLICATES=0
    for CANDIDATE in $ALL_LOCATIONS; do
        if [ -f "$CANDIDATE" ] || [ -L "$CANDIDATE" ]; then
            # Canonicalize paths
            REAL_CANDIDATE="$(readlink -f "$CANDIDATE" 2>/dev/null || echo "$CANDIDATE")"
            REAL_TARGET="$(readlink -f "$INSTALL_TARGET" 2>/dev/null || echo "$INSTALL_TARGET")"
            if [ "$REAL_CANDIDATE" != "$REAL_TARGET" ] && [ "$CANDIDATE" != "$INSTALL_TARGET" ]; then
                info "Removing alternate/stale binary at $CANDIDATE to ensure a single active version..."
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
        success "Cleaned up $CLEANED_DUPLICATES duplicate/stale binary instance(s). Only $INSTALL_TARGET is now active."
    fi

    success "Installed nexus successfully to $INSTALL_TARGET"

    # PATH warning if installing to non-standard user directory
    case ":$PATH:" in
        *":$TARGET_DIR:"*) ;;
        *)
            warn "$TARGET_DIR is not currently in your system PATH."
            warn "Add it to your shell configuration (e.g. ~/.bashrc or ~/.zshrc):"
            warn "  export PATH=\"$TARGET_DIR:\$PATH\""
            ;;
    esac

    printf "\n"
    printf "${GREEN}${BOLD}Nexus is ready to use!${NC}\n\n"
    printf "Quick Start Commands:\n"
    printf "  ${BOLD}nexus info${NC}            Display host hardware & platform telemetry\n"
    printf "  ${BOLD}nexus disk list${NC}       Inspect mounted filesystems and capacity\n"
    printf "  ${BOLD}nexus config edit${NC}     Edit or view agent configuration\n"
    printf "  ${BOLD}nexus config reload${NC}   Reload configuration on running daemon\n"
    printf "  ${BOLD}nexus start${NC}           Launch persistent daemon (Telegram + IPC)\n"
    printf "  ${BOLD}nexus --help${NC}          Explore all subcommands\n\n"
}

main "$@"
