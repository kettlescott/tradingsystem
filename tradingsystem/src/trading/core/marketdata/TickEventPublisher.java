package trading.core.marketdata;

import trading.core.event.TickEvent;

final class TickEventPublisher {

    private TickEventPublisher() {
    }

    static void populateMarketTick(
            TickEvent event,
            long sequence,
            int instrumentId,
            double bid,
            double ask,
            long exchangeTsNanos,
            long ingestTsNanos
    ) {
        event.eventType = TickEvent.EventType.MARKET_TICK;
        event.instrumentId = instrumentId;
        event.bid = bid;
        event.ask = ask;
        event.exchangeTsNanos = exchangeTsNanos;
        event.ingestTsNanos = ingestTsNanos;
        event.sequence = sequence;
        event.riskAccepted = false;
    }
}

