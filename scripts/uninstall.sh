#!/bin/sh
# ==============================================================================
# Nexus Host Management Agent - One-Line Uninstaller
#
# Usage:
#   curl -sL https://raw.githubusercontent.com/Shrikshel/Nexus/master/scripts/uninstall.sh | bash
#
# Options via environment variables:
#   PURGE=1         Also delete configuration files and audit logs
#   ASSUME_YES=1    Skip interactive confirmation prompts (-y)
#   DRY_RUN=1       Display actions without modifying the system
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

# Parse CLI flags if passed directly
KEEP_CONFIG=0
for arg in "$@"; do
    case "$arg" in
        -y|--yes)       ASSUME_YES=1 ;;
        --purge|--all)  PURGE=1 ;;
        --keep-config)  KEEP_CONFIG=1 ;;
        --dry-run)      DRY_RUN=1 ;;
    esac
done

ASSUME_YES="${ASSUME_YES:-0}"
PURGE="${PURGE:-1}" # Default to complete trace-free cleanup unless --keep-config is set
if [ "$KEEP_CONFIG" = "1" ]; then
    PURGE=0
fi
DRY_RUN="${DRY_RUN:-0}"

printf "${BOLD}====================================================${NC}\n"
printf "${BOLD}   Nexus: Host Management Agent Uninstaller        ${NC}\n"
printf "${BOLD}====================================================${NC}\n\n"

if [ "$DRY_RUN" = "1" ]; then
    info "Running in DRY-RUN mode. No files will be deleted or services stopped."
fi

# Confirmation prompt if interactive
if [ "$ASSUME_YES" != "1" ] && [ "$DRY_RUN" != "1" ] && [ -t 0 ]; then
    printf "Are you sure you want to completely uninstall Nexus and remove all traces? [y/N]: "
    read -r CONFIRM
    case "$CONFIRM" in
        [yY]|[yY][eE][sS]) ;;
        *)
            info "Uninstallation cancelled by user."
            exit 0
            ;;
    esac
fi

# 1. Stop running daemon service
info "Checking for running Nexus daemon processes..."
if command_exists nexus; then
    if [ "$DRY_RUN" = "1" ]; then
        info "[DRY-RUN] Would request graceful daemon stop via 'nexus stop'"
    else
        nexus stop >/dev/null 2>&1 || true
    fi
fi

# Check PID files
for PID_FILE in "$HOME/.nexus/nexus.pid" "/tmp/nexus-$USER/nexus.pid" /tmp/nexus-*/nexus.pid; do
    if [ -f "$PID_FILE" ]; then
        PID="$(cat "$PID_FILE" 2>/dev/null || true)"
        if [ -n "$PID" ] && kill -0 "$PID" 2>/dev/null; then
            info "Terminating running daemon process (PID: $PID)..."
            if [ "$DRY_RUN" != "1" ]; then
                kill "$PID" 2>/dev/null || true
                sleep 0.5
                kill -9 "$PID" 2>/dev/null || true
            fi
        fi
    fi
done

# Terminate any remaining nexus processes
if [ "$DRY_RUN" != "1" ]; then
    if command_exists pkill; then
        pkill -x nexus >/dev/null 2>&1 || true
    fi
fi

# 2. Stop and disable systemd service (system & user)
if command_exists systemctl; then
    for SVC_NAME in nexus.service nexus; do
        if systemctl is-active --quiet "$SVC_NAME" 2>/dev/null || systemctl is-enabled --quiet "$SVC_NAME" 2>/dev/null; then
            info "Stopping and disabling systemd $SVC_NAME..."
            if [ "$DRY_RUN" != "1" ]; then
                if [ "$(id -u)" -eq 0 ]; then
                    systemctl stop "$SVC_NAME" 2>/dev/null || true
                    systemctl disable "$SVC_NAME" 2>/dev/null || true
                elif command_exists sudo; then
                    sudo systemctl stop "$SVC_NAME" 2>/dev/null || true
                    sudo systemctl disable "$SVC_NAME" 2>/dev/null || true
                fi
            fi
        fi
    done

    # Remove systemd unit files
    UNIT_FILES="/etc/systemd/system/nexus.service /lib/systemd/system/nexus.service $HOME/.config/systemd/user/nexus.service"
    for UF in $UNIT_FILES; do
        if [ -f "$UF" ]; then
            info "Removing systemd unit: $UF"
            if [ "$DRY_RUN" != "1" ]; then
                if [ -w "$UF" ] || [ -w "$(dirname "$UF")" ]; then
                    rm -f "$UF"
                elif command_exists sudo; then
                    sudo rm -f "$UF"
                fi
                if command_exists systemctl; then
                    if [ "$(id -u)" -eq 0 ]; then
                        systemctl daemon-reload 2>/dev/null || true
                    elif command_exists sudo; then
                        sudo systemctl daemon-reload 2>/dev/null || true
                    fi
                fi
            fi
        fi
    done
