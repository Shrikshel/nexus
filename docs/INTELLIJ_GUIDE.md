# IntelliJ IDEA Run Configurations Guide for Nexus

This document describes the shared Maven run configurations provided for development and testing in **IntelliJ IDEA**.

All configurations are stored in the `.run/` folder according to IntelliJ IDEA's modern shared configuration standard and are automatically detected by IntelliJ when opening the project.

---

## 1. Project Prerequisites & Setup

1. **JDK 17 or higher**:
   - Open IntelliJ IDEA and verify your Project SDK:
     - `File` -> `Project Structure` (`Ctrl+Alt+Shift+S` on Linux/Windows, `Cmd+;` on macOS) -> `Project` -> **SDK: 17** (e.g., OpenJDK 17 or GraalVM JDK 17).
2. **Maven Wrapper**:
   - IntelliJ automatically detects `pom.xml` and configures the Maven project structure.
3. **Run Configurations Location**:
   - Configurations reside in `.run/*.run.xml` and automatically populate IntelliJ's **Run/Debug Configuration dropdown** on the top toolbar.
   - Shortcut to browse and run configurations: `Alt+Shift+F10` (Linux/Windows) or `Ctrl+Option+R` (macOS).

---

## 2. Catalog of Run Configurations

The run configurations are grouped into 6 logical categories:

### A. Build & Compilation
| Configuration Name | Maven Goals | Parameters | Purpose |
|-------------------|-------------|------------|---------|
| **`Nexus: 1. Clean & Compile`** | `clean compile` | - | Cleans `target/`, recompiles sources, and runs `picocli-codegen` annotation processor to generate reflection metadata. |
| **`Nexus: 4. Package (Fat JAR)`** | `clean package` | - | Builds the fully executable production fat JAR (`target/nexus-1.0.0-SNAPSHOT.jar`) after executing all tests. |
| **`Nexus: 5. Package (Skip Tests)`** | `clean package` | `-DskipTests` | Rapid packaging for quick developer iteration skipping test execution. |

### B. Automated Testing
| Configuration Name | Maven Goals | Parameters | Purpose |
|-------------------|-------------|------------|---------|
| **`Nexus: 2. Test (All)`** | `test` | - | Runs all 48+ JUnit 5 automated unit and integration tests (CLI, IPC, Capabilities, Security, Telegram). |
| **`Nexus: 3. Clean & Test`** | `clean test` | - | Performs a clean target sweep followed by running the full test suite. |

### C. CLI Commands (via Spring Boot Maven Plugin)
These configurations launch the Nexus CLI directly in IntelliJ without needing to rebuild or package the JAR first.

| Configuration Name | Maven Goals | Parameters (`-Dspring-boot.run.arguments=...`) | Purpose |
|-------------------|-------------|------------------------------------------------|---------|
| **`Nexus: CLI - Help`** | `spring-boot:run` | `"--help"` | Displays the Picocli root usage guide and all available subcommands. |
| **`Nexus: CLI - System Info`** | `spring-boot:run` | `"info"` | Executes the `sys.info` capability, displaying hostname, OS, kernel, CPU cores, RAM, and localized timezone. |
| **`Nexus: CLI - System Info (JSON)`** | `spring-boot:run` | `"info --json"` | Outputs the `sys.info` report in structured JSON format. |
| **`Nexus: CLI - CPU Stats`** | `spring-boot:run` | `"cpu"` | Displays CPU model, architecture, load averages (1m, 5m, 15m), and utilization. |
| **`Nexus: CLI - Memory Stats`** | `spring-boot:run` | `"memory"` | Displays physical RAM and swap capacity and usage percentages. |
| **`Nexus: CLI - Disk List`** | `spring-boot:run` | `"disk list"` | Lists mounted filesystems, mount points, file system types, and available capacity. |
| **`Nexus: CLI - Capabilities List`** | `spring-boot:run` | `"capabilities list"` | Introspects and lists all 11 registered agent capabilities with their descriptions and required RBAC permissions. |
| **`Nexus: CLI - Update (Check)`** | `spring-boot:run` | `"update --check"` | Checks GitHub Releases for new agent versions without applying updates. |
| **`Nexus: CLI - Uninstall (Dry Run)`** | `spring-boot:run` | `"uninstall --dry-run"` | Previews uninstallation actions, target binary path, and runtime directories. |

### D. Daemon Mode
| Configuration Name | Maven Goals | Parameters | Purpose |
|-------------------|-------------|------------|---------|
| **`Nexus: Daemon - Start Service`** | `spring-boot:run` | `-Dspring-boot.run.arguments="start"` | Starts Nexus as a persistent background daemon, generating the local authenticated IPC token (`0600`) and starting the Telegram adapter (if enabled). Press the red Stop button in IntelliJ to terminate. |

### E. GraalVM Native Image & AOT
| Configuration Name | Maven Goals | Profiles / Parameters | Purpose |
|-------------------|-------------|------------------------|---------|
| **`Nexus: GraalVM - Process AOT`** | `spring-boot:process-aot` | - | Pre-evaluates Spring context Ahead-Of-Time and generates GraalVM reflection/reachability metadata in `target/spring-aot`. |
| **`Nexus: GraalVM - Build Native Executable`** | `clean package` | Profile: `native`<br>`-DskipTests` | Invokes GraalVM Native Image compiler to produce the standalone binary `target/nexus` (~20MB RSS footprint, sub-50ms startup). Requires GraalVM JDK 17+. |
| **`Nexus: GraalVM - Native Test`** | `test` | Profile: `native` | Compiles and executes native tests in native binary format. |

### F. Dependency Diagnostics
| Configuration Name | Maven Goals | Parameters | Purpose |
|-------------------|-------------|------------|---------|
| **`Nexus: Diagnostic - Dependency Tree`** | `dependency:tree` | - | Displays the hierarchical Maven dependency graph, showing transitive libraries and versions. |
| **`Nexus: Diagnostic - Dependency Analyze`** | `dependency:analyze` | - | Checks for used undeclared dependencies or unused declared dependencies. |

---

## 3. How to Run & Debug

### Running a Configuration
1. Click the **Run/Debug dropdown** on the top right toolbar in IntelliJ.
2. Select any `Nexus: ...` configuration.
3. Click the green **Run** button (`Shift+F10`) or **Debug** button (`Shift+F9`).

### Running Custom CLI Arguments
To run any custom CLI subcommand with Maven in IntelliJ:
1. In the Run Configurations dropdown, select **Edit Configurations...**.
2. Select e.g. `Nexus: CLI - System Info` and click the **Duplicate** icon (`Ctrl+D`).
3. In **Command line**, adjust the arguments:
   ```
   spring-boot:run -Dspring-boot.run.arguments="network"
   ```
   or
   ```
   spring-boot:run -Dspring-boot.run.arguments="services"
   ```
4. Click **OK** and Run.

---

## 4. Git Version Control Policy

The project `.gitignore` is configured to preserve shared project run configurations while ignoring personal workspace files:
- **Tracked in Git**: `.run/*.run.xml` (shared with the team)
- **Ignored in Git**: `.idea/workspace.xml`, `.idea/shelf/`, `target/`, `logs/`, `nexus.token`
