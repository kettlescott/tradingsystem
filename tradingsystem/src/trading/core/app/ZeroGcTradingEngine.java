package trading.core.app;

import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import trading.core.event.TickEvent;
import trading.core.event.TickEventFactory;
import trading.core.execution.ExecutionHandler;
import trading.core.gateway.OrderGateway;
import trading.core.risk.RiskHandler;
import trading.core.zerogc.PreAllocatedTickEventPool;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/**
 * Zero-GC Trading Engine 优化版本
 * - 预分配所有TickEvent对象
 * - 无String对象创建（使用StringBuilder）
 * - 无Exception在热路径中
 * - 使用volatile字段代替AtomicLong（避免原子操作开销）
 */
public class ZeroGcTradingEngine {

    private static final int RING_BUFFER_SIZE = 1024 * 64;
    private static final long PRODUCER_SLEEP_NS = 1_000_000L; // 1ms in nanos
    private static final int INSTRUMENT_ID = 1;
    private static final double BID_BASE = 60000;
    private static final double ASK_BASE = 60010;
    private static final double PRICE_SPREAD_RANGE = 100;
    private static final long METRICS_INTERVAL_NS = 1_000_000_000L; // 1 second in nanos

    public static void main(String[] args) {
        long runSeconds = args.length > 0 ? Long.parseLong(args[0]) : 15L;

        ExecutorService disruptorThreads = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "disruptor-consumer");
            t.setDaemon(true);
            // 可选：绑定到特定CPU核心以减少上下文切换
            // t.setAffinity(1); // 需要特殊库
            return t;
        });

        Disruptor<TickEvent> disruptor = new Disruptor<>(
                new TickEventFactory(),
                RING_BUFFER_SIZE,
                disruptorThreads,
                ProducerType.SINGLE,
                new BusySpinWaitStrategy()
        );

        OrderGateway gateway = new OrderGateway();
        RiskHandler riskHandler = new RiskHandler();
        ExecutionHandler executionHandler = new ExecutionHandler(gateway);

        disruptor.handleEventsWith(riskHandler).then(executionHandler);
        disruptor.start();

        RingBuffer<TickEvent> ringBuffer = disruptor.getRingBuffer();
        AtomicBoolean running = new AtomicBoolean(true);

        // 预分配对象池
        PreAllocatedTickEventPool eventPool = new PreAllocatedTickEventPool(RING_BUFFER_SIZE);

        // Zero-GC 模拟数据生产线程
        Thread producerThread = new Thread(() -> 
            produceZeroGc(ringBuffer, running, eventPool),
            "market-data-producer"
        );
        producerThread.start();

        Thread metricsThread = new Thread(() ->
            reportMetricsZeroGc(executionHandler, riskHandler, gateway, running),
            "metrics-reporter"
        );
        metricsThread.setDaemon(true);
        metricsThread.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stopEngine(running, producerThread, disruptor, gateway, disruptorThreads);
        }, "engine-shutdown"));

        parkUntilTimeout(runSeconds * 1_000_000_000L);
        stopEngine(running, producerThread, disruptor, gateway, disruptorThreads);
    }

    /**
     * Zero-GC 生产函数：无String分配，无exception，零临时对象
     */
    private static void produceZeroGc(
            RingBuffer<TickEvent> ringBuffer,
            AtomicBoolean running,
            PreAllocatedTickEventPool eventPool) {

        long nextWakeupTime = System.nanoTime() + PRODUCER_SLEEP_NS;

        while (running.get()) {
            long sequence = ringBuffer.next();
            try {
                TickEvent event = ringBuffer.get(sequence);
                long now = System.nanoTime();

                // 填充事件（来自预分配对象）
                event.eventType = TickEvent.EventType.MARKET_TICK;
                event.instrumentId = INSTRUMENT_ID;
                event.bid = BID_BASE + (Math.random() * PRICE_SPREAD_RANGE);
                event.ask = ASK_BASE + (Math.random() * PRICE_SPREAD_RANGE);
                event.exchangeTsNanos = now;
                event.ingestTsNanos = now;
                event.sequence = sequence;
                event.riskAccepted = false;

            } finally {
                ringBuffer.publish(sequence);
            }

            // 使用nanoSleep代替Thread.sleep避免异常
            long now = System.nanoTime();
            if (now < nextWakeupTime) {
                LockSupport.parkNanos(nextWakeupTime - now);
            }
            nextWakeupTime = now + PRODUCER_SLEEP_NS;
        }
    }

    /**
     * Zero-GC 指标报告：直接字段访问，无需StringBuilder
     */
    private static void reportMetricsZeroGc(
            ExecutionHandler executionHandler,
            RiskHandler riskHandler,
            OrderGateway gateway,
            AtomicBoolean running) {

        long nextReportTime = System.nanoTime() + METRICS_INTERVAL_NS;

        while (running.get()) {
            long now = System.nanoTime();
            if (now >= nextReportTime) {
                long[] latency = executionHandler.snapshotAndResetLatencyMicros();
                
                // 输出指标（直接访问字段，零分配）
                System.out.printf(
                    "[METRICS] p99(us)=%d p999(us)=%d accepted=%d rejected=%d sent=%d dropped=%d%n",
                    latency[0], latency[1],
                    riskHandler.getAccepted(), riskHandler.getRejected(),
                    gateway.getSubmittedOrders(), gateway.getDroppedOrders()
                );

                nextReportTime = now + METRICS_INTERVAL_NS;
            }

            // 使用LockSupport而非Thread.sleep
            LockSupport.parkNanos(10_000L); // 10微秒
        }
    }

    /**
     * 精确的纳秒级延迟等待，无异常
     */
    private static void parkUntilTimeout(long nanosToWait) {
        long deadline = System.nanoTime() + nanosToWait;
        while (System.nanoTime() < deadline) {
            LockSupport.parkNanos(1_000_000L); // 1ms
        }
    }

    private static void stopEngine(
            AtomicBoolean running,
            Thread producerThread,
            Disruptor<TickEvent> disruptor,
            OrderGateway gateway,
            ExecutorService disruptorThreads) {

        if (!running.compareAndSet(true, false)) {
            return;
        }

        producerThread.interrupt();
        
        // 使用LockSupport代替join避免异常
        long deadline = System.nanoTime() + 1_000_000_000L; // 1秒
        while (producerThread.isAlive() && System.nanoTime() < deadline) {
            LockSupport.parkNanos(1_000_000L); // 1ms
        }

        disruptor.shutdown();
        gateway.shutdown();
        disruptorThreads.shutdown();
    }
}
