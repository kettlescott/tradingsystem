# Quick Start Guide - Market Data Integration

## For Impatient Engineers 🚀

### Run It Now

```bash
# 1. Build
mvn clean package -DskipTests

# 2. Test with simulated data (15 seconds)
java -cp target/your-artifact-name-1.0.0-SNAPSHOT.jar \
  trading.core.app.TradingEngine 15

# 3. Test with JSON file (if you have market-data.jsonl)
java -cp target/your-artifact-name-1.0.0-SNAPSHOT.jar \
  trading.core.app.TradingEngine 60 json-lines market-data.jsonl
```

---

## Add Your Exchange in 3 Steps

### Step 1: Create Decoder (5 mins)

```java
package trading.core.marketdata;

public class MyExchangeDecoder {
    public void decode(String rawMessage, DecodedTick target) {
        // Parse your message format into bid/ask/instrumentId
        // Example for your exchange protocol:
        target.instrumentId = extractInstrumentId(rawMessage);
        target.bid = extractBid(rawMessage);
        target.ask = extractAsk(rawMessage);
        target.exchangeTsNanos = System.nanoTime();
    }
}
```

### Step 2: Create Source (10 mins)

```java
package trading.core.marketdata;

import core.marketdata.MarketDataSource;

public class MyExchangeMarketDataSource implements MarketDataSource {
    private final MyExchangeWebSocketClient wsClient;
    private final MyExchangeDecoder decoder;

    public MyExchangeMarketDataSource(String apiKey, String symbol) {
        this.wsClient = new MyExchangeWebSocketClient(apiKey);
        this.decoder = new MyExchangeDecoder();
    }

    @Override
    public void publishTo(RingBuffer<TickEvent> ringBuffer, AtomicBoolean running) {
        wsClient.subscribe(symbol);

        while (running.get()) {
            String message = wsClient.nextMessage(); // blocking call
            if (message != null) {
                decoder.decode(message, decodedTick);

                long seq = ringBuffer.next();
                try {
                    TickEvent event = ringBuffer.get(seq);
                    TickEventPublisher.populateMarketTick(
                            event, seq,
                            decodedTick.getInstrumentId(),
                            decodedTick.getBid(),
                            decodedTick.getAsk(),
                            decodedTick.getExchangeTsNanos(),
                            System.nanoTime()
                    );
                } finally {
                    ringBuffer.publish(seq);
                }
            }
        }
    }

    @Override
    public void close() {
        wsClient.disconnect();
    }
}
```

### Step 3: Register in TradingEngine (5 mins)

```java
// In TradingEngine.createMarketDataSource()
private static MarketDataSource createMarketDataSource(String[] args) {
    if (args.length >= 3 && "myexchange".equalsIgnoreCase(args[1])) {
        return new MyExchangeMarketDataSource(args[2], args[3]);
    }
    
    // ... existing modes
    return new SimulatedMarketDataSource(...);
}
```

### Run It

```bash
mvn clean package -DskipTests
java -cp target/your-artifact-name-1.0.0-SNAPSHOT.jar \
  trading.core.app.TradingEngine 300 myexchange "YOUR_API_KEY" "BTC/USDT"
```

**Total time: ~20 minutes for simple exchange** ⚡

---

## Template: JSON-Based Exchange

If your exchange uses JSON over WebSocket:

```java
public class JsonWebSocketSource implements MarketDataSource {
    private final JsonTickDecoder decoder = new JsonTickDecoder(INSTRUMENT_ID);
    private final WebSocketClient ws;
    
    @Override
    public void publishTo(RingBuffer<TickEvent> ringBuffer, AtomicBoolean running) {
        ws.connect(WS_URL);
        ws.subscribe(channel);
        
        while (running.get()) {
            String jsonMsg = ws.nextMessage();
            if (jsonMsg != null && !jsonMsg.isBlank()) {
                decoder.decode(jsonMsg, decodedTick);
                publishToRingBuffer(ringBuffer, decodedTick);
            }
        }
    }
    
    private void publishToRingBuffer(RingBuffer<TickEvent> ringBuffer, 
                                      JsonTickDecoder.DecodedTick tick) {
        long seq = ringBuffer.next();
        try {
            TickEvent event = ringBuffer.get(seq);
            TickEventPublisher.populateMarketTick(event, seq,
                tick.getInstrumentId(),
                tick.getBid(),
                tick.getAsk(),
                tick.getExchangeTsNanos(),
                System.nanoTime());
        } finally {
            ringBuffer.publish(seq);
        }
    }
}
```

---

## Template: Binary Protocol Exchange (FIX, SBE, OUCH)

For ultra-low-latency:

```java
public class BinaryProtocolSource implements MarketDataSource {
    private final BinaryMessageDecoder decoder;
    private final TcpClient tcpClient;
    
    @Override
    public void publishTo(RingBuffer<TickEvent> ringBuffer, AtomicBoolean running) {
        tcpClient.connect(EXCHANGE_HOST, EXCHANGE_PORT);
        
        while (running.get()) {
            ByteBuffer buffer = tcpClient.readNextMessage();
            if (buffer != null) {
                decoder.decode(buffer, decodedTick);
                
                long seq = ringBuffer.next();
                try {
                    TickEvent event = ringBuffer.get(seq);
                    TickEventPublisher.populateMarketTick(event, seq,
                        decodedTick.getInstrumentId(),
                        decodedTick.getBid(),
                        decodedTick.getAsk(),
                        decodedTick.getExchangeTsNanos(),
                        System.nanoTime());
                } finally {
                    ringBuffer.publish(seq);
                }
            }
        }
    }
}
```

