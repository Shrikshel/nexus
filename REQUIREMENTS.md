Project: Cross-Platform Host Management Agent (Nexus)

Act as a senior software architect and engineer. Design and build a robust, lightweight, cross-platform host management agent with a first-class CLI and remote integrations.

The priority is reliability, security, extensibility, and low runtime memory consumption. Binary size is not a major concern (100–200 MB is acceptable).

1. Technology Stack
Java with Spring Boot.
GraalVM Native Image for producing standalone native executables without requiring a JVM at runtime.
Picocli for the CLI.
Maven for dependency and build management.
Target Linux, Windows, and macOS.
Prefer mature, actively maintained libraries.
Avoid unnecessary dependencies and heavyweight frameworks.
Do not introduce a database unless a concrete requirement justifies it.

The application should be headless. Do not start an embedded web server unless REST functionality is enabled.

2. Operating Modes

The same executable must support two operating modes:

On-demand CLI mode

Individual commands execute and exit.
No permanently running background process is required.
All capabilities must be accessible through the CLI, subject to permissions.

Daemon mode

Runs continuously as a background service.
Supports Telegram connectivity, notifications, scheduled tasks, and other enabled integrations.
Exposes a local, authenticated IPC interface for CLI-to-daemon communication where appropriate.

Both modes MUST share the same application services, capability implementations, authorization logic, and execution engine. Never duplicate business logic between CLI and remote interfaces.

3. Architecture and Design Principles

Use clean architecture, SOLID principles, dependency inversion, and interface-driven development.

Build a modular monolith with clearly separated layers:

Domain
Application services
Capability/plugin system
Infrastructure and platform abstractions
CLI adapters
Remote communication adapters
Notification system
Security and authorization

Implement a pluggable capability architecture using interfaces, a registry, and dependency injection.

Each capability should:

Have a unique name and description.
Define its required permissions.
Define its input and output.
Execute independently.
Support cancellation, timeouts, and structured errors.
Be independently testable.

Start with statically compiled and registered capabilities to preserve GraalVM compatibility. Avoid Java dynamic plugin mechanisms that complicate Native Image builds. External plugin support can be considered later.

Adding a capability should not require modifications to existing capabilities or communication adapters.

4. CLI

The CLI is a first-class interface, not an afterthought.

Use Picocli and provide intuitive commands, subcommands, help, argument validation, useful error messages, and machine-readable output (JSON).

Illustrative commands:

nexus info
nexus uptime
nexus cpu
nexus memory
nexus disk list
nexus disk smart
nexus process list
nexus network
nexus services
nexus capabilities list
nexus capabilities describe smart
nexus status
nexus start
nexus stop
nexus config validate

The command structure should be extensible and capability-driven wherever practical.

5. Communication Channels

Communication integrations are independent adapters.

Initial priority: Telegram.

Future integrations: REST API, email, and potentially other messaging platforms.

IMPORTANT: Every communication channel can have two responsibilities:

Inbound

Receive commands and requests.
Trigger capabilities.
Handle interactive confirmations where appropriate.

Outbound

Send notifications and alerts.
Deliver command execution results.
Report failures and agent health events.

Telegram must support BOTH inbound commands and outbound notifications.

Examples:

/info returns system information.
/smart returns disk SMART reports.
Scheduled SMART checks proactively notify Telegram about disk degradation.
Critical agent failures generate notifications.
Results of tasks initiated through other channels can be delivered through configured notification channels.

Use a common internal request model so all communication channels invoke the same application services.

Implement a centralized notification dispatcher. Capabilities must publish application events or notifications without depending directly on Telegram or any other provider.

6. Initial Capabilities

Design the capability system to accommodate:

System information.
CPU and memory monitoring.
Disk and filesystem information.
SMART health monitoring.
Process monitoring.
Network information.
Service monitoring and management.
System logs and diagnostics.
Scheduled health checks.

Use platform-specific implementations behind common interfaces. Do not assume Linux commands or utilities exist on Windows and macOS.

For SMART monitoring, use appropriate OS and hardware integrations, including smartmontools where suitable.

7. Privilege Management and Security

The agent must support both ordinary user-level operations and privileged administrative operations.

Prefer a security-conscious architecture that keeps the communication-facing agent unprivileged and exposes narrowly scoped privileged operations through a separate helper or OS-specific privileged service when necessary.

Do NOT expose arbitrary root shell execution.

Implement:

Explicit capability-level permission requirements.
Authorization on every operation.
Telegram sender and chat allowlisting.
Secure REST authentication when introduced.
IPC authentication and access controls.
Audit logging.
Secret management.
Rate limiting.
Timeouts and cancellation.
Confirmation for potentially disruptive operations.
Safe error handling without leaking sensitive information.

Never trust an incoming request simply because it originated from an authenticated communication channel.

8. Reliability and Observability

Robustness is a primary requirement.

Design for long-running operation and recovery:

Graceful startup and shutdown.
Automatic Telegram reconnection with exponential backoff.
Resilient network handling.
Structured logging.
Health monitoring and heartbeat.
Task lifecycle tracking.
Duplicate request protection/idempotency.
Resource and concurrency limits.
Graceful handling of capability failures.
Audit trail of executed operations.
Configurable notification policies.

One failing capability or communication provider must not crash the entire agent.

9. GraalVM Native Image

Native executable generation is a core deployment requirement, not an afterthought.

Keep the implementation compatible with GraalVM Native Image:

Minimize runtime reflection and dynamic class loading.
Account for serialization and reflection metadata.
Verify third-party library compatibility.
Ensure dependency injection and Picocli work in native mode.
Produce native executables for each supported OS and architecture.
Test the actual native binaries, not just JVM builds.

Measure actual idle and active memory consumption. Optimize runtime footprint without compromising reliability.

10. Development Expectations

Before implementing everything:

Propose the architecture and explain important trade-offs.
Define the module/package structure.
Define the core interfaces and capability contracts.
Explain the privilege boundary and IPC design.
Establish the build and native compilation strategy.
Identify technical risks and compatibility limitations.

Then implement incrementally.

Start with:

Application bootstrap.
Configuration.
Logging.
Capability registry.
Execution engine.
Picocli CLI.
System information and uptime capabilities.
Automated tests.

Next add Telegram inbound commands and outbound notifications, followed by SMART monitoring and the privilege architecture.

Do not generate an enormous amount of code in one step. Work in small, independently testable increments.

Write unit tests and integration tests. Include documentation, configuration examples, build instructions, and native deployment instructions.

Final objective: A reliable, low-memory, cross-platform host management agent distributed as a native executable, usable locally through a powerful CLI and remotely through bidirectional communication channels, with a secure and extensible capability architecture.