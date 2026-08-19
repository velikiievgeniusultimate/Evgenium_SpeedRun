# Building Evgenium SpeedRun

## Requirements

- JDK 25
- Gradle 9.5.1

The project currently uses system Gradle in CI. A Gradle Wrapper will be added as a follow-up once the initial Fabric 26.2 foundation is validated.

## Build

```bash
gradle build
```

Compiled jars are written to:

```text
build/libs/
```

## Development client

```bash
gradle runClient
```

## Version pins

The authoritative version pins live in `gradle.properties`:

- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.156.0+26.2
- Fabric Loom 1.17-SNAPSHOT
