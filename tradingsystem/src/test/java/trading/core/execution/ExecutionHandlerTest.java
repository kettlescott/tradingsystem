package trading.core.execution;

import org.junit.jupiter.api.Test;
import trading.core.event.TickEvent;
import trading.core.gateway.OrderGateway;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import trading.core.execution.ExecutionHandler;

class ExecutionHandlerTest {

    @Test
    void sendsBuyWhenRiskAcceptedAndBidBelowThreshold() {
        RecordingGateway gateway = new RecordingGateway();
        ExecutionHandler handler = new ExecutionHandler(gateway);
        TickEvent tick = new TickEvent();
        tick.riskAccepted = true;
        tick.bid = 60049.0;
        tick.ask = 60051.5;
        tick.instrumentId = 7;
        tick.sequence = 42L;
        tick.ingestTsNanos = System.nanoTime();

        handler.onEvent(tick, 42L, true);

        assertEquals(1, gateway.sendBuyCalls.get());
        assertEquals(42L, gateway.lastSequence);
        assertEquals(7, gateway.lastInstrumentId);
        assertEquals(60051.5, gateway.lastPrice, 0.0);
        assertEquals(0.001, gateway.lastQty, 0.0);
    }

    @Test
    void doesNotSendWhenRiskRejectedOrBidAtThreshold() {
        RecordingGateway gateway = new RecordingGateway();
        ExecutionHandler handler = new ExecutionHandler(gateway);

        TickEvent rejected = new TickEvent();
        rejected.riskAccepted = false;
        rejected.bid = 60000.0;
        rejected.ask = 60010.0;
        rejected.ingestTsNanos = System.nanoTime();
        handler.onEvent(rejected, 1L, false);

        TickEvent atThreshold = new TickEvent();
        atThreshold.riskAccepted = true;
        atThreshold.bid = 60050.0;
        atThreshold.ask = 60055.0;
        atThreshold.ingestTsNanos = System.nanoTime();
        handler.onEvent(atThreshold, 2L, true);

        assertEquals(0, gateway.sendBuyCalls.get());
    }

    @Test
    void recordsLatencyPercentiles() {
        RecordingGateway gateway = new RecordingGateway();
        ExecutionHandler handler = new ExecutionHandler(gateway);

        TickEvent tick = new TickEvent();
        tick.riskAccepted = true;
        tick.bid = 60000.0;
        tick.ask = 60001.0;
        tick.ingestTsNanos = System.nanoTime() - 5_000;
        handler.onEvent(tick, 1L, true);

        long[] snapshot = handler.snapshotAndResetLatencyMicros();

        assertEquals(2, snapshot.length);
        assertTrue(snapshot[0] >= 1L);
        assertTrue(snapshot[1] >= 1L);
    }

    private static final class RecordingGateway extends OrderGateway {
        private final AtomicInteger sendBuyCalls = new AtomicInteger();
        private long lastSequence;
        private int lastInstrumentId;
        private double lastPrice;
        private double lastQty;

        @Override
        public void sendBuy(long sourceSequence, int instrumentId, double price, double qty) {
            sendBuyCalls.incrementAndGet();
            lastSequence = sourceSequence;
            lastInstrumentId = instrumentId;
            lastPrice = price;
            lastQty = qty;
        }
    }
}

