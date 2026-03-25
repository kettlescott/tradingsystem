package core.app;

import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import core.event.TickEvent;
import core.event.TickEventFactory;
import core.execution.ExecutionHandler;
import core.gateway.OrderGateway;
import core.marketdata.JsonMarketDataSource;
import core.marketdata.JsonTickDecoder;
import core.marketdata.MarketDataSource;
import core.marketdata.SimulatedMarketDataSource;
import core.risk.RiskHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
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
    private static final String JSON_LINES_MODE = "json-lines";

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
        MarketDataSource marketDataSource = createMarketDataSource(args);

        Thread producerThread = new Thread(() -> marketDataSource.publishTo(ringBuffer, running), "market-data-producer");
        producerThread.start();

        Thread metricsThread = new Thread(() -> reportMetrics(executionHandler, riskHandler, gateway, running), "metrics-reporter");
        metricsThread.setDaemon(true);
        metricsThread.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            stopEngine(running, producerThread, disruptor, gateway, disruptorThreads, marketDataSource);
        }, "engine-shutdown"));

        sleep(runSeconds * 1_000L);
        stopEngine(running, producerThread, disruptor, gateway, disruptorThreads, marketDataSource);
    }

    private static MarketDataSource createMarketDataSource(String[] args) {
        if (args.length >= 3 && JSON_LINES_MODE.equalsIgnoreCase(args[1])) {
            Path sourcePath = Path.of(args[2]);
            try {
                return new JsonMarketDataSource(
                        Files.newBufferedReader(sourcePath),
                        new JsonTickDecoder(INSTRUMENT_ID)
                );
            } catch (IOException e) {
                throw new IllegalArgumentException("Unable to open JSON market data source: " + sourcePath, e);
            }
        }
        return new SimulatedMarketDataSource(
                PRODUCER_SLEEP_MS,
                INSTRUMENT_ID,
                BID_BASE,
                ASK_BASE,
                PRICE_SPREAD_RANGE
        );
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
            ExecutorService disruptorThreads,
            MarketDataSource marketDataSource
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
        marketDataSource.close();
        disruptorThreads.shutdown();
    }

}
