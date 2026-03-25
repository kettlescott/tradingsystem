package trading.core.zerogc;

import core.event.TickEvent;
import core.zerogc.PreAllocatedTickEventPool;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreAllocatedTickEventPoolTest {

    @Test
    void poolPreAllocatesAllObjects() {
        PreAllocatedTickEventPool pool = new PreAllocatedTickEventPool(64);
        
        // 获取多个对象，验证它们不是空的
        for (int i = 0; i < 100; i++) {
            var event = pool.getNextEventForPopulation();
            assertTrue(event != null);
        }
    }

    @Test
    void poolReusesSameObjectsInCycle() {
        PreAllocatedTickEventPool pool = new PreAllocatedTickEventPool(16);
        
        var event1 = pool.getNextEventForPopulation();
        var event2 = pool.getNextEventForPopulation();
        var event3 = pool.getNextEventForPopulation();
        var event4 = pool.getNextEventForPopulation();
        
        // 获取16个对象后，应该循环回到第一个对象
        for (int i = 4; i < 16; i++) {
            pool.getNextEventForPopulation();
        }
        
        var eventAgain1 = pool.getNextEventForPopulation();
        var eventAgain2 = pool.getNextEventForPopulation();
        var eventAgain3 = pool.getNextEventForPopulation();
        var eventAgain4 = pool.getNextEventForPopulation();
        
        assertEquals(event1, eventAgain1);
        assertEquals(event2, eventAgain2);
        assertEquals(event3, eventAgain3);
        assertEquals(event4, eventAgain4);
    }

    @Test
    void resetEventClearsState() {
        var event = new TickEvent();
        event.instrumentId = 42;
        event.bid = 123.45;
        event.ask = 124.56;
        event.sequence = 999L;
        event.riskAccepted = true;

        PreAllocatedTickEventPool.resetEvent(event);

        assertEquals(0, event.instrumentId);
        assertEquals(0.0, event.bid);
        assertEquals(0.0, event.ask);
        assertEquals(0L, event.sequence);
        assertTrue(!event.riskAccepted);
    }
}

