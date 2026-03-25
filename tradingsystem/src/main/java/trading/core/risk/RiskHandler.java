package core.risk;

import com.lmax.disruptor.EventHandler;
import core.event.TickEvent;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Synchronous pre-trade checks on the same Disruptor sequence.
 */
public class RiskHandler implements EventHandler<TickEvent> {

    private static final double MAX_ALLOWED_SPREAD = 25.0;

    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong rejected = new AtomicLong();

    @Override
    public void onEvent(TickEvent event, long sequence, boolean endOfBatch) {
        boolean priceValid = event.bid > 0.0 && event.ask > event.bid;
        boolean spreadOk = (event.ask - event.bid) <= MAX_ALLOWED_SPREAD;

        event.riskAccepted = priceValid && spreadOk;
        if (event.riskAccepted) {
            accepted.incrementAndGet();
        } else {
            rejected.incrementAndGet();
        }
    }

    public long getAccepted() {
        return accepted.get();
    }

    public long getRejected() {
        return rejected.get();
    }
}

