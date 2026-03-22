package trading.core.app;

import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import trading.core.event.TickEvent;
import trading.core.event.TickEventFactory;
import trading.core.execution.ExecutionHandler;
import trading.core.gateway.OrderGateway;
import trading.core.risk.RiskHandler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class TradingEngine {

    private static final int RING_BUFFER_SIZE = 1024 * 64;
    private static final long PRODUCER_SLEEP_MS = 1L;
    private static final int INSTRUMENT_ID = 1;
    private static final double BID_BASE = 60000;
    private static final double ASK_BASE = 60010;
    private static final double PRICE_SPREAD_RANGE = 100;
    private static final long METRICS_INTERVAL_MS = 1_000L;

    public static void main(String[] args) {
        long runSeconds = args.length > 0 ? Long.parseLong(args[0]) : 15L;

        ExecutorService disruptorThreads = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "disruptor-consumer");
            t.setDaemon(true);
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

        Thread producerThread = new Thread(() -> produce(ringBuffer, running), "market-data-producer");
        producerThread.start();

        Thread metricsThread = new Thread(() -> reportMetrics(executionHandler, riskHandler, gateway, running), "metrics-reporter");
        metricsThread.setDaemon(true);
        metricsThread.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stopEngine(running, producerThread, disruptor, gateway, disruptorThreads);
        }, "engine-shutdown"));

        sleep(runSeconds * 1_000L);
        stopEngine(running, producerThread, disruptor, gateway, disruptorThreads);
    }

    private static void produce(RingBuffer<TickEvent> ringBuffer, AtomicBoolean running) {

        while (running.get()) {
            long seq = ringBuffer.next();
            try {
                TickEvent event = ringBuffer.get(seq);

                event.eventType = TickEvent.EventType.MARKET_TICK;
                event.instrumentId = INSTRUMENT_ID;
                event.bid = BID_BASE + ThreadLocalRandom.current().nextDouble(PRICE_SPREAD_RANGE);
                event.ask = ASK_BASE + ThreadLocalRandom.current().nextDouble(PRICE_SPREAD_RANGE);
                event.exchangeTsNanos = System.nanoTime();
                event.ingestTsNanos = System.nanoTime();
                event.sequence = seq;
                event.riskAccepted = false;

            } finally {
                ringBuffer.publish(seq);
            }

            sleep(PRODUCER_SLEEP_MS);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static void reportMetrics(
            ExecutionHandler executionHandler,
            RiskHandler riskHandler,
            OrderGateway gateway,
            AtomicBoolean running
    ) {
        while (running.get()) {
            sleep(METRICS_INTERVAL_MS);
            long[] p = executionHandler.snapshotAndResetLatencyMicros();
            System.out.println(
                    "[METRICS] p99(us)=" + p[0]
                            + " p999(us)=" + p[1]
                            + " accepted=" + riskHandler.getAccepted()
                            + " rejected=" + riskHandler.getRejected()
                            + " sent=" + gateway.getSubmittedOrders()
                            + " dropped=" + gateway.getDroppedOrders()
            );
        }
    }

    private static void stopEngine(
            AtomicBoolean running,
            Thread producerThread,
            Disruptor<TickEvent> disruptor,
            OrderGateway gateway,
            ExecutorService disruptorThreads
    ) {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        producerThread.interrupt();
        try {
            producerThread.join(1_000L);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        disruptor.shutdown();
        gateway.shutdown();
        disruptorThreads.shutdown();
    }

}
