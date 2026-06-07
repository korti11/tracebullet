# Contributing to TraceBullet

## Prerequisites

- Java 25
- IntelliJ IDEA (recommended) or Eclipse

## Building locally

```bash
./gradlew build
```

Output JAR is placed in `build/libs/`.

## Running in a dev environment

```bash
./gradlew server   # headless Minecraft server
./gradlew client   # Minecraft client with GUI
```

## Branch model

| Branch | Purpose |
|--------|---------|
| `26.1.x`, `26.2.x`, ... | One long-lived branch per Minecraft version |
| `feat/<name>`, `fix/<name>` | Short-lived branches opened as PRs against the relevant version branch |

## Commit messages

This project uses [Conventional Commits](https://www.conventionalcommits.org/):

| Prefix | When to use |
|--------|------------|
| `feat:` | New metric or user-visible feature |
| `fix:` | Bug fix |
| `chore:` | Build, CI, or tooling changes |
| `docs:` | Documentation only |
| `refactor:` | Code change with no user-visible effect |

## Adding a new metric

1. Create a class in `src/main/java/io/korti/tracebullet/metrics/` implementing `Metric`
2. Subscribe to `RegisterMetricEvent` and `UnregisterMetricEvent` on the NeoForge event bus
3. In `onRegister`, obtain an OTel instrument via `event.getMeterProvider().get(InstrumentationScopeNames.SERVER|WORLD)` and schedule writes via `ThreadPoolManager`
4. In `onUnregister`, cancel the scheduled task and null out instrument references
5. If the metric needs a live `MinecraftServer` reference, subscribe to `ServerStartedEvent` / `ServerStoppingEvent` to acquire/release it
6. Register the subscriber in `TraceBullet.registerMetrics()`

## Pull requests

External contributors should fork the repository and open a PR from their fork against the relevant version branch (e.g. `26.1.x`). Direct pushes to version branches are restricted to maintainers.

- One logical change per PR
- CI must pass before merge
