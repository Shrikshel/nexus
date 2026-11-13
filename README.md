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
curl -sL https://raw.githubusercontent.com/Shrikshel/nexus/master/scripts/install.sh | bash
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
# Install a specific release version (e.g., v1.0.0)
curl -sL https://raw.githubusercontent.com/Shrikshel/nexus/master/scripts/install.sh | VERSION=v1.0.0 bash

# Install to a custom directory (e.g., ~/bin or /opt/nexus)
curl -sL https://raw.githubusercontent.com/Shrikshel/nexus/master/scripts/install.sh | INSTALL_DIR="$HOME/bin" bash
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
curl -sL https://raw.githubusercontent.com/Shrikshel/nexus/master/scripts/uninstall.sh | sudo bash -s -- -y
```

---

## Quick Start & CLI Usage

### Build the Application
```bash
./mvnw clean package
```

### Run Commands Directly
Use the included `./nexus` wrapper script (or the compiled native binary `./target/nexus`):

#### 1. Host & Resource Telemetry
```bash
# General help and all available commands
./nexus --help

# System and OS Information
./nexus info
./nexus info --json

# Host & Agent Uptime
./nexus uptime

# CPU Utilization and Load Averages (1m, 5m, 15m)
./nexus cpu

# Memory (RAM and Swap) Utilization
./nexus memory

# Running Processes (sorted by CPU/Memory)
./nexus process list

# Network Interfaces & Assigned IP Addresses
./nexus network

# Installed System Services (systemd/Launchd/Windows SCM)
./nexus services
```

#### 2. Physical Storage & Drive Telemetry
```bash
# Storage and Filesystem Mounts (filtered to physical drives)
./nexus disk list
./nexus disk list --json

# Physical Drive SMART Telemetry (Privileged)
# Reports health, TBW, power-on hours, unsafe shutdowns, and error diagnostics
./nexus disk smart
./nexus disk smart /dev/sda
```

#### 3. OpenZFS Storage Management
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

#### 6. Privileged Host Power Management
```bash
# Gracefully restart / reboot host machine (prompts for confirmation)
sudo ./nexus reboot

# Bypass prompt with -y flag, optional custom delay, and audit reason
sudo ./nexus reboot -y -d 10 -r "Scheduled kernel update"

# Gracefully shutdown / power off host machine (prompts for confirmation)
sudo ./nexus poweroff

# Bypass prompt with -y flag
sudo ./nexus poweroff -y -d 5 -r "Maintenance window"
```

#### 7. Configuration Management & Live Reloading
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

#### 8. Capabilities Introspection & Updates
```bash
# Introspect all registered capabilities and their RBAC permissions
./nexus capabilities list
./nexus capabilities describe sys.info
./nexus capabilities describe net.tailscale.up
./nexus capabilities describe zfs.pool.status

# Check for new GitHub Releases
nexus update --check

# Apply latest update in-place (or uninstall)
sudo nexus update
sudo nexus uninstall -y --all
```

---

## Daemon Mode & Local Authenticated IPC

Nexus includes a local authenticated IPC server over loopback TCP (port `29786`).

### Starting the Daemon
```bash
./nexus start
# Or specify a custom IPC port:
./nexus start -p 29786
```

When started, the daemon:
1. Generates an ephemeral cryptographically secure 256-bit token stored in `~/.nexus/nexus.token` (or `/tmp/nexus-$USER/nexus.token`) with `0600` file permissions.
2. Starts the background telemetry engine and Telegram long-polling (if configured).
3. Listens on loopback `127.0.0.1:29786` for authenticated client commands.

### Querying Daemon Status
From another terminal window:
```bash
./nexus status
./nexus status --json
```

### Stopping the Daemon
```bash
./nexus stop
```

---

## Telegram Remote Integration

Nexus supports full bi-directional Telegram management.

### Configuration (`application.yml` or `config.yml`)
```yaml
nexus:
  telegram:
    enabled: true
    bot-token: "123456789:ABCDefGhIJKlmNoPQRsTUVwxyZ"
    allowed-chat-id: 987654321
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
   - **Host Power & Services**: `/sbin/shutdown`, `/sbin/reboot`, `/sbin/poweroff`, `/bin/systemctl`
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

7. **Audit Logging**: Every capability invocation records an audit log entry in `logs/nexus-audit.log`:
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