**Expected latency improvement: 10-20x faster than JSON** ⚡⚡

---

## Testing Your Implementation

### Unit Test Template

```java
@Test
void decodesRawMessageIntoNormalizedTick() {
    MyExchangeMarketDataSource source = new MyExchangeMarketDataSource(
        "API_KEY", "BTC/USDT"
    );
    
    MyExchangeDecoder.DecodedTick tick = new MyExchangeDecoder.DecodedTick();
    decoder.decode(rawMessageFromExchange, tick);
    
    assertEquals(1, tick.getInstrumentId());
    assertEquals(60000.5, tick.getBid(), 0.01);
    assertEquals(60001.0, tick.getAsk(), 0.01);
    assertTrue(tick.getExchangeTsNanos() > 0);
}
```

### Integration Test Template

```java
@Test
void publishesMessagesToRingBuffer() throws Exception {
    MyExchangeMarketDataSource source = ...;
    Disruptor<TickEvent> disruptor = ...;
    CountDownLatch received = new CountDownLatch(1);
    
    disruptor.handleEventsWith((event, seq, eob) -> {
        assertEquals(60000.5, event.bid);
        received.countDown();
    });
    
    source.publishTo(disruptor.start(), running);
    assertTrue(received.await(2, TimeUnit.SECONDS));
}
```

---

## Performance Tuning Checklist

- [ ] **Latency Baseline**: Run with `SimulatedMarketDataSource` to get expected p99
- [ ] **Network I/O**: Ensure socket reads don't block the main loop
- [ ] **Parser Overhead**: Profile JSON parsing vs binary parsing
- [ ] **Memory Allocation**: Check GC logs, aim for zero allocations per message
- [ ] **Thread Affinity**: Pin market data thread to a dedicated CPU core
- [ ] **Buffer Sizing**: Match Disruptor ring buffer to your throughput

**Command to profile:**
```bash
java -XX:+FlightRecorder \
     -XX:StartFlightRecording=dumponexit=true,filename=recording.jfr \
     -cp target/*.jar trading.core.app.TradingEngine 60
# Open recording.jfr in JDK Mission Control
```

---

## Common Issues & Fixes

| Issue | Cause | Fix |
|-------|-------|-----|
| `Events not publishing` | `running` set to false too early | Check main thread sleep/join timing |
| `High latency p99` | JSON parsing too slow | Switch to binary protocol or pre-parse |
| `Memory leak` | Ring buffer events not released | Ensure `ringBuffer.publish()` is called |
| `Network timeout` | Exchange disconnecting | Add reconnection logic in `publishTo()` |
| `Out of order events` | Multiple producer threads | Keep `ProducerType.SINGLE` in Disruptor config |

---

## File Structure After Integration

```
tradingsystem/
├── src/trading/core/
│   ├── app/
│   │   └── TradingEngine.java (modified)
│   ├── event/
│   │   └── TickEvent.java
│   ├── execution/
│   │   └── ExecutionHandler.java
│   ├── risk/
│   │   └── RiskHandler.java
│   ├── gateway/
│   │   └── OrderGateway.java
│   └── marketdata/
│       ├── MarketDataSource.java (interface)
│       ├── SimulatedMarketDataSource.java
│       ├── JsonMarketDataSource.java
│       ├── JsonTickDecoder.java
│       ├── MyExchangeMarketDataSource.java (YOUR IMPLEMENTATION)
│       └── TickEventPublisher.java
├── src/test/java/trading/core/marketdata/
│   ├── JsonTickDecoderTest.java
│   ├── JsonMarketDataSourceTest.java
│   ├── SimulatedMarketDataSourceTest.java
│   └── MyExchangeMarketDataSourceTest.java (YOUR TESTS)
├── pom.xml
├── REFACTORING.md
└── SUMMARY.md
```

---

## Key Files to Read

1. **SUMMARY.md** - Overview of what changed
2. **REFACTORING.md** - Deep dive into architecture
3. **TradingEngine.java** - Entry point, see `createMarketDataSource()`
4. **MarketDataSource.java** - Interface you're implementing
5. **JsonMarketDataSource.java** - Example implementation to copy

---

## One-Liner Commands

```bash
# Build
mvn clean package -DskipTests

# Test everything
mvn test

# Run with simulated data for 30 seconds
java -cp target/*.jar trading.core.app.TradingEngine 30

# Run with JSON file
java -cp target/*.jar trading.core.app.TradingEngine 60 json-lines data.jsonl

# Profile with JFR
java -XX:+FlightRecorder -XX:StartFlightRecording=dumponexit=true -cp target/*.jar trading.core.app.TradingEngine 60
```

---

## Still Have Questions?

- **Q: Where do I put my exchange credentials?**  
  A: Pass via command line args or environment variables, never hardcode them.

- **Q: Can I test my exchange source in isolation?**  
  A: Yes! Mock the WebSocket/TCP client and test the decoder independently.

- **Q: What's the performance expectation for my exchange?**  
  A: Depends on protocol. JSON: 5-50μs per message. Binary: 1-10μs per message.

- **Q: How do I monitor if my exchange is disconnected?**  
  A: Add a `lastMessageTimestamp` check and reconnect logic in `publishTo()`.

---

**You're ready! Start integrating your exchange and happy trading! 🎉**

