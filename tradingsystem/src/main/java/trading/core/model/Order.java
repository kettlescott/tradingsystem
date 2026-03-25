package core.model;

public final class Order {
    public long sourceSequence;
    public int instrumentId;
    public Side side;
    public double price;
    public double qty;

    public enum Side {
        BUY, SELL
    }
}