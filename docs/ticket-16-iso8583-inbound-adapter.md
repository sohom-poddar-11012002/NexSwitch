# Ticket #16 — ISO 8583 Inbound Adapter (Netty TCP Server)

## What

A Netty TCP server inside `adapters/` that receives ISO 8583 authorization, reversal, and network-management messages from the terminal-simulator and any real acquiring terminal.

Five new files:

| File | Role |
|---|---|
| `cfg/acquiring-packager.xml` | jPOS GenericPackager XML — same dialect as terminal-simulator |
| `Iso8583PackagerFactory` | Spring `@Component`; parses XML once at startup; shared read-only singleton |
| `Iso8583Decoder` | `@Sharable MessageToMessageDecoder<ByteBuf>` → `ISOMsg` |
| `Iso8583Encoder` | `@Sharable MessageToByteEncoder<ISOMsg>` → 4-byte length prefix + body |
| `Iso8583RequestHandler` | `@Sharable SimpleChannelInboundHandler<ISOMsg>` — builds 0110/0410/0810 responses |
| `Iso8583ChannelInitializer` | `@Component ChannelInitializer<SocketChannel>` — assembles Netty pipeline per connection |
| `Iso8583TcpServer` | `@Component SmartLifecycle` — boss+worker NioEventLoopGroup, port 8000 default |

## Why

Ticket #17 (authorization flow) needs a running TCP endpoint to route through the 8-step validation chain. The terminal-simulator (#15) can send frames immediately once this server is up.

## Design Decisions

**`LengthFieldBasedFrameDecoder` parameters**
- `maxFrameLength=1MB, offset=0, length=4, adjustment=0, strip=4`
- Strip=4 removes the length header before the body reaches `Iso8583Decoder`; decoder sees pure payload with no stream re-assembly needed.
- `LengthFieldBasedFrameDecoder` is NOT `@Sharable` — it buffers partial frames, so each channel gets its own instance (created in `initChannel`).

**`@Sharable` on decoder/encoder/handler**
- All three are stateless per connection — packager is read-only, response logic has no mutable state.
- Single shared instance avoids allocation on every accepted connection.

**`SmartLifecycle` over `@PostConstruct`**
- `@PostConstruct` fires before the Spring context is fully wired; a `SmartLifecycle.start()` call happens after all beans are ready, preventing lifecycle races when the handler eventually depends on use-case beans.

**Port 0 in tests**
- `@Value("${iso8583.port:8000}")` with a programmatic override of `0` lets the OS pick a free port, eliminating port conflicts in CI.
- `getPort()` reads the bound address from the `ChannelFuture` after bind.

**Response echo rules**
- 0100 → 0110: echo F3, F4, F7, F11, F12, F13, F41, F42; set F38=AUTH01, F39=00
- 0400 → 0410: echo F11, F41; set F39=00
- 0800 → 0810: echo F11; set F39=00
- Unknown MTI → 0110 F39=30 (format error)

## Test Coverage

`Iso8583InboundAdapterTest` — 6 tests, pure Java (no Spring context):

| Test | Verifies |
|---|---|
| `auth0100_receives0110_rc00` | MTI, RC=00, auth code present, STAN and terminal echoed |
| `auth0100_echoesAmountAndProcessingCode` | F3 and F4 carried in 0110 |
| `reversal0400_receives0410_rc00` | MTI, RC=00, STAN echo |
| `networkManagement0800_receives0810_rc00` | MTI, RC=00, STAN echo |
| `unknownMti_receives0110_rc30` | Error response for unrecognized MTI |
| `multipleSequentialRequestsOnSameConnection` | Three back-to-back 0100→0110 on a single socket; verifies framing correctness and STAN correlation |

## How to Verify

```bash
# Run adapter tests
mvn test -pl adapters --no-transfer-progress

# Start acquiring-service on port 8000 (future ticket), then run terminal-simulator
java -jar tools/terminal-simulator/target/*.jar NORMAL_PURCHASE
```
