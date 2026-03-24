package trading.core.risk;

import org.junit.jupiter.api.Test;
import trading.core.event.TickEvent;
import trading.core.risk.RiskHandlerTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskHandlerTest {

    @Test
    void acceptsValidTightSpreadTick() {
        trading.core.risk.RiskHandler handler = new RiskHandler();
        TickEvent tick = new TickEvent();
        tick.bid = 100.0;
        tick.ask = 101.0;

        handler.onEvent(tick, 1L, true);

        assertTrue(tick.riskAccepted);
        assertEquals(1L, handler.getAccepted());
        assertEquals(0L, handler.getRejected());
    }

    @Test
    void rejectsWideSpreadTick() {
        RiskHandler handler = new RiskHandler();
        TickEvent tick = new TickEvent();
        tick.bid = 100.0;
        tick.ask = 140.0;

        handler.onEvent(tick, 1L, true);

        assertFalse(tick.riskAccepted);
        assertEquals(0L, handler.getAccepted());
        assertEquals(1L, handler.getRejected());
    }

    @Test
    void acceptsSpreadAtBoundary() {
        RiskHandler handler = new RiskHandler();
        TickEvent tick = new TickEvent();
        tick.bid = 100.0;
        tick.ask = 125.0;

        handler.onEvent(tick, 1L, true);

        assertTrue(tick.riskAccepted);
        assertEquals(1L, handler.getAccepted());
    }

    @Test
    void rejectsInvalidPrices() {
        RiskHandler handler = new RiskHandler();
        TickEvent tick = new TickEvent();
        tick.bid = 100.0;
        tick.ask = 99.0;

        handler.onEvent(tick, 1L, true);

        assertFalse(tick.riskAccepted);
        assertEquals(1L, handler.getRejected());
    }
}

