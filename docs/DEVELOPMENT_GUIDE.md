# Nexus Developer & Contributor Guide

Welcome to the **Nexus Host Management Agent** development guide. This document provides step-by-step instructions for setting up your local environment, rapid iterative development, running automated tests, interactive debugging, and building GraalVM Native Image executables.

---

## Table of Contents
1. [Prerequisites & Toolchain](#1-prerequisites--toolchain)
2. [Project Architecture & Package Structure](#2-project-architecture--package-structure)
3. [Local Development (The Rapid Inner Loop)](#3-local-development-the-rapid-inner-loop)
   - [Method A: Using the `./nexus` Helper Script](#method-a-using-the-nexus-helper-script-fastest)
   - [Method B: Using Maven Directly (`./mvnw`)](#method-b-using-maven-directly-mvnw)
   - [Method C: IntelliJ IDEA (Run & Interactive Debug)](#method-c-intellij-idea-run--interactive-debug)
   - [Method D: Executing the Packaged Fat JAR](#method-d-executing-the-packaged-fat-jar)
4. [Testing Locally](#4-testing-locally)
   - [Running the Test Suite](#running-the-test-suite)
   - [Testing Daemon Mode & IPC Locally](#testing-daemon-mode--ipc-locally)
   - [Testing Telegram Bot Integration](#testing-telegram-bot-integration)
5. [Building GraalVM Native Image Executables](#5-building-graalvm-native-image-executables)
   - [Why AOT & Native Image?](#why-aot--native-image)
   - [Prerequisites per Operating System](#prerequisites-per-operating-system)
   - [Installing GraalVM JDK 17](#installing-graalvm-jdk-17)
   - [Step 1: Test Ahead-Of-Time (AOT) Metadata](#step-1-test-ahead-of-time-aot-metadata)
   - [Step 2: Compile the Native Executable](#step-2-compile-the-native-executable)
   - [Step 3: Run and Benchmark the Native Binary](#step-3-run-and-benchmark-the-native-binary)
   - [Alternative: Build with Docker (Zero Local Setup)](#alternative-build-with-docker-zero-local-setup)
6. [Daemon Architecture & Local IPC Interface](#6-daemon-architecture--local-ipc-interface)
   - [Overview & Port Allocation](#overview--port-allocation)
   - [Ephemeral Token Authentication & Security Model](#ephemeral-token-authentication--security-model)
   - [IPC Wire Protocol Specification](#ipc-wire-protocol-specification)
   - [Practical Client Examples (nc, Python, CLI)](#practical-client-examples-nc-python-cli)
   - [Running Nexus as a Systemd Service](#running-nexus-as-a-systemd-service)
7. [Adding New Capabilities & Privileged Operations](#7-adding-new-capabilities--privileged-operations)
   - [Step 1: Define Input & Output Record DTOs](#step-1-define-input--output-record-dtos)
   - [Step 2: Implement the Capability Interface](#step-2-implement-the-capability-interface)
   - [Step 3: Privileged Execution & Security Whitelisting](#step-3-privileged-execution--security-whitelisting)
   - [Step 4: Expose via CLI Subcommand](#step-4-expose-via-cli-subcommand)
   - [Step 5: Expose via Telegram Bot Adapter](#step-5-expose-via-telegram-bot-adapter)
   - [Step 6: Write Automated Tests](#step-6-write-automated-tests)
8. [Troubleshooting & FAQ](#8-troubleshooting--faq)

---

## 1. Prerequisites & Toolchain

### Required for JVM Development
- **Java Development Kit (JDK)**: OpenJDK 17 or higher (JDK 17 LTS recommended).
- **Maven**: Version 3.9+ (the repository includes an executable `./mvnw` wrapper).
- **Git**: For version control.

Verify your environment:
```bash
java -version
# Expected: openjdk version "17.0.x" or higher

./mvnw -v
# Expected: Apache Maven 3.x
```

---

## 2. Project Architecture & Package Structure

Nexus follows Clean Architecture principles, ensuring that capability implementations and business logic remain completely decoupled from delivery protocols:

```
src/main/java/com/nexus/
├── NexusApplication.java              # Application bootstrap & entry point
├── config/                            # Configuration properties (NexusProperties)
├── core/
│   ├── capability/                    # Capability interface, registry, execution engine
│   ├── security/                      # RBAC, permissions, subjects, audit logger
│   │   └── privilege/                 # Privileged execution boundary (sudo, smartctl)
│   └── notification/                  # Notification dispatcher, host events, severity
├── capabilities/                      # Statically registered capability implementations
│   ├── system/                        # sys.info, sys.uptime, sys.power.reboot, sys.power.poweroff
│   ├── resource/                      # sys.cpu, sys.memory
│   ├── storage/                       # disk.list, disk.smart, zfs.pool.*, zfs.dataset.*, zfs.snapshot.*
│   ├── process/                       # proc.list
│   ├── network/                       # net.info, net.tailscale.status, net.tailscale.up/down
│   ├── terminal/                      # tmux.session.list, tmux.session.new/attach/kill
│   ├── service/                       # svc.list
│   └── meta/                          # cap.list, cap.describe
├── platform/                          # Platform Abstraction Layer (PAL)
│   ├── PlatformDetector.java          # OS detection (Linux, macOS, Windows)
│   ├── SystemCollector.java           # CPU, memory, uptime collectors
│   ├── StorageCollector.java          # Mount points & SMART parser
│   └── HostTimezoneResolver.java      # Host timezone auto-alignment
└── adapters/
    ├── cli/                           # Picocli commands & output formatters
    ├── ipc/                           # Local loopback TCP server & client (port 29786)
    └── telegram/                      # Telegram bot polling adapter & alerts
```

---

## 3. Local Development (The Rapid Inner Loop)

> [!TIP]
> **Recommended Developer Workflow**: Do not compile a GraalVM native image for routine code modifications. Native compilation takes 1–3 minutes. Instead, use JVM mode (Methods A, B, or C below) for instant feedback during feature development, and build the native executable at release or verification time.

### Method A: Using the `./nexus` Helper Script (Fastest)

The project includes an executable `./nexus` runner script in the repository root. It compiles changes and launches the CLI instantly:

```bash
# Print general help and available commands
./nexus --help

# Run system info capability
./nexus info
./nexus info --json

# Run CPU, memory, or disk queries
./nexus cpu
./nexus memory
./nexus disk list

# Inspect registered capabilities
./nexus capabilities list
./nexus capabilities describe disk.smart

# Validate configuration
./nexus config validate
```

### Method B: Using Maven Directly (`./mvnw`)

Run commands through Spring Boot's Maven plugin using `-Dspring-boot.run.arguments`:

```bash
# Display help
./mvnw spring-boot:run -Dspring-boot.run.arguments="--help"

# Run system info
./mvnw spring-boot:run -Dspring-boot.run.arguments="info"

# Run with machine-readable JSON output
./mvnw spring-boot:run -Dspring-boot.run.arguments="info --json"

# Run storage inspection
./mvnw spring-boot:run -Dspring-boot.run.arguments="disk list"

# Start the continuous background daemon
./mvnw spring-boot:run -Dspring-boot.run.arguments="start"
```

### Method C: IntelliJ IDEA (Run & Interactive Debug)

The repository provides **18 pre-configured run configurations** in [`.run/`](../.run).

1. Open the project in IntelliJ IDEA (`File` -> `Open` -> select the repository directory).
2. Verify SDK is set to Java 17 (`File` -> `Project Structure` -> `Project` -> `SDK`).
3. Click the **Run/Debug Configuration dropdown** on the top toolbar:
   - Select **`Nexus: CLI - System Info`** to run `./nexus info`.
   - Select **`Nexus: CLI - Disk List`** to inspect disks.
   - Select **`Nexus: Daemon - Start Service`** to start the daemon.
   - Select **`Nexus: 2. Test (All)`** to run all tests.
4. **Interactive Debugging**:
   - Set a breakpoint in any capability (e.g. `SystemInfoCapability.java`).
   - Click the green **Debug icon** (`Shift+F9`).
   - Execution halts at your breakpoint with full inspection of variables, threads, and context.

See the dedicated [INTELLIJ_GUIDE.md](INTELLIJ_GUIDE.md) for full details.

### Method D: Executing the Packaged Fat JAR

To test the production Spring Boot JAR locally:

```bash
# 1. Package the JAR
./mvnw clean package -DskipTests

# 2. Run CLI commands
java -jar target/nexus-1.0.0-SNAPSHOT.jar info
java -jar target/nexus-1.0.0-SNAPSHOT.jar disk list --json

# 3. Run Daemon mode
java -jar target/nexus-1.0.0-SNAPSHOT.jar start
```

---

## 4. Testing Locally

### Running the Test Suite
The test suite includes 48+ automated unit and integration tests covering capabilities, execution engine, security filter, IPC server/client, and Telegram adapters.

```bash
# Run all tests
./mvnw test

# Run a specific test class
./mvnw test -Dtest=CliIntegrationTest

# Run a specific test method
./mvnw test -Dtest=CliIntegrationTest#testInfoCommandHumanOutput

# Run all capability tests
./mvnw test -Dtest=*CapabilityTest
```

### Testing Daemon Mode & IPC Locally
Nexus daemon mode runs a continuous service and exposes an authenticated loopback IPC interface (port `29786`):

1. **Terminal 1 (Start Daemon)**:
   ```bash
   ./nexus start
   ```
   *The daemon starts, generates a 256-bit token in `~/.nexus/nexus.token` (or `/tmp/nexus-$USER/nexus.token`) with secure `0600` permissions, and listens on `127.0.0.1:29786`.*

2. **Terminal 2 (Query Status via IPC)**:
   ```bash
   ./nexus status
   ./nexus status --json
   ```
   *The CLI connects to the IPC server, authenticates using the token, and prints the daemon's operational health, thread pools, and active integrations.*

3. **Terminal 2 (Graceful Stop via IPC)**:
   ```bash
   ./nexus stop
   ```
   *The CLI sends a secure shutdown signal to the daemon; the daemon shuts down gracefully.*

### Testing Telegram Bot Integration
1. Obtain a bot token from [@BotFather](https://t.me/botfather) on Telegram.
2. Find your personal Telegram user/chat ID using [@userinfobot](https://t.me/userinfobot).
3. Create `config.yml` (copy from `nexus-example.yml`):
   ```yaml
   nexus:
     telegram:
       enabled: true
       bot-token: "YOUR_BOT_TOKEN"
       allowed-chat-id: YOUR_CHAT_ID
   ```
4. Start the daemon with the custom configuration:
   ```bash
   ./nexus -c config.yml start
   ```
5. In your Telegram chat with the bot, send:
   - `/info` - returns system summary
   - `/uptime` - returns host and agent uptime
   - `/disk` - returns mounted disks
   - `/smart` - returns SMART report
   - `/caps` - returns list of capabilities

---

## 5. Building GraalVM Native Image Executables

### Why AOT & Native Image?
GraalVM Native Image compiles Java bytecode Ahead-Of-Time (AOT) directly into a standalone platform machine binary:
- **Instant startup**: Sub-50ms (compared to ~1.5s on JVM).
- **Extremely low memory footprint**: ~20MB RSS idle (compared to 80–150MB on JVM).
- **Standalone executable**: Requires no JVM or JRE installed on target machines.

### Prerequisites per Operating System

#### Linux (Ubuntu/Debian)
```bash
sudo apt update
sudo apt install -y build-essential libz-dev zlib1g-dev
```

#### macOS
Install Xcode command-line developer tools:
```bash
xcode-select --install
```

#### Windows
Install **Visual Studio 2022 Community** (or Visual Studio Build Tools) with the workload:
- **Desktop development with C++**
- Always execute native builds from the **`x64 Native Tools Command Prompt for VS 2022`**.

---

### Installing GraalVM JDK 17

#### Option 1: Via SDKMAN! (Recommended for Linux & macOS)
```bash
# 1. Install SDKMAN (if not already installed)
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"

# 2. Install GraalVM JDK 17
sdk install java 17.0.12-graalce

# 3. Use GraalVM JDK 17
sdk use java 17.0.12-graalce
export GRAALVM_HOME="$JAVA_HOME"
```

#### Option 2: Direct Binary Download (Linux, macOS, Windows)
1. Download GraalVM Community Edition JDK 17 from [GitHub Releases](https://github.com/graalvm/graalvm-ce-builds/releases).
2. Extract to a directory (e.g., `/opt/graalvm-jdk-17`).
3. Set environment variables:
   ```bash
   export JAVA_HOME=/opt/graalvm-jdk-17
   export GRAALVM_HOME=/opt/graalvm-jdk-17
   export PATH=$GRAALVM_HOME/bin:$PATH
   ```

Verify `native-image` is available:
```bash
native-image --version
# Expected: GraalVM ... Java 17 ... native-image
```

---

### Step 1: Test Ahead-Of-Time (AOT) Metadata
Before running native compilation, you can verify that Spring Boot AOT processing and Picocli code generation succeed without errors:

```bash
./mvnw spring-boot:process-aot
```

This generates reflection configuration in:
`target/classes/META-INF/native-image/com.nexus/nexus/reflect-config.json`

---

### Step 2: Compile the Native Executable
Invoke Maven with the `native` profile:

```bash
./mvnw -Pnative package -DskipTests
```

*(Alternatively, use `./mvnw -Pnative native:compile -DskipTests`)*.

Maven will:
1. Compile sources and run `picocli-codegen` processor.
2. Run Spring Boot AOT engine to discover reached beans and register runtime hints.
3. Invoke `native-image` with `--no-fallback` to produce the standalone binary at `target/nexus` (or `target\nexus.exe` on Windows).

> [!NOTE]
> Native compilation performs whole-world points-to analysis, dead code elimination, and native binary synthesis. It typically takes **1 to 3 minutes** and requires 4GB–8GB of RAM.

---

### Step 3: Run and Benchmark the Native Binary

Once compilation finishes, test the standalone binary:

```bash
# Run without any Java runtime installed:
./target/nexus --help

./target/nexus info
./target/nexus info --json
./target/nexus disk list
```

#### Benchmark Startup Time:
```bash
time ./target/nexus info
# Real time will be ~0.03s - 0.05s (30 - 50 ms)!
```

#### Benchmark Memory Footprint:
```bash
# Start daemon in background:
./target/nexus start &

# Check resident set size (RSS):
ps -o rss,command -p $(cat /tmp/nexus-$USER/nexus.pid)
# Expected RSS: ~18MB - 25MB!

# Graceful stop:
./target/nexus stop
```

---

### Alternative: Build with Docker (Zero Local Setup)

If you do not have GraalVM installed on your host, you can compile the native binary using an official GraalVM container:

```bash
docker run --rm \
  -v "$(pwd)":/app \
  -w /app \
  ghcr.io/graalvm/native-image-community:17-ol8 \
  ./mvnw -Pnative package -DskipTests
```

The resulting `target/nexus` binary is built for Linux x86_64 and can be deployed directly to any Linux host or minimal container!

---

### Automated Cross-Platform CI/CD with GitHub Actions

The repository includes a production-grade multi-platform GitHub Actions workflow at [`.github/workflows/build-native.yml`](../.github/workflows/build-native.yml):

- **Build Matrix**: Compiles native binaries for:
  - Linux `amd64` (`ubuntu-latest` -> `nexus-linux-amd64`)
  - macOS `arm64` Apple Silicon (`macos-latest` -> `nexus-darwin-arm64`)
  - macOS `amd64` Intel (`macos-13` -> `nexus-darwin-amd64`)
  - Windows `amd64` (`windows-latest` -> `nexus-windows-amd64.exe`)
- **Automated Verification**: Runs `--help` and `info` smoke tests on each platform runner after compilation.
- **Automated Releases**: When a tag matching `v*` (e.g. `v1.0.0`) is pushed, the workflow downloads all cross-platform binaries, generates `SHA256SUMS.txt`, and publishes a GitHub Release with all assets attached.
- **One-Line Installer Support**: Pre-built binaries are consumed directly by [`scripts/install.sh`](../scripts/install.sh).

---

## 6. Daemon Architecture & Local IPC Interface

### Overview & Port Allocation
When running in background service mode (`nexus start`), Nexus launches a persistent daemon that runs scheduled monitoring tasks, manages the Telegram bot long-polling loop, and serves an authenticated loopback Inter-Process Communication (IPC) interface.

- **Loopback Binding**: The IPC server binds strictly to `127.0.0.1:29786`. It is never exposed to external or remote network interfaces.
- **Port Allocation**: The default port is `29786`. If needed, it can be customized via `nexus.ipc.port` or system properties.
- **Graceful Lifecycle**:
  - `nexus start` initiates the daemon.
  - `nexus status` queries health, uptime, thread pools, and active integrations over IPC.
  - `nexus stop` transmits an authenticated graceful shutdown signal to the daemon.

```
┌────────────────────────────────────────────────────────┐
│                   Host Operating System                │
│                                                        │
│  ┌──────────────┐         AUTH <token>                 │
│  │  nexus CLI   │──────────────────────────┐           │
│  └──────────────┘                          ▼           │
│  ┌──────────────┐     STATUS / INVOKE ┌─────────────┐  │
│  │  Scripts/nc  │────────────────────▶│    Nexus    │  │
│  └──────────────┘   127.0.0.1:29786   │ Background  │  │
│  ┌──────────────┐                     │   Daemon    │  │
│  │   Python     │────────────────────▶│             │  │
│  └──────────────┘                     └─────────────┘  │
│                                              ▲         │
│  ~/.nexus/nexus.token (0600) ────────────────┘         │
│  ~/.nexus/nexus.pid                                    │
└────────────────────────────────────────────────────────┘
```

### Ephemeral Token Authentication & Security Model
To prevent unauthorized local users from manipulating the agent or executing privileged host commands, the daemon implements token-based authentication:

1. **Token Generation**: On daemon bootstrap, `DaemonIpcServer` generates a cryptographically secure 256-bit hexadecimal string via `SecureRandom`.
2. **Restricted Storage**: The token is written to `~/.nexus/nexus.token` (or `/tmp/nexus-$USER/nexus.token` when running in restricted/sandboxed containers).
3. **POSIX 0600 Permissions**: File permissions are explicitly locked down to `0600` (`-rw-------`), ensuring only the user account owning the daemon process can read the secret.
4. **Connection Isolation**: Incoming connections to the socket are untrusted and unauthenticated. Any command received before a successful `AUTH` handshake is rejected with `ERROR: UNAUTHORIZED` and the socket is immediately terminated.

### IPC Wire Protocol Specification
The local IPC interface speaks a lightweight, line-delimited UTF-8 text protocol:

| Command | Arguments | Server Response | Description |
| :--- | :--- | :--- | :--- |
| `AUTH` | `<token>` | `OK: AUTHENTICATED` or `ERROR: UNAUTHORIZED` | Authenticates the connection using the ephemeral token. Must be the first command sent. |
| `PING` | *None* | `PONG` | Verifies daemon connectivity and responsiveness. |
| `STATUS` | *None* | JSON object (`DaemonStatus`) | Returns process status (`RUNNING`), PID, uptime, active integrations, and capability count. |
| `INVOKE` | `<capabilityId> [inputJson]` | JSON `CapabilityResult<O>` | Executes any registered capability and returns structured execution output. |
| `EXEC` | `<capabilityId> [inputJson]` | JSON `CapabilityResult<O>` | Alias for `INVOKE`. |
| `STOP` | *None* | `OK: STOPPING` | Requests graceful Spring context shutdown and process exit. |

### Practical Client Examples (nc, Python, CLI)

#### Example 1: Querying Daemon Status via Netcat (`nc`)
```bash
# Read the active token
TOKEN=$(cat ~/.nexus/nexus.token)

# Query daemon status
(echo "AUTH $TOKEN"; echo "STATUS"; sleep 0.1) | nc 127.0.0.1 29786
```

#### Example 2: Invoking a Capability over IPC via Netcat
```bash
TOKEN=$(cat ~/.nexus/nexus.token)

# Execute system uptime capability
(echo "AUTH $TOKEN"; echo "INVOKE sys.uptime"; sleep 0.2) | nc 127.0.0.1 29786

# Execute Tailscale status capability
(echo "AUTH $TOKEN"; echo "INVOKE net.tailscale.status"; sleep 0.3) | nc 127.0.0.1 29786
```

#### Example 3: Python 3 Client Script
```python
import socket
import json
from pathlib import Path

# Locate token
token_file = Path.home() / ".nexus" / "nexus.token"
if not token_file.exists():
    token_file = Path(f"/tmp/nexus-{Path.home().name}/nexus.token")

token = token_file.read_text().strip()

def invoke_capability(cap_id: str, payload=None):
    with socket.create_connection(("127.0.0.1", 29786), timeout=10) as s:
        # Handshake
        s.sendall(f"AUTH {token}\n".encode())
        auth_resp = s.recv(1024).decode().strip()
        if auth_resp != "OK: AUTHENTICATED":
            raise RuntimeError(f"Authentication failed: {auth_resp}")

        # Invoke
        cmd = f"INVOKE {cap_id} {json.dumps(payload)}\n" if payload else f"INVOKE {cap_id}\n"
        s.sendall(cmd.encode())

        # Collect response
        response = b""
        while True:
            chunk = s.recv(4096)
            if not chunk:
                break
            response += chunk
            if b"\n" in response:
                break

        return json.loads(response.decode().strip())

# Example execution
result = invoke_capability("sys.uptime")
print("Host Uptime:", result["payload"]["hostUptimeFormatted"])
```

#### Example 4: Built-in CLI Client
```bash
# Query status
nexus status
nexus status --json

# Stop daemon
nexus stop
```

### Running Nexus as a Systemd Service
To run Nexus continuously on Linux servers as a systemd background daemon:

1. Create `/etc/systemd/system/nexus.service`:
   ```ini
   [Unit]
   Description=Nexus Host Management Agent Daemon
   After=network.target network-online.target
   Wants=network-online.target

   [Service]
   Type=simple
   User=nexus
   Group=nexus
   ExecStart=/usr/local/bin/nexus daemon start
   ExecStop=/usr/local/bin/nexus daemon stop
   Restart=always
   RestartSec=5s
   TimeoutStopSec=15s
   StandardOutput=journal
   StandardError=journal

   [Install]
   WantedBy=multi-user.target
   ```
2. Enable and start:
   ```bash
   sudo systemctl daemon-reload
   sudo systemctl enable --now nexus
   sudo systemctl status nexus
   ```

---

## 7. Adding New Capabilities & Privileged Operations

Adding a capability in Nexus is clean, modular, and automatically exposes functionality across all interfaces: CLI commands, Telegram bot interactions, and local IPC scripts.

### Step 1: Define Input & Output Record DTOs
Create strongly-typed immutable records in `com.nexus.capabilities.<domain>.dto`:

```java
package com.nexus.capabilities.battery.dto;

import java.time.Instant;

public record BatteryStatusReport(
    int percentage,
    boolean charging,
    String powerSource,
    String healthStatus,
    Instant timestamp
) {}
```

For parameterless capabilities, use `Void` as the input type. For parameterized operations, define a dedicated request record:

```java
package com.nexus.capabilities.battery.dto;

public record BatteryThresholdRequest(
    int chargeLimitPercentage,
    boolean enableConservationMode
) {}
```

### Step 2: Implement the Capability Interface
Implement `Capability<I, O>` in `com.nexus.capabilities.<domain>`:

```java
package com.nexus.capabilities.battery;

import com.nexus.capabilities.battery.dto.BatteryStatusReport;
import com.nexus.core.capability.*;
import com.nexus.core.security.Permission;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;

@Component
public class BatteryStatusCapability implements Capability<Void, BatteryStatusReport> {

    public static final String ID = "sys.battery.status";

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayName() {
        return "Battery Status & Health";
    }

    @Override
    public String getDescription() {
        return "Inspects host battery charge percentage, charging state, and health";
    }

    @Override
    public CapabilityCategory getCategory() {
        return CapabilityCategory.SYSTEM;
    }

    @Override
    public Set<Permission> getRequiredPermissions() {
        return Set.of(Permission.READ_SYSTEM);
    }

    @Override
    public boolean isPrivileged() {
        return false; // Set to true if root/administrator privileges are required
    }

    @Override
    public Class<Void> getInputType() {
        return Void.class;
    }

    @Override
    public Class<BatteryStatusReport> getOutputType() {
        return BatteryStatusReport.class;
    }

    @Override
    public CapabilityResult<BatteryStatusReport> execute(ExecutionContext context, Void input) {
        Instant start = Instant.now();
        // Query hardware metrics via PAL or system tools
        BatteryStatusReport report = new BatteryStatusReport(85, true, "AC Adapter", "GOOD", Instant.now());
        return CapabilityResult.success(ID, report, start);
    }
}
```

### Step 3: Privileged Execution & Security Whitelisting
When implementing capabilities that alter system configuration, control network state (e.g. `tailscale up/down`), or control host power (`reboot`/`poweroff`):

1. **Mark as Privileged**:
   Override `isPrivileged()` to return `true`.
2. **Use `PrivilegeRunner`**:
   Never invoke arbitrary shell strings (`Runtime.getRuntime().exec("sh -c ...")`). Inject and use `PrivilegeRunner`:
   ```java
   PrivilegeRunner.ExecutionOutput output = privilegeRunner.executeWhitelisted(binary, args, timeoutSeconds);
   ```
3. **Whitelist Binaries**:
   Ensure the target binary is explicitly registered in `PrivilegeRunner.ALLOWED_BINARIES`.
4. **Shell Injection Prevention**:
   `PrivilegeRunner` automatically inspects every command argument and blocks execution if shell metacharacters (`;`, `&`, `|`, `` ` ``, `$`) are present.
5. **Cross-Platform Elevation Model**:
   - **Linux & macOS**: Non-root processes run commands using `sudo -n` (non-interactive). Sudoers rules must be added (e.g., `/etc/sudoers.d/nexus`):
     ```
     nexus ALL=(root) NOPASSWD: /usr/sbin/smartctl, /usr/bin/tailscale, /sbin/reboot, /sbin/poweroff, /bin/systemctl, /sbin/zfs, /sbin/zpool, /usr/sbin/zfs, /usr/sbin/zpool
     ```
   - **Windows**: Windows does not support `sudo`. `PrivilegeRunner` detects Windows environments (`isWindows()`) and executes binaries directly under the running process's token (Administrator elevation).
6. **Destructive Safety Confirmations**:
   Destructive capabilities (`reboot`, `poweroff`, drive wipe) MUST require explicit user confirmation (`confirm=true`). If unconfirmed, return `CapabilityResult.failure(...)` with `ErrorCode.FORBIDDEN`:
   ```java
   if (input == null || !input.confirm()) {
       return CapabilityResult.failure(ID, ErrorCode.FORBIDDEN, "Operation requires explicit confirmation", start);
   }
   ```

### Step 4: Expose via CLI Subcommand
Add a command class in `com.nexus.adapters.cli.commands`:

```java
package com.nexus.adapters.cli.commands;

import com.nexus.adapters.cli.CliExecutionBridge;
import com.nexus.adapters.cli.NexusCliRootCommand;
import com.nexus.capabilities.battery.BatteryStatusCapability;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.util.concurrent.Callable;

@Component
@Command(name = "battery", description = "Displays host battery and power status", mixinStandardHelpOptions = true)
public class BatteryCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;
    private final CliExecutionBridge bridge;

    public BatteryCommand(CliExecutionBridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public Integer call() {
        NexusCliRootCommand root = spec != null && spec.root() != null ? spec.root().commandLine().getCommand() : null;
        boolean json = root != null && root.isJson();
        PrintWriter out = spec != null ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
        PrintWriter err = spec != null ? spec.commandLine().getErr() : new PrintWriter(System.err, true);
        return bridge.execute(BatteryStatusCapability.ID, null, json, out, err);
    }
}
```

Register the subcommand in `NexusCliRootCommand.java`:
```java
@Command(subcommands = { ... BatteryCommand.class })
```

Add rendering logic in `OutputFormatter.java`:
```java
} else if (payload instanceof BatteryStatusReport report) {
    renderBattery(report, out);
}
```

### Step 5: Expose via Telegram Bot Adapter
In `TelegramBotAdapter.java`:
1. Add command route in `handleCommand(...)`:
   ```java
   case "/battery":
       executeAndReply("sys.battery.status", context, null, chatId, botToken);
       break;
   ```
2. For destructive commands, enforce confirmation prompts before execution:
   ```java
   if (!text.contains("confirm")) {
       client.sendMessage(botToken, chatId, "⚠️ Confirmation required. Send: /reboot confirm");
       return;
   }
   ```
3. Add HTML rendering logic in `TelegramFormatter.java`.
4. Update `sendHelpMessage(...)` to include the new command in the user menu.

### Step 6: Write Automated Tests
Create unit tests in `src/test/java/com/nexus/capabilities/...` and verify with:
```bash
./mvnw test -Dtest=BatteryCapabilityTest
```

---

## 8. Troubleshooting & FAQ

### Q: Why do I get `native-image: command not found`?
**A**: Ensure your `JAVA_HOME` points to a GraalVM installation (not standard OpenJDK) and `export GRAALVM_HOME="$JAVA_HOME"`. Check `which native-image`. If you cannot install GraalVM on the host, use the Docker container build described in [Section 5](#alternative-build-with-docker-zero-local-setup).

### Q: Why does the daemon fail with `Read-only file system: /home/.../.nexus`?
**A**: When running in sandboxed or container environments where `$HOME` is read-only, Nexus automatically detects the restriction and falls back to `/tmp/nexus-$USER`. You can also explicitly specify a writable directory via `nexus.runtime.dir=/path/to/dir`.

### Q: How do I change the CLI output format?
**A**: Append `--json` (or `-j`) to any command to obtain structured JSON suitable for jq, scripting, or automated ingestion:
```bash
./nexus info --json | jq .hostname
```

### Q: How can I view full debug logs?
**A**: Pass `--verbose` (or `-v`):
```bash
./nexus -v info
```
Logs are also recorded to `logs/nexus.log` and security audits to `logs/nexus-audit.log`.
