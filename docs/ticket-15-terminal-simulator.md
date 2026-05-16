# Ticket #15 — Terminal Simulator: MTI 0100 Builder + TCP Client

## What

A standalone Java POS terminal emulator (`tools/terminal-simulator`) with no Spring Boot context. It builds ISO 8583 MTI 0100 Authorization Request messages and sends them over TCP to the acquiring-service switch. Used to exercise the full card authorization path from #16 onwards.

## New Files

| File | Role |
|---|---|
| `cfg/terminal-packager.xml` | jPOS GenericPackager field definitions — ASCII encoding, 128-bit bitmap |
| `message/Iso8583Builder.java` | Builds ISOMsg 0100 and 0400 from TerminalConfig + STAN + amount |
| `message/StanGenerator.java` | Thread-safe STAN counter; wraps 999999 → 1 via `AtomicInteger.updateAndGet` |
| `network/SwitchTcpClient.java` | TCP transport; 4-byte big-endian length prefix framing; implements Closeable |
| `scenario/Scenario.java` | Enum: NORMAL_PURCHASE, OVERLIMIT, TIMEOUT, DUPLICATE |
| `scenario/ScenarioRunner.java` | Dispatches each scenario; TIMEOUT scenario auto-sends 0400 reversal |
| `TerminalSimulator.java` | Updated `main()`: loads config, resolves scenario by name, runs |

## Why

Before #16 (ISO 8583 inbound adapter) can be tested, there must be a client that can:
1. Construct a valid 0100 message (correct bitmap, field lengths, zero-padding)
2. Frame it with a 4-byte length prefix (NACChannel style) so the acquiring-service's Netty server can decode it

The TIMEOUT scenario is educational: in real payment systems, a terminal that doesn't receive a 0110 within its read timeout MUST send a 0400 reversal to prevent double-debit if the switch actually processed the transaction.

## Design Decisions

**ASCII format, not binary**: `IFA_BITMAP` + `IFA_NUMERIC` + `IF_CHAR` matches what domestic acquirers and NPCI NFS use. Wire data is human-readable hex — easier to debug with Wireshark / tcpdump.

**4-byte length prefix**: jPOS NACChannel protocol. Both client (DataOutputStream.writeInt) and server (Netty LengthFieldBasedFrameDecoder with BYTES_4) use this framing.

**`ClassLoader.getResourceAsStream` over `GenericPackager(String)`**: jPOS's string-based constructor resolves the path relative to CWD first, then classpath. The InputStream constructor bypasses the filesystem lookup entirely, which is reliable in both prod (fat JAR) and test (target/classes).

**`IFA_BITMAP` length="16"**: 16 binary bytes = 128 bits. jPOS auto-extends to secondary bitmap when fields 65-128 are set — needed for field 102 (VPA in UPI flow, ticket #19).

**StanGenerator wraps via `updateAndGet`**: ISO 8583 §4.3 requires STAN to be unique per terminal per calendar day. `updateAndGet(v -> v >= 999_999 ? 1 : v + 1)` is atomic — safe for concurrent scenario runners.

## Test Coverage

21 unit tests across 3 test classes:

| Class | Tests | What's covered |
|---|---|---|
| `Iso8583BuilderTest` | 12 | MTI, each critical field (2, 3, 4, 11, 22, 41, 42, 49), pack/unpack round-trip, reversal field 90 |
| `StanGeneratorTest` | 5 | First STAN = 000001, sequential increment, zero-padding, wrap 999999→000001, current() |
| `SwitchTcpClientTest` | 4 | Full send/receive with in-process mock server, length prefix correctness, SocketTimeoutException on silent server, IOException on connection refused |

## How to Verify

```bash
# Run all tests
mvn test -pl tools/terminal-simulator

# Build fat JAR
mvn package -pl tools/terminal-simulator -DskipTests

# Run against acquiring-service (once #16 is up)
java -jar tools/terminal-simulator/target/terminal-simulator-1.0.0-SNAPSHOT.jar NORMAL_PURCHASE
java -jar tools/terminal-simulator/target/terminal-simulator-1.0.0-SNAPSHOT.jar TIMEOUT
java -jar tools/terminal-simulator/target/terminal-simulator-1.0.0-SNAPSHOT.jar DUPLICATE
```

## Wire Protocol

```
Client → Switch:
  [4 bytes big-endian int: N][N bytes ISO 8583 packed body]

Switch → Client:
  [4 bytes big-endian int: M][M bytes ISO 8583 packed 0110 body]

MTI 0100 on wire (ASCII, primary bitmap):
  "0100"                         ← 4 bytes MTI
  "3820000020C08008"             ← 16 hex chars primary bitmap (fields 2,3,4,7,11,12,13,18,22,25,35,37,41,42,43,49)
  "164539148803436467"           ← PAN (LLVAR: "16" + 16 digits)
  "000000"                       ← Processing code
  ...
```
