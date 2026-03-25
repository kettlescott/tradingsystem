package trading.core.app;

import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import org.junit.jupiter.api.Test;
import core.event.TickEvent;
import core.event.TickEventFactory;
import core.execution.ExecutionHandler;
import core.gateway.OrderGateway;
import core.risk.RiskHandler;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradingPipelineTest {

    @Test
    void riskGateControlsExecutionInDisruptorPipeline() throws Exception {
        CountingGateway gateway = new CountingGateway();
        RiskHandler riskHandler = new RiskHandler();
        ExecutionHandler executionHandler = new ExecutionHandler(gateway);

        Disruptor<TickEvent> disruptor = new Disruptor<>(
                new TickEventFactory(),
                1024,
                Executors.defaultThreadFactory(),
                ProducerType.SINGLE,
                new BusySpinWaitStrategy()
        );

        try {
            disruptor.handleEventsWith(riskHandler).then(executionHandler);
            RingBuffer<TickEvent> ringBuffer = disruptor.start();

            publishTick(ringBuffer, 1L, 101.0);
            publishTick(ringBuffer, 2L, 140.0);

            assertTrue(gateway.orderLatch.await(1, TimeUnit.SECONDS));
            assertEquals(1, gateway.sent.get());
            assertEquals(1L, riskHandler.getAccepted());
            assertEquals(1L, riskHandler.getRejected());
        } finally {
            disruptor.shutdown();
            gateway.shutdown();
        }
    }

    private static void publishTick(
            RingBuffer<TickEvent> ringBuffer,
            long sequence,
            double ask
    ) {
        long slot = ringBuffer.next();
        try {
            TickEvent event = ringBuffer.get(slot);
            event.sequence = sequence;
            event.instrumentId = 1;
            event.bid = 100.0;
            event.ask = ask;
            event.ingestTsNanos = System.nanoTime();
            event.riskAccepted = true;
        } finally {
            ringBuffer.publish(slot);
        }
    }

    private static final class CountingGateway extends OrderGateway {
        private final AtomicInteger sent = new AtomicInteger();
        private final CountDownLatch orderLatch = new CountDownLatch(1);

        @Override
        public void sendBuy(long sourceSequence, int instrumentId, double price, double qty) {
            sent.incrementAndGet();
            orderLatch.countDown();
        }
    }
}



