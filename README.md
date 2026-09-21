# Rover Fault Tolerance

A Java simulation of a space mission with a Mother Ship, multiple rovers, and a Ground Control station, built for the Computer Communications course at the University of Minho. The system implements two custom application-layer protocols and is tested inside the CORE network emulator under adverse network conditions (delay, packet loss).

## Protocols

- **MissionLink (UDP):** sends missions to rovers and collects status updates, with custom application-level reliability mechanisms (acknowledgements and retransmissions) since UDP provides none
- **TelemetryStream (TCP):** streams continuous telemetry from each rover back to the Mother Ship

## Features

- Observation API (built with Spark/Jetty) exposing mission and rover state as JSON, consumed by Ground Control
- Ground Control with a JavaFX graphical interface (map view) and a terminal-only fallback
- Network topology defined in CORE via XML, with test variants for different delay/packet-loss scenarios
- Python script (`stats.py`) to analyze logs for duplicate, unique, and lost packets

## Tech stack

Java 21, JavaFX, Spark, Jetty, Gson, CORE network emulator, Python (for log analysis).

## Run locally

Requires the CORE network emulator and manually downloaded dependencies under `libs/` (JavaFX 23 SDK, Spark, Jetty, Gson, SLF4J), none of which are included in this repository.

Inside the CORE topology:

```bash
# On the Mother Ship node, first set up routing
./update_routes.sh

# Compile
./compile.sh

# Mother Ship
./run_MainNaveR.sh
./run_MainNaveGC.sh

# Ground Control (graphical or terminal)
./run_MainGC.sh
./run_MainGC_Terminal.sh

# Rovers
./run_MainRoverN.sh   # N = rover ID
```

Runtime logs are written as `.log` files and can be cleared with `clean_logs.sh`; persistent logs from past test runs are kept under `logs_persistentes/`.

## Team

- David Sousa e Silva
- João Rafael Martins da Costa
- Tomás Pinto
