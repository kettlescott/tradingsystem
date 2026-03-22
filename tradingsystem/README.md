# Trading System (Low-Latency Skeleton)

This repo now includes a staged Disruptor pipeline:

1. market data producer -> `TickEvent`
2. synchronous `RiskHandler`
3. `ExecutionHandler` (order decision + latency histogram)
4. bounded async `OrderGateway`

## What changed

- Added risk gate (`src/trading/core/risk/RiskHandler.java`)
- Reworked event payload for low-allocation fields (`instrumentId`, `sequence`, timestamps)
- Added histogram-based p99/p999 reporting in execution path
- Added bounded gateway queue with submitted/dropped counters
- Added tiny benchmark harness (`src/trading/core/app/EngineBenchmark.java`)

## Quick start

Run tests:

```powershell
mvn -q test
```

Run engine for 15 seconds:

```powershell
mvn -q "exec:java" "-Dexec.mainClass=trading.core.app.TradingEngine" "-Dexec.args=15"
```

Run benchmark harness for 30 seconds:

```powershell
mvn -q "exec:java" "-Dexec.mainClass=trading.core.app.EngineBenchmark" "-Dexec.args=30"
```

> If `exec:java` is unavailable in your local Maven setup, run with IDE launch config using main class `trading.core.app.TradingEngine`.

