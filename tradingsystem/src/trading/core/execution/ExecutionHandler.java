package trading.core.execution;

import com.lmax.disruptor.EventHandler;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;
import trading.core.event.TickEvent;
import trading.core.gateway.OrderGateway;

public class ExecutionHandler implements EventHandler<TickEvent> {

    private static final double BUY_THRESHOLD = 60050.0;
    private static final double ORDER_QTY = 0.001;

    private final OrderGateway gateway;
    private final Recorder latencyMicros = new Recorder(1, 10_000_000, 2);

    public ExecutionHandler(OrderGateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public void onEvent(TickEvent event, long sequence, boolean endOfBatch) {
        latencyMicros.recordValue(Math.max(1L, (System.nanoTime() - event.ingestTsNanos) / 1_000));

        if (event.riskAccepted && event.bid < BUY_THRESHOLD) {
            gateway.sendBuy(event.sequence, event.instrumentId, event.ask, ORDER_QTY);
        }
    }

    public long[] snapshotAndResetLatencyMicros() {
        Histogram snapshot = latencyMicros.getIntervalHistogram();
        return new long[] {
                snapshot.getValueAtPercentile(99.0),
                snapshot.getValueAtPercentile(99.9)
        };
    }
}
