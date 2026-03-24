package trading.core.zerogc;

import com.lmax.disruptor.RingBuffer;
import trading.core.event.TickEvent;

/**
 * Zero-GC object pool for pre-allocated TickEvent objects.
 * 避免任何GC暂停（GC pause），所有对象都在初始化时预分配。
 */
public class PreAllocatedTickEventPool {

    private final TickEvent[] pool;
    private final int mask;
    private int nextIndex = 0;

    public PreAllocatedTickEventPool(int capacity) {
        // 2的幂次方，用于位运算代替模运算
        int powerOfTwo = Integer.highestOneBit(capacity - 1) << 1;
        this.pool = new TickEvent[powerOfTwo];
        this.mask = powerOfTwo - 1;
        
        // 预分配所有对象
        for (int i = 0; i < powerOfTwo; i++) {
            this.pool[i] = new TickEvent();
        }
    }

    /**
     * 获取下一个预分配的TickEvent对象
     * 注意：调用者必须在使用后归还对象到ring buffer
     */
    public TickEvent getNextEventForPopulation() {
        TickEvent event = pool[nextIndex & mask];
        nextIndex++;
        return event;
    }

    /**
     * 重置对象为干净状态（可选）
     */
    public static void resetEvent(TickEvent event) {
        event.eventType = null;
        event.instrumentId = 0;
        event.bid = 0.0;
        event.ask = 0.0;
        event.exchangeTsNanos = 0L;
        event.ingestTsNanos = 0L;
        event.sequence = 0L;
        event.riskAccepted = false;
    }
}

