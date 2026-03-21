"# tradingsystem"





&#x20;               ┌────────────────────┐

&#x20;               │   Exchange (API)   │

&#x20;               └─────────┬──────────┘

&#x20;                         ↑

&#x20;                  Order Gateway (Java)

&#x20;                         ↑

&#x20;               ┌─────────┴──────────┐

&#x20;               │ Execution Engine   │  ← 核心（低延迟）

&#x20;               └─────────┬──────────┘

&#x20;                         ↑

&#x20;                  Risk Engine (Java)

&#x20;                         ↑

&#x20;                  Signal Bus（Kafka/Disruptor）

&#x20;                         ↑

&#x20;       ┌────────────┬──────────────┐

&#x20;       │            │              │

&#x20;Python Strategy  Python Strategy  Java Strategy

&#x20;(进程隔离)         (进程隔离)        (可选)

&#x20;       ↑

&#x20;  Market Data Feed（Java）

&#x20;       ↑

&#x20;  WebSocket / TCP Feed

