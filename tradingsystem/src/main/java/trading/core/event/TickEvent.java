package core.event;

public class TickEvent {
    public EventType eventType;
    public int instrumentId;
    public double bid;
    public double ask;
    public long exchangeTsNanos;
    public long ingestTsNanos;
    public long sequence;
    public boolean riskAccepted;

    public enum EventType {
        MARKET_TICK
    }
}

