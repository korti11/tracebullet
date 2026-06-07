<p>
  <img src="src/main/resources/logo.png" alt="TraceBullet">
</p>

A [NeoForge](https://neoforged.net/) mod that instruments a Minecraft server with [OpenTelemetry](https://opentelemetry.io/), exporting metrics via OTLP to any compatible collector (Prometheus, Grafana Alloy, OpenTelemetry Collector, Dynatrace, etc.).

## Metrics

| Metric | Description |
|--------|-------------|
| TPS & tick time | Server ticks per second and mean tick duration |
| Player count & latency | Online player count and per-player network latency |
| Chunk counts | Loaded chunk counts per dimension |
| Entity counts | Entity counts per dimension |
| Block entity counts | Block entity counts per dimension |
| Item entity counts | Item entity (dropped item) counts per dimension |
| World save duration | Histogram of world save time per dimension |
| Scheduled tick queue | Block and fluid scheduled tick queue sizes per dimension |
| Chunk generation | Counter of newly generated chunks |
| JVM metrics | Heap/non-heap memory, GC, threads, classes |

## Requirements

- Minecraft 26.1.2 with NeoForge 26.1.2.71+
- Java 21
- A running OTLP-compatible metrics endpoint

Primarily tested with the [OpenTelemetry Collector (contrib)](https://github.com/open-telemetry/opentelemetry-collector-contrib) + [Prometheus](https://prometheus.io/) + [Grafana](https://grafana.com/) stack. Other OTLP receivers (Grafana Alloy, Dynatrace, etc.) should work but are less tested.

## Installation

1. Download the latest JAR from [Releases](../../releases) and drop it in your server's `mods/` folder.
2. Start the server once to generate the config file at `config/tracebullet-server.toml`.
3. Set `otel.url` to your collector's OTLP endpoint and restart.

## Configuration

The config file is generated at `config/tracebullet-server.toml` on first launch.

### OTel settings

| Key | Default | Description |
|-----|---------|-------------|
| `otel.url` | `""` | OTLP endpoint URL (required to export anything) |
| `otel.auth` | `""` | Authorization header value — must start with `Bearer` or `Basic` |
| `otel.service_name_override` | `""` | Override `service.name`; defaults to the current world name |
| `otel.custom_attributes` | `[]` | Extra resource attributes in `key=value` format |

### Threading

| Key | Default | Description |
|-----|---------|-------------|
| `threading.schedule_thread_pool_size` | `availableProcessors × 0.25` | Background thread pool size (1–32) |

### Metric toggles

Each metric group can be individually enabled or disabled. All default to `true`.

| Key | Controls |
|-----|----------|
| `metrics.tps` | TPS and tick time |
| `metrics.player` | Player count and latency |
| `metrics.world_chunk` | Chunk counts per dimension |
| `metrics.entity` | Entity counts per dimension |
| `metrics.block_entity` | Block entity counts per dimension |
| `metrics.item_entity` | Item entity counts per dimension |
| `metrics.world_save` | World save duration histogram |
| `metrics.scheduled_tick` | Scheduled tick queue sizes |
| `metrics.chunk_generation` | Chunk generation counter |
| `metrics.jvm` | JVM memory, GC, threads |

## Building from source

```bash
./gradlew build
```

The output JAR is placed in `build/libs/`.

## License

MIT — see [LICENSE](LICENSE).
