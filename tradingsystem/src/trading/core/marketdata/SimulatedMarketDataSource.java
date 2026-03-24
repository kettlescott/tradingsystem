package trading.core.marketdata;

import com.lmax.disruptor.RingBuffer;
import trading.core.event.TickEvent;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

public class SimulatedMarketDataSource implements MarketDataSource {

    private final long producerSleepMs;
    private final int instrumentId;
    private final double bidBase;
    private final double askBase;
    private final double priceSpreadRange;

    public SimulatedMarketDataSource(
            long producerSleepMs,
            int instrumentId,
            double bidBase,
            double askBase,
            double priceSpreadRange
    ) {
        this.producerSleepMs = producerSleepMs;
        this.instrumentId = instrumentId;
        this.bidBase = bidBase;
        this.askBase = askBase;
        this.priceSpreadRange = priceSpreadRange;
    }

    @Override
    public void publishTo(RingBuffer<TickEvent> ringBuffer, AtomicBoolean running) {
        while (running.get()) {
            long sequence = ringBuffer.next();
            try {
                long now = System.nanoTime();
                double randomOffset = ThreadLocalRandom.current().nextDouble(priceSpreadRange);
                TickEvent event = ringBuffer.get(sequence);
                TickEventPublisher.populateMarketTick(
                        event,
                        sequence,
                        instrumentId,
                        bidBase + randomOffset,
                        askBase + randomOffset,
                        now,
                        now
                );
            } finally {
                ringBuffer.publish(sequence);
            }

            pauseBetweenPublishes();
        }
    }

    private void pauseBetweenPublishes() {
        if (producerSleepMs <= 0L) {
            return;
        }
        LockSupport.parkNanos(producerSleepMs * 1_000_000L);
    }
}

