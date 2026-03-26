# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

TraceBullet is a NeoForge Minecraft mod (1.21.x) that instruments a Minecraft server with OpenTelemetry, collecting metrics like TPS, player count, chunk counts, and entity counts and exporting them via OTLP to an external observability collector.

## Build Commands

```bash
./gradlew build              # Build the mod JAR
./gradlew client             # Run Minecraft client in dev environment
./gradlew server             # Run Minecraft server in dev environment
./gradlew gameTestServer     # Run game tests
./gradlew clean              # Clean build artifacts
```

Key versions: Minecraft 1.21.11, NeoForge 21.11.38-beta, Java 21, OpenTelemetry 1.59.0.

## Architecture

**Event-driven metrics pipeline:**

```
Server lifecycle events (NeoForge event bus)
    → OpenTelemetryMetricRegistry (posts RegisterMetricEvent / UnregisterMetricEvent)
    → Individual Metric implementations subscribe and create OTel instruments
    → ScheduledExecutorService (via ThreadPoolManager) calls metric.write() periodically
    → OpenTelemetry SDK exports via OTLP to configured endpoint
```

**Key layers:**

- `api/metrics/` — Interfaces: `Metric` (write + getWritingPeriod), `MetricRegistry`, `MetricEvent`; `InstrumentationScopeNames` constants (`SERVER`, `WORLD`) used when calling `event.getMeterProvider().get(...)`
- `otel/` — `OpenTelemetryMetricRegistry` schedules writes; `OpenTelemetrySetupHandler` initializes OTel SDK
- `metrics/` — Concrete implementations: `TPSMetric`, `PlayerCountMetric`, `WorldChunkMetrics`, `EntityMetrics`
- `threading/` — `ThreadPoolManager` owns all mod thread pools; `ThreadPool` enum identifies them (currently only `METRIC`)
- `Config.java` — NeoForge ModConfig with OTel endpoint URL, service name, custom attributes, and thread pool sizing (defaults to 25% of available processors)

**Metric lifecycle:** Metrics register on server start (subscribe to `RegisterMetricEvent`) and unregister on server stop (`UnregisterMetricEvent`). Each metric schedules its own periodic write task and cancels it on unregister.

**Dependency embedding:** OpenTelemetry jars are packaged via `jarJar` in `build.gradle` so the mod is self-contained.

## Adding a New Metric

1. Create a class in `metrics/` implementing `Metric`
2. Subscribe to `RegisterMetricEvent` and `UnregisterMetricEvent` on the NeoForge event bus
3. In `onRegister`, obtain an OTel instrument via `event.getMeterProvider().get(InstrumentationScopeNames.SERVER|WORLD)` and schedule writes via `ThreadPoolManager`
4. In `onUnregister`, cancel the scheduled task and null out instrument references
5. If the metric needs a live `MinecraftServer` reference, also subscribe to `ServerStartedEvent` / `ServerStoppingEvent` to acquire/release it (see `EntityMetrics` for the pattern)
6. Register the event subscriber in `TraceBullet.registerMetrics()`
