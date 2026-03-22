package trading.core.gateway;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class OrderGateway {

    private static final int IO_QUEUE_CAPACITY = 4096;

    private final AtomicLong submittedOrders = new AtomicLong();
    private final AtomicLong droppedOrders = new AtomicLong();

    private final ExecutorService ioPool = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(IO_QUEUE_CAPACITY),
            runnable -> {
                Thread thread = new Thread(runnable, "order-gateway-io");
                thread.setDaemon(true);
                return thread;
            },
            (runnable, executor) -> droppedOrders.incrementAndGet()
    );

    public void sendBuy(long sourceSequence, int instrumentId, double price, double qty) {
        send(sourceSequence, instrumentId, Side.BUY, price, qty);
    }

    public void send(long sourceSequence, int instrumentId, Side side, double price, double qty) {
        ioPool.execute(() -> {
            // TODO: replace with real exchange adapter and non-blocking network client.
            submittedOrders.incrementAndGet();
        });
    }

    public long getSubmittedOrders() {
        return submittedOrders.get();
    }

    public long getDroppedOrders() {
        return droppedOrders.get();
    }

    public void shutdown() {
        ioPool.shutdown();
    }

    public enum Side {
        BUY,
        SELL
    }
}