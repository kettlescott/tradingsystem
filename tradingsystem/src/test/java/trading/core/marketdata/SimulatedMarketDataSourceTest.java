package trading.core.marketdata;

import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import core.marketdata.SimulatedMarketDataSource;
import org.junit.jupiter.api.Test;
import core.event.TickEvent;
import core.event.TickEventFactory;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulatedMarketDataSourceTest {

    @Test
    void generatesRandomBidsAndAsksAroundConfiguredBases() throws Exception {
        Disruptor<TickEvent> disruptor = new Disruptor<>(
                new TickEventFactory(),
                128,
                Executors.defaultThreadFactory(),
                ProducerType.SINGLE,
                new BusySpinWaitStrategy()
        );
        AtomicInteger eventCount = new AtomicInteger(0);
        CountDownLatch received = new CountDownLatch(10);

        try {
            disruptor.handleEventsWith((event, sequence, endOfBatch) -> {
                assertTrue(event.bid >= 60000.0, "Bid should be >= BID_BASE");
                assertTrue(event.ask >= 60010.0, "Ask should be >= ASK_BASE");
                assertTrue(event.ask > event.bid, "Ask should be greater than bid");
                assertEquals(1, event.instrumentId);
                assertFalse(event.riskAccepted, "Risk should not be accepted yet");
                eventCount.incrementAndGet();
                received.countDown();
            });

            AtomicBoolean running = new AtomicBoolean(true);
            SimulatedMarketDataSource source = new SimulatedMarketDataSource(
                    0L,
                    1,
                    60000.0,
                    60010.0,
                    100.0
            );

            Thread producer = new Thread(() -> {
                source.publishTo(disruptor.start(), running);
            });
            producer.start();

            assertTrue(received.await(2, TimeUnit.SECONDS));
            running.set(false);
            producer.join(1000L);

            assertTrue(eventCount.get() >= 10);
        } finally {
            disruptor.shutdown();
        }
    }

    @Test
    void respectsProducerSleepInterval() throws Exception {
        Disruptor<TickEvent> disruptor = new Disruptor<>(
                new TickEventFactory(),
                16,
                Executors.defaultThreadFactory(),
                ProducerType.SINGLE,
                new BusySpinWaitStrategy()
        );
        CountDownLatch received = new CountDownLatch(3);

        try {
            disruptor.handleEventsWith((event, sequence, endOfBatch) -> received.countDown());

            AtomicBoolean running = new AtomicBoolean(true);
            SimulatedMarketDataSource source = new SimulatedMarketDataSource(
                    5L,
                    1,
                    60000.0,
                    60010.0,
                    100.0
            );

            long startTime = System.currentTimeMillis();
            Thread producer = new Thread(() -> {
                source.publishTo(disruptor.start(), running);
            });
            producer.start();

            received.await(3, TimeUnit.SECONDS);
            long elapsedMs = System.currentTimeMillis() - startTime;

            running.set(false);
            producer.join(1000L);

            assertTrue(elapsedMs >= 10L, "Should have slept at least 10ms for 3 events with 5ms sleep");
        } finally {
            disruptor.shutdown();
        }
    }
}

