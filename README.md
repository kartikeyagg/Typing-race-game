# TypeShift

TypeShift is a self-contained LAN typing race inspired by the immediacy and competitive track presentation of Nitro Type. One Java server hosts the game, keeps every participant's progress in sync, simulates three computer racers, and exposes live distance plus stepper-motor rotation data.

## Start a race

Requirements: Java 11+ and Maven 3.6+.

```bash
mvn clean package
java -jar target/typing-race.jar --wheel-radius=0.03
```

The server binds to every network interface on port **7894**. At startup it prints addresses similar to:

```text
This computer: http://localhost:7894
Same Wi-Fi:    http://192.168.1.42:7894
```

Open the local address on the server computer. Other participants connected to the same Wi-Fi open the printed `Same Wi-Fi` address. No separate client installation is required.

## Shared waiting lobby

Every driver joins the same waiting lobby before a heat. The first person to join is the lobby host. Once at least two human drivers are present, the host can select **Start Race**. A server-synchronized **3-2-1** countdown appears for every driver before the heat begins and typing unlocks. The typing passage stays locked while the lobby is waiting and during the countdown, and new drivers cannot join once the countdown begins.

Use **Return to Lobby** after a heat to reset every connected driver and wait for the next start. The same host remains in control of the start button for subsequent heats.

If other devices cannot connect, verify that they are on the same non-guest network and allow inbound TCP port `7894` through the server computer's firewall. Guest Wi-Fi commonly prevents devices from communicating with one another.

The wheel radius is in metres and is fixed when the process starts. It can also be supplied with an environment variable:

```bash
WHEEL_RADIUS_METERS=0.05 java -jar target/typing-race.jar
```

`--wheel-radius` takes precedence over `WHEEL_RADIUS_METERS`. The optional `--port` argument exists for development; the normal LAN port is `7894`.

## Distance and motor API

Read all racers with:

```bash
curl http://localhost:7894/api/race/distances
```

The endpoint is public on the LAN and has CORS enabled for hardware dashboards. Its response contains the shared race configuration and one entry per participant:

```json
{
  "raceId": "5F9C1A20",
  "status": "RUNNING",
  "trackLengthMeters": 100.0,
  "wheelRadiusMeters": 0.03,
  "stepAngleDegrees": 1.8,
  "participants": [
    {
      "id": 1001,
      "name": "NightShift",
      "position": 1,
      "typedCharacters": 84,
      "progressPercent": 37.33,
      "distanceMeters": 37.3333,
      "wpm": 62.4,
      "accuracyPercent": 98.8,
      "finished": false,
      "rotation": {
        "radians": 1244.443333,
        "degrees": 71291.4,
        "steps": 39606
      }
    }
  ]
}
```

`rotation.radians` is the exact wheel rotation (`distance / radius`). `rotation.degrees` is snapped to the nearest whole 1.8° motor step and is therefore always a multiple of 1.8. `rotation.steps` can be sent directly as the target step count.

Other endpoints:

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/health` | Server health check |
| `GET` | `/api/race` | Full race state, including the typing passage |
| `GET` | `/api/race/distances` | Participant distances and motor output |
| `POST` | `/api/race/participants` | Join with `{ "name": "Ada", "color": "#22D3EE" }` |
| `POST` | `/api/race/start` | Lobby host starts the heat with `{ "participantId": 1001 }` |
| `PUT` | `/api/race/participants/{id}/progress` | Report `{ "typedCharacters": 42, "errors": 1 }` |
| `POST` | `/api/race/reset` | Return every connected participant to the waiting lobby |

## Stepper conversion class

Hardware integrations can use the converter independently:

```java
StepperAngleConverter converter = new StepperAngleConverter(0.03); // radius in metres
StepperAngleConverter.Rotation rotation = converter.convert(2.5);  // distance in metres

long steps = rotation.getSteps();
double degrees = rotation.getDegrees(); // multiple of 1.8
double radians = rotation.getRadians(); // exact, before step quantization
```

The class rejects zero/negative radii and negative/non-finite distances.

## Development

```bash
mvn test
mvn package
```

The UI is plain HTML, CSS, and JavaScript under `src/main/resources/web`; the Maven package step embeds it in the runnable JAR.
