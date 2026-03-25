package core.marketdata;

import com.lmax.disruptor.RingBuffer;
import core.event.TickEvent;

import java.util.concurrent.atomic.AtomicBoolean;

public interface MarketDataSource extends AutoCloseable {

    void publishTo(RingBuffer<TickEvent> ringBuffer, AtomicBoolean running);

    @Override
    default void close() {
        // default no-op
    }
}