fi

# macOS Launchd plists
PLIST_FILES="/Library/LaunchDaemons/com.nexus.agent.plist $HOME/Library/LaunchAgents/com.nexus.agent.plist"
for PF in $PLIST_FILES; do
    if [ -f "$PF" ]; then
        info "Removing launchd plist: $PF"
        if [ "$DRY_RUN" != "1" ]; then
            if command_exists launchctl; then
                launchctl unload "$PF" 2>/dev/null || true
            fi
            rm -f "$PF" 2>/dev/null || (command_exists sudo && sudo rm -f "$PF")
        fi
    fi
done

# 3. Locate and remove ALL executable instances
ALL_CANDIDATE_BINS="/usr/local/bin/nexus /usr/bin/nexus $HOME/.local/bin/nexus $HOME/bin/nexus"
if command_exists which; then
    ALL_CANDIDATE_BINS="$ALL_CANDIDATE_BINS $(which -a nexus 2>/dev/null || true)"
fi

for BIN in $ALL_CANDIDATE_BINS; do
    if [ -f "$BIN" ] || [ -L "$BIN" ]; then
        info "Removing executable: $BIN"
        if [ "$DRY_RUN" != "1" ]; then
            if [ -w "$BIN" ] || [ -w "$(dirname "$BIN")" ]; then
                rm -f "$BIN"
            elif command_exists sudo; then
                sudo rm -f "$BIN"
            else
                warn "Could not remove $BIN (permission denied). Run with sudo."
            fi
        fi
    fi
done

# 4. Clean runtime state & IPC files
info "Cleaning runtime directories and IPC tokens..."
RUNTIME_DIRS="$HOME/.nexus /tmp/nexus-$USER"
for DIR in $RUNTIME_DIRS; do
    if [ -d "$DIR" ]; then
        if [ "$DRY_RUN" = "1" ]; then
            info "[DRY-RUN] Would delete runtime directory: $DIR"
        else
            rm -rf "$DIR"
        fi
    fi
done

# Clean any leftover /tmp/nexus-* sockets
for TMP_DIR in /tmp/nexus-*; do
    if [ -d "$TMP_DIR" ]; then
        if [ "$DRY_RUN" = "1" ]; then
            info "[DRY-RUN] Would delete temp runtime directory: $TMP_DIR"
        else
            rm -rf "$TMP_DIR" 2>/dev/null || true
        fi
    fi
done

# 5. Purge configurations, logs, and sudoers (Trace-free uninstallation)
if [ "$PURGE" = "1" ]; then
    info "Purging all configuration files, audit logs, and system drop-ins..."
    PURGE_PATHS="/etc/nexus $HOME/.config/nexus logs/nexus.log logs/nexus-audit.log logs /var/log/nexus /etc/sudoers.d/nexus /etc/bash_completion.d/nexus $HOME/.local/share/bash-completion/completions/nexus"
    for P in $PURGE_PATHS; do
        if [ -e "$P" ]; then
            if [ "$DRY_RUN" = "1" ]; then
                info "[DRY-RUN] Would purge: $P"
            else
                if [ -w "$P" ] || [ -w "$(dirname "$P")" ]; then
                    rm -rf "$P"
                elif command_exists sudo; then
                    sudo rm -rf "$P"
                fi
            fi
        fi
    done
fi

printf "\n"
success "Nexus has been completely uninstalled. All binaries, services, tokens, and traces were removed."
if [ "$PURGE" != "1" ]; then
    info "Note: User configuration files were preserved (--keep-config). Run without --keep-config to purge them."
fi
