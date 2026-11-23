# Nexus: Cross-Platform Host Management Agent

[![Build GraalVM Native](https://github.com/Shrikshel/nexus/actions/workflows/build-native.yml/badge.svg)](https://github.com/Shrikshel/nexus/actions/workflows/build-native.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Nexus is an enterprise-grade, lightweight, cross-platform host management agent built with Java, Spring Boot, Picocli, and GraalVM Native Image ahead-of-time (AOT) compilation.

It targets **Linux, Windows, and macOS**, running with minimal runtime memory footprint while providing a first-class CLI, authenticated local IPC, and remote bi-directional Telegram integrations.

---

## Key Highlights

- **Headless & Low Footprint**: Operates with `web-application-type=none` by default (no web server overhead, ~20MB RSS compiled with GraalVM).
- **Dual Operating Modes**:
  - **On-demand CLI mode**: Direct execution of capabilities with ANSI color formatting, interactive terminal pass-through, or machine-readable `--json` output.
  - **Daemon mode**: Continuous background service managing scheduled health checks, Telegram bot polling, and authenticated local loopback IPC (`127.0.0.1:29786`).
- **Pluggable Capability System**: Clean architecture with strongly-typed `Capability<I, O>` interfaces, dynamic Spring discovery, execution engine timeouts, and cancellation tokens.
- **OpenZFS Storage Suite**: Native pool health monitoring, vdev status trees, dataset hierarchies, snapshot inspection, and dataset property queries (`zpool` and `zfs`).
- **Physical Drive & SMART Telemetry**: Deep drive diagnostics reporting overall health, TBW (Total Bytes Written), Power-On Hours, Unsafe Shutdown counts, and error logs with automatic physical drive filtering.
- **Tailscale Mesh VPN**: Live connection diagnostics, tailnet IP mapping, peer node status, and privileged network state management (`up` / `down`).
- **Terminal Multiplexer (`tmux`)**: CLI session lifecycle management (`list`, `new`, `attach`, `kill`) with automatic interactive TTY console handover.
- **Recurring Timers & Automation Engine**: Scaffolds, configures, and manages systemd timers (Linux), `launchd` agents (macOS), and Windows Task Scheduler tasks. Enforces deterministic sequential identifiers (`NX001-backup-db`), generates executable script stubs with `chmod +x`, provides rich schedule presets (`daily`, `midnight`, `hourly`, `weekly`, `twice-weekly`, `monthly`, `boot`) plus monotonic intervals (`every 15m`, `every 2h`), and launches your editor directly to add automation logic.
- **Privileged System Power Controls**: Safe host reboot and shutdown operations with mandatory explicit confirmation guards across both CLI (`-y`) and Telegram (`/reboot confirm`).
- **Defense-in-Depth Security**: Communication daemon runs strictly unprivileged; privileged operations (`smartctl`, `tailscale`, `zfs`, `reboot`, `poweroff`) are isolated via `PrivilegeRunner` with strict binary whitelisting.
- **Bi-Directional Telegram Adapter**: Supports rich HTML formatting, automatic message splitting (>4096 characters), inbound commands, and proactive threshold alerts (disk degradation, SMART failures, lifecycle events).
- **AOT & Native-First**: Fully configured with `picocli-codegen` and Spring Boot AOT to generate reflection and reachability metadata at compile time.

---

## Architecture Overview

```
                      ┌─────────────────────────────────────────┐
                      │            Delivery Adapters            │
                      │  Picocli CLI   •   Telegram Bot   • IPC │
                      └────────────────────┬────────────────────┘
                                           │
                                           ▼
                      ┌─────────────────────────────────────────┐
                      │            Security & RBAC              │
                      │  Allowlists • Rate Limiter • Audit Log  │
                      └────────────────────┬────────────────────┘
                                           │
                                           ▼
                      ┌─────────────────────────────────────────┐
                      │            Execution Engine             │
                      │ Timeouts • Cooperative Cancel • Metrics │
                      └────────────────────┬────────────────────┘
                                           │
                                           ▼
                      ┌────────────────────────────────────────────────────────┐
                      │              Pluggable Capability Registry             │
                      └───┬──────────────┬──────────────────┬──────────────┬───┘
                          │              │                  │              │
               ┌──────────┘              │                  │              └──────────┐
               ▼                         ▼                  ▼                         ▼
      ┌────────────────┐        ┌────────────────┐ ┌────────────────┐        ┌────────────────┐
      │ System / Power │        │ Storage & ZFS  │ │ Net & Tailscale│        │Terminal (tmux) │
      │ info, reboot.. │        │ SMART, datasets│ │ status, up/down│        │ sessions, TTY  │
      └───────┬────────┘        └────────┬───────┘ └────────┬───────┘        └────────┬───────┘
              │                          │                  │                         │
              └────────────────────┐     │                  │     ┌───────────────────┘
                                   ▼     ▼                  ▼     ▼
                      ┌────────────────────────────────────────────────────────┐
                      │               Platform Abstraction Layer               │
                      │  Linux (/proc, /sys) • macOS (sysctl) • Win32 (PowerS) │
                      │  Privileged Helper (smartctl, tailscale, zfs, systemd) │
                      └────────────────────────────────────────────────────────┘
```

---

## Installation

Nexus binaries are compiled ahead-of-time using GraalVM Native Image into standalone, dependency-free executables for Linux (amd64) and Windows (amd64).

### Option A: One-Line Automated Installer (Recommended)

Install or update the latest pre-compiled standalone native binary directly with a single command:

```bash
curl -fsSL https://raw.githubusercontent.com/Shrikshel/nexus/main/scripts/install.sh | bash
```

> **What the installer does automatically:**
> 1. Detects your operating system (Linux, macOS, Windows) and CPU architecture (x86_64, ARM64).
> 2. Downloads the official release binary from [GitHub Releases](https://github.com/Shrikshel/nexus/releases/latest).
> 3. Verifies the SHA256 cryptographic checksum against `SHA256SUMS.txt`.
> 4. Gracefully stops any active background daemon before upgrading.
> 5. Installs the new binary to `/usr/local/bin/nexus` (or `~/.local/bin/nexus`).
> 6. **Guarantees Idempotence**: Scans for and removes duplicate or stale `nexus` binaries across other standard locations (`~/.local/bin/nexus`, `/usr/local/bin/nexus`, `/usr/bin/nexus`) so you never have two conflicting versions installed side-by-side.

#### Installer Customization
You can pass environment variables or CLI flags to customize installation:
```bash
# Install a specific release version or snapshot (e.g., v0.0.1-SNAPSHOT or v1.0.0)
curl -fsSL https://raw.githubusercontent.com/Shrikshel/nexus/main/scripts/install.sh | VERSION=v0.0.1-SNAPSHOT bash

# Install to a custom directory (e.g., ~/bin or /opt/nexus)
curl -fsSL https://raw.githubusercontent.com/Shrikshel/nexus/main/scripts/install.sh | INSTALL_DIR="$HOME/bin" bash
```

---

### Option B: Local Source Build & Installation (Zero Internet Required)

If you are developing locally or building from source:

```bash
# 1. Compile the native executable locally
./mvnw -Pnative package -DskipTests

# 2. Run the idempotent installer with --local
./scripts/install.sh --local

# Or install manually:
sudo cp target/nexus /usr/local/bin/nexus
```

---

### Option C: Offline & Debian Package Installation (.deb / Air-Gapped / Auth Bypass)

When installing on air-gapped hosts, private network infrastructure, or environments where you do not want to provide GitHub authentication tokens over `curl`, use the offline installer with a pre-downloaded `.deb` package or native binary:

```bash
# Auto-detects .deb package or native binary in the current directory and installs it
./scripts/install-offline.sh

# Or provide the downloaded asset explicitly:
./scripts/install-offline.sh nexus_1.0.0_amd64.deb
./scripts/install-offline.sh nexus-linux-amd64
```

> **Offline Installer Capabilities:**
> - **Zero Network & Zero Auth**: Runs 100% locally from the pre-downloaded package or binary.
> - **Native Debian Package (`dpkg`)**: If `dpkg` is present, installs via `dpkg -i`.
> - **Universal Extraction Mode (`--extract-only`)**: If running on a non-Debian Linux system (or without root `dpkg` rights), automatically extracts the binary using `dpkg-deb` or `ar` + `tar`.
> - **Pre-Flight Dry Run**: Preview all actions before modifying anything:
>   ```bash
>   ./scripts/install-offline.sh --dry-run nexus_1.0.0_amd64.deb
>   ```
> - **Guaranteed Idempotency**: Automatically stops running daemons, purges duplicate binaries across standard directories (`/usr/local/bin`, `~/.local/bin`, `/usr/bin`), and ensures a single version is active.

#### Building Debian Packages (.deb)
To package the native executable into an installable `.deb` package containing the binary, systemd service unit, and default `/etc/nexus/config.yml`:

```bash
./scripts/package-deb.sh [VERSION]
# Example: produces target/nexus_1.0.0_amd64.deb and target/nexus-linux-amd64.deb
./scripts/package-deb.sh 1.0.0
```

---

### Manual Binary & Package Download

Download pre-built standalone binaries and Debian packages directly from [GitHub Releases](https://github.com/Shrikshel/nexus/releases/latest):

| Operating System / Distro | Architecture | Package / Binary Asset | Description |
|---|---|---|---|
| **Linux (Debian / Ubuntu)** | x86_64 / amd64 | [`nexus-linux-amd64.deb`](https://github.com/Shrikshel/nexus/releases/latest/download/nexus-linux-amd64.deb) | Standalone `.deb` package (binary + systemd unit + starter config) |
| **Linux (All Distros)** | x86_64 / amd64 | [`nexus-linux-amd64`](https://github.com/Shrikshel/nexus/releases/latest/download/nexus-linux-amd64) | Standalone executable (GraalVM native binary) |
| **Windows** | x86_64 / amd64 | [`nexus-windows-amd64.exe`](https://github.com/Shrikshel/nexus/releases/latest/download/nexus-windows-amd64.exe) | Standalone executable |

---

### Updating Nexus

Nexus includes self-updating capabilities to check for and apply in-place binary updates:

```bash
# Check if an update is available without installing
nexus update --check

# Download and apply the latest release in-place
sudo nexus update

# Force re-installation of the latest release
sudo nexus update --force

# Target a specific release tag
sudo nexus update --tag=v1.0.1
```

---

### Uninstalling Nexus (Trace-Free)

Nexus uninstallation leaves **zero traces** on the system, cleanly removing all binaries, duplicate paths, background systemd services, tokens, configuration files, and audit logs:

#### Option 1: Using the Built-In CLI Command
```bash
# Preview what would be removed (dry-run)
nexus uninstall --dry-run

# Complete trace-free uninstallation (removes binaries, runtime tokens, configs, and logs)
sudo nexus uninstall -y --all
```

#### Option 2: Using the Uninstaller Script
```bash
# Run local or remote uninstaller script
sudo ./scripts/uninstall.sh -y
# Or via curl:
curl -fsSL https://raw.githubusercontent.com/Shrikshel/nexus/main/scripts/uninstall.sh | sudo bash -s -- -y
```

---

## Quick Start & CLI Usage

### Build the Application
```bash
./mvnw clean package
```

### Run Commands Directly
Use the included `./nexus` wrapper script (or the compiled native binary `./target/nexus`).

#### Command Aliases & Enterprise CLI Formatting
Nexus follows modern CLI standards inspired by tools like GitHub CLI (`gh`), Docker, and Google Cloud SDK (`gcloud`):
- **Structured Command Categories**: Commands are logically grouped into `CORE & SYSTEM`, `AUTOMATION & SERVICES`, `STORAGE & WORKSPACES`, and `AGENT CONFIGURATION`.
- **Dynamic Terminal Width**: Automatically detects terminal columns (`$COLUMNS` / `tput cols`) with bounded wrapping (110–140 cols) so descriptions never awkwardly truncate.
- **ANSI Multi-Color Styling**: Bold yellow section headings, bright cyan command verbs, bright green flags, magenta argument metavars, and bright blue aliases.
- **Prominent Alias Introspection**: Subcommands cleanly display all registered aliases in an `ALIASES` section when inspecting help (`nx <command> --help`).

For fast and convenient command-line invocation, Nexus also supports root aliases:
- `nx` (recommended short alias)
- `n` (ultra-compact alias)
- `nxs`
- `nexux`

Any command can be invoked interchangeably with `nexus`, `nx`, or `n`:
```bash
nx system info
n timer list
nx system audit
n daemon status
```

#### 1. Host System & Diagnostic Management (`nexus system` / `nexus sys`)
All host-level telemetry, package management, hardware health diagnostics, self-audits, and power operations are consolidated under the clean `nexus system` group:

```bash
# General help and all system subcommands
nexus system --help

# System Package Management (auto-detects APT, DNF, Pacman, Zypper, APK, Brew, Winget)
nexus system update                     # Refresh package repositories
nexus system upgrade -y                 # Upgrade installed packages with auto-yes
nexus system upgrade --dry-run          # Preview packages to be upgraded
nexus system update-upgrade -y          # Update repositories then upgrade sequentially

# Comprehensive System Self-Audit
# Verifies disk capacity thresholds (>85% / >95%), SSD/HDD SMART health, RAM pressure,
# failed systemd services (systemctl --failed), internet reachability, and agent security
nexus system audit
nexus system audit --json

# Host Hardware & OS Telemetry
nexus system info                       # Host OS, kernel, arch, memory summary, timezone
nexus system uptime                     # Host uptime, boot time, and daemon uptime
nexus system cpu                        # CPU model, core topology, load averages (1m, 5m, 15m)
nexus system memory                     # Physical RAM and Swap capacity and utilization
nexus system disk                       # Mounted filesystem storage (filtered to physical drives)
nexus system smart                      # SSD/HDD SMART health, wear level, TBW, temperatures
nexus system smart /dev/sda             # Target specific disk drive
nexus system network                    # Network interfaces, MAC addresses, MTU, IPv4/IPv6
nexus system process                    # Process table sorted by CPU and memory consumption
nexus system service                    # Inspect installed system services and runtime states

# Privileged Host Power Management (requires sudo/admin)
sudo nexus system reboot                # Prompts for confirmation
sudo nexus system reboot -y -d 10 -r "Scheduled maintenance"
sudo nexus system poweroff              # Prompts for confirmation
sudo nexus system poweroff -y -d 5 -r "Maintenance window"
```

#### 2. Nexus Background Daemon Management (`nexus daemon` / `nexus d`)
Natively manages the OS background service supervisor (Linux `systemd`, macOS `launchd`, Windows `Task Scheduler`) providing 24/7 Telegram bot polling, health monitoring, and authenticated local loopback IPC (`127.0.0.1:29786`):

```bash
# Install and enable the daemon background service (does NOT start immediately)
nexus daemon install                    # User scope: zero-root (~/.config/systemd/user/nexus.service)
sudo nexus daemon install --system      # System scope: multi-user (/etc/systemd/system/nexus.service)

# Start the background daemon service (automatically ensures enabled; fails if not installed)
nexus daemon start                      # Runs headlessly under systemd/launchd
nexus daemon start --foreground         # Run directly in foreground terminal for debugging

# Query supervisor and IPC operational status
nexus daemon status
nexus daemon status --json

# Enable or disable auto-start on host boot
nexus daemon enable                     # Auto-start on boot
nexus daemon disable                    # Disable auto-start on boot

# Gracefully stop the running background daemon (via supervisor or IPC)
nexus daemon stop

# Stream live daemon logs in real time
nexus daemon logs -f

# Completely uninstall daemon service and remove supervisor unit files
nexus daemon uninstall
```

#### 3. PM2-Style Synchronized Log Management (`nexus logs` / `nx logs`)
Nexus centralizes all application telemetry and audit logs into a single canonical directory (`~/.nexus/logs/` for user scope or `/var/log/nexus/` for system scope). It features PM2-style automatic log rotation (10MB chunks, gzip `.gz` compression, 14-day retention, and 500MB total size cap):

```bash
# View last 30 lines of application logs (default)
nexus logs

# View custom line count
nexus logs -n 50

# Stream and follow live log updates in real-time (like 'tail -f' or 'pm2 logs')
nexus logs -f

# Inspect the tamper-evident audit trail log
nexus logs --audit

# List all active log files, file sizes, and compressed rotated archives
nexus logs --list

# Truncate / flush active log files without restarting services (like 'pm2 flush')
nexus logs --flush

# Print the active canonical log directory path
nexus logs --path
```

#### 4. OpenZFS Storage Management
```bash
# List all configured ZFS storage pools and health states
./nexus zfs pools
./nexus zfs pools --json

# Inspect detailed pool health, scrub status, and vdev tree topology
./nexus zfs status
./nexus zfs status tank

# List ZFS datasets (filesystems and volumes)
./nexus zfs datasets
./nexus zfs datasets -t filesystem

# List ZFS snapshots
./nexus zfs snapshots
./nexus zfs snapshots tank/data

# Inspect detailed properties and configuration for a particular dataset
./nexus zfs get tank/data
./nexus zfs get tank/data -p compression,mountpoint,used,available,quota
```

#### 4. Tailscale Mesh VPN Management
```bash
# Query Tailscale connection state, MagicDNS name, tailnet IPs, and peers
./nexus tailscale status
./nexus tailscale status --json

# Connect to Tailscale network and bring VPN up (requires sudo/admin)
sudo ./nexus tailscale up
sudo ./nexus tailscale up --authkey=tskey-auth-... --accept-routes --ssh

# Disconnect from Tailscale network and tear VPN down (requires sudo/admin)
sudo ./nexus tailscale down
```

#### 5. Terminal Multiplexer (`tmux`) — CLI-Only
```bash
# List all active tmux sessions
./nexus tmux list
./nexus tmux list --json

# Create a new detached tmux session
./nexus tmux new -n dev
./nexus tmux new -n backend --dir /home/user/backend --cmd "cargo run"

# Attach interactively to an existing session (automatic TTY handover)
./nexus tmux attach dev

# Terminate a specific session or kill all sessions
./nexus tmux kill dev
./nexus tmux kill --all
```

#### 6. Configuration Management & Live Reloading
```bash
# Validate configuration file syntax, tokens, and properties
nexus config validate

# Edit configuration in interactive terminal editor (nano, vim, vi, notepad)
# Automatically creates default template if missing, and validates syntax on exit
nexus config edit

# Display the exact editor command to open the configuration file (e.g. nano <path>)
nexus config edit --print

# Print the absolute path of the currently resolved configuration file
nexus config path

# Display contents of the active configuration file
nexus config show

# Dynamically reload configuration on the running background daemon over IPC (zero downtime)
nexus config reload
```

#### 7. Recurring Timers & Automation Engine (systemd, launchd, Task Scheduler)
Manage cross-platform scheduled tasks across both user space and system services. User timers run unprivileged via `systemctl --user` with scripts in `~/.nexus/scripts/`, while system timers run machine-wide with scripts in `/root/.nexus/scripts/` and unit files in `/etc/systemd/system/`.

```bash
# List all configured timers across all scopes (user and system) with live telemetry and SCOPE column
nexus timer
nexus timer list
nexus timer list --user     # Filter strictly to user timers
nexus timer list --system   # Filter strictly to system-wide daemon timers
nexus timer list --json

# Create a user timer with friendly preset (daily, midnight, hourly, weekly, twice-weekly, monthly, boot)
# Scaffolds ~/.nexus/scripts/NX001-backup-db.sh with executable permissions (0755) and enables systemd timer immediately
nexus timer create backup-db --schedule "daily" --desc "Daily PostgreSQL database backup"

# Create a system-wide timer (stored under /root/.nexus/ and enabled in systemd system scope)
nexus timer create root-cleanup --schedule "hourly" --desc "System-wide cache cleanup" --system

# Create a timer with monotonic interval (e.g. every 15m, every 2h, 30s)
nexus timer create sync-s3 --schedule "every 15m" --desc "Sync files to S3 bucket"

# Create and immediately open the executable automation script in your editor (nano, vim, code)
nexus timer create cert-renew --schedule "weekly" --edit

# Inspect live status, next activation time, and recent execution logs (auto-resolves scope)
nexus timer status NX001
nexus timer status backup-db

# Trigger an immediate one-off execution of the timer's script for testing
nexus timer run NX001

# Edit the executable script or update the schedule (auto-resolves scope)
nexus timer edit NX001                      # Opens script in $EDITOR
nexus timer edit NX001 --schedule "every 2h" # Updates schedule and reloads daemon
nexus timer edit NX001 --timer              # Inspect/edit the systemd .timer unit file
nexus timer edit NX001 --print              # Prints exact editor command and path

# Delete a timer (auto-resolves scope, unlinks systemd unit files, and unregisters state)
nexus timer delete NX001 -y
nexus timer delete NX001 --purge-script -y  # Also deletes associated executable automation script
```

#### 8. Native Telegram Notification & Media Dispatching API
Send messages, alerts, log files, photos, and stickers directly from the CLI or automation scripts/timers with multi-recipient broadcasting:

```bash
# Broadcast a text alert to all configured recipients (server.yaml)
nexus telegram send "Nightly backup completed successfully!"

# Pipe text output or logs directly into Telegram using '-'
cat /var/log/syslog | grep -i "error" | nexus telegram send -
uptime | nexus telegram send -

# Upload and dispatch files, logs, or documents (auto-detects document vs photo)
nexus telegram send file /var/log/nginx/error.log --caption "Production Nginx Errors"
nexus telegram send --file report.pdf --caption "Monthly Host Report"

# Upload and send images / snapshots
nexus telegram send photo /tmp/dashboard.png --caption "System metrics dashboard"
nexus telegram send --photo /var/backup/snapshot.jpg

# Send Telegram stickers (by local .webp file or Telegram file_id)
nexus telegram send sticker CAACAgIAAxkBAAI...
nexus telegram send --sticker animated_alert.tgs

# Send silently without sound notifications
nexus telegram send "Nightly disk scrub started" -s

# Custom formatting (HTML, Markdown, MarkdownV2)
nexus telegram send "<b>CRITICAL:</b> Disk utilization reached <i>95%</i>" --format html

# Override target recipients dynamically (accepts numeric user IDs, group IDs, or @channel_handles)
nexus telegram send "Deployment started" -r 123456789 -r @devops_broadcast

# Machine-readable JSON output for integrations
nexus --json telegram send "Automated ping"
```

#### 9. Capabilities Introspection & Updates
```bash
# Introspect all registered capabilities and their RBAC permissions
./nexus capabilities list
./nexus capabilities describe sys.info
./nexus capabilities describe net.tailscale.up
./nexus capabilities describe zfs.pool.status
./nexus capabilities describe telegram.send

# Check for new GitHub Releases
nexus update --check

# Apply latest update in-place (or uninstall)
sudo nexus update
sudo nexus uninstall -y --all
```

---

## Daemon Mode & OS Service Supervision

Nexus runs headlessly as a native OS background service (Linux `systemd`, macOS `launchd`, Windows Task Scheduler) with a local authenticated IPC server over loopback TCP (port `29786`).

### Installing & Managing the Background Service
```bash
# 1. Install and register the service (zero root required in user scope)
nexus daemon install

# 2. Start the headless background daemon
nexus daemon start

# 3. Check status (supervisor state + live IPC)
nexus daemon status

# 4. Stop or disable
nexus daemon stop
nexus daemon disable
```

When running, the daemon:
1. Generates an ephemeral cryptographically secure 256-bit token stored in `~/.nexus/nexus.token` (or `/tmp/nexus-$USER/nexus.token`) with `0600` file permissions.
2. Starts the background telemetry engine and Telegram long-polling (if configured).
3. Listens on loopback `127.0.0.1:29786` for authenticated client commands.
4. Restarts automatically on crash or host reboot under the OS supervisor.

---

## Telegram Remote Integration

Nexus supports full bi-directional Telegram management.

### Configuration (`server.yaml` or `application.yml`)
```yaml
nexus:
  telegram:
    enabled: true
    bot-token: "123456789:ABCDefGhIJKlmNoPQRsTUVwxyZ"
    allowed-chat-id: 987654321
    recipients:
      - 987654321
      - "@devops_channel"
```

### Inbound Commands

| Command | Arguments | Permission | Description |
|---|---|---|---|
| `/start`, `/help` | — | Public | Overview of bot commands and usage instructions |
| `/info` | — | `READ_SYSTEM` | Host hardware, CPU cores, RAM, OS, kernel, and timezone |
| `/uptime` | — | `READ_SYSTEM` | System uptime and Nexus daemon uptime |
| `/cpu` | — | `READ_SYSTEM` | Real-time CPU core utilization and system load averages |
| `/memory` | — | `READ_SYSTEM` | System RAM and Swap memory utilization |
| `/disk` | — | `READ_STORAGE` | Mounted filesystem capacity (filtered to physical drives) |
| `/smart` | `[device]` | `READ_STORAGE` | SMART health, TBW, power-on hours, unsafe shutdowns, error log |
| `/zfs` | — | `READ_STORAGE` | OpenZFS storage pools overview and health |
| `/zfs_status` | `[pool]` | `READ_STORAGE` | Detailed ZFS pool status, scrub info, and vdev layout |
| `/zfs_datasets` | `[type]` | `READ_STORAGE` | OpenZFS dataset hierarchy and mountpoints |
| `/zfs_snapshots` | `[dataset]` | `READ_STORAGE` | OpenZFS snapshots and referenced storage bytes |
| `/zfs_get` | `<dataset>` | `READ_STORAGE` | Inspect detailed properties for a specific ZFS dataset |
| `/services` | — | `READ_SERVICES` | Installed system services status |
| `/timers` | `[id]` | `READ_SERVICES` | List recurring timers or inspect live timer status & journal logs |
| `/tailscale` | — | `READ_NETWORK` | Tailscale VPN state, MagicDNS name, tailnet IPs, and peers |
| `/tailscale_up` | `[args]` | `WRITE_NETWORK` | Connect to Tailscale network (`/tailscale_up --accept-routes`) |
| `/tailscale_down` | — | `WRITE_NETWORK` | Disconnect from Tailscale network |
| `/reboot` | `confirm` | `ADMIN_POWER` | Gracefully restart host system (requires explicit `confirm`) |
| `/poweroff` | `confirm` | `ADMIN_POWER` | Gracefully power off host system (requires explicit `confirm`) |
| `/reload` | — | `ADMIN` | Reload configuration from disk without restarting daemon |
| `/caps` | — | `READ_SYSTEM` | Introspect all compiled agent capabilities |

> [!NOTE]
> **tmux CLI Exclusivity**: Terminal multiplexer commands (`nexus tmux`) provide interactive pseudo-terminal (TTY) attachment and session handover. Because Telegram does not provide an interactive TTY console, `tmux` is strictly designated for the local CLI and is excluded from the Telegram bot interface by design.

> [!TIP]
> **Automatic Message Splitting**: Long responses (such as comprehensive SMART diagnostic logs or large snapshot listings) that exceed Telegram's 4,096-character limit are automatically chunked and delivered sequentially without truncation.

### Outbound Alerting
When scheduled health sweeps detect issues, alerts are automatically pushed to Telegram:
- **Disk Degradation**: Published when reallocated sectors > 0 or temperature exceeds threshold.
- **Critical Failure**: Published when overall SMART health assessment fails.
- **Agent Lifecycle**: Startup and shutdown notifications.

---

## Security Architecture

1. **Least Privilege & Non-Root Execution**:
   The internet-facing daemon runs under standard user credentials (e.g. `nexus` or current user).
2. **No Arbitrary Shells**:
   The agent strictly disallows arbitrary bash/sh command strings.
3. **Privilege Boundary & Isolation (`PrivilegeRunner`)**:
   Privileged operations are invoked through `PrivilegeRunner`, which validates binaries against a strict whitelist:
   - **SMART Diagnostics**: `/usr/sbin/smartctl`, `/sbin/smartctl`
   - **Mesh VPN**: `/usr/bin/tailscale`, `/bin/tailscale`
   - **Host Power & Services**: `/sbin/shutdown`, `/sbin/reboot`, `/sbin/poweroff`, `/bin/systemctl`, `/usr/bin/systemctl`, `/bin/journalctl`, `/usr/bin/journalctl`
   - **Scheduled Tasks**: `launchctl`, `schtasks.exe`
   - **OpenZFS Storage**: `/sbin/zfs`, `/usr/sbin/zfs`, `/sbin/zpool`, `/usr/sbin/zpool`
   - **Terminal Multiplexer**: `/usr/bin/tmux`, `/bin/tmux`, `/usr/local/bin/tmux`, `/opt/homebrew/bin/tmux`

   All arguments are passed as isolated string arrays directly to `ProcessBuilder` without invoking a shell interpreter, preventing argument injection and shell metacharacter exploits (`;`, `&`, `|`, `` ` ``).

4. **Destructive Operation Safeguards**:
   Destructive actions (`reboot`, `poweroff`) require dual confirmation:
   - **CLI**: Interactive terminal prompt or explicit `-y` / `--yes` flag.
   - **Telegram**: Requires explicit `confirm` argument (e.g., `/reboot confirm`).

5. **Configuring Sudoers for Non-Root Daemon Execution**:
   To allow the unprivileged `nexus` user to perform privileged telemetry and network operations without running the daemon as root:
   ```sudoers
   # /etc/sudoers.d/nexus
   nexus ALL=(ALL) NOPASSWD: /usr/sbin/smartctl, /usr/bin/tailscale, /sbin/shutdown, /sbin/reboot, /sbin/poweroff, /bin/systemctl, /sbin/zfs, /sbin/zpool, /usr/sbin/zfs, /usr/sbin/zpool
   ```

6. **Role-Based Access Control (RBAC)**:
   Fine-grained permissions (`READ_SYSTEM`, `READ_STORAGE`, `WRITE_NETWORK`, `WRITE_PROCESSES`, `ADMIN_POWER`) ensure callers only access authorized capabilities.

7. **Audit Logging**: Every capability invocation records an audit log entry in `~/.nexus/logs/nexus-audit.log`:
   ```
   2026-11-13 09:05:15.123 AUDIT_SUCCESS invocationId=... source=CLI subjectId=cli-local username=admin capability=disk.list durationMs=8
   ```

---

## GraalVM Native Image Compilation

### Ahead-of-Time (AOT) Metadata Generation
Spring Boot AOT processing and `picocli-codegen` generate reflection and reachability metadata at compile time:
```bash
./mvnw spring-boot:process-aot
```

### Native Executable Compilation
With GraalVM JDK installed and `GRAALVM_HOME` set:
```bash
export GRAALVM_HOME=/path/to/graalvm-jdk-17
./mvnw -Pnative package -DskipTests
```
The native binary will be generated at `target/nexus`, running with ~20MB RSS and sub-50ms startup time!

---

## IntelliJ IDEA Run Configurations

Pre-configured, project-level Maven run configurations are provided in `.run/` for out-of-the-box development and testing inside IntelliJ IDEA:

| Category | Available Run Configurations |
|----------|------------------------------|
| **Build & Compile** | `Nexus: 1. Clean & Compile`, `Nexus: 4. Package (Fat JAR)`, `Nexus: 5. Package (Skip Tests)` |
| **Testing** | `Nexus: 2. Test (All)`, `Nexus: 3. Clean & Test` |
| **CLI Execution** | `Nexus: CLI - Help`, `Nexus: CLI - System Info`, `Nexus: CLI - System Info (JSON)`, `Nexus: CLI - CPU Stats`, `Nexus: CLI - Memory Stats`, `Nexus: CLI - Disk List`, `Nexus: CLI - Capabilities List` |
| **Daemon Service** | `Nexus: Daemon - Start Service` |
| **GraalVM & AOT** | `Nexus: GraalVM - Process AOT`, `Nexus: GraalVM - Build Native Executable`, `Nexus: GraalVM - Native Test` |
| **Diagnostics** | `Nexus: Diagnostic - Dependency Tree`, `Nexus: Diagnostic - Dependency Analyze` |

See [INTELLIJ_GUIDE.md](docs/INTELLIJ_GUIDE.md) for full setup instructions, keyboard shortcuts, and customization details.

---

## Developer Guide & Contributing

For a comprehensive guide on local development workflows, running automated tests, interactive debugging, adding new capabilities, and building GraalVM Native Image executables, see:
👉 **[DEVELOPMENT_GUIDE.md](docs/DEVELOPMENT_GUIDE.md)**


