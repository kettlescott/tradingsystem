package trading.core.marketdata;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;

public class JsonTickDecoder {

    private final JsonFactory jsonFactory = new JsonFactory();
    private final int defaultInstrumentId;

    public JsonTickDecoder(int defaultInstrumentId) {
        this.defaultInstrumentId = defaultInstrumentId;
    }

    public void decode(String message, DecodedTick target) throws IOException {
        int instrumentId = defaultInstrumentId;
        double bid = Double.NaN;
        double ask = Double.NaN;
        long exchangeTsNanos = System.nanoTime();

        try (JsonParser parser = jsonFactory.createParser(message)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IllegalArgumentException("Tick message must be a JSON object");
            }

            while (parser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = parser.currentName();
                parser.nextToken();
                if (fieldName == null) {
                    parser.skipChildren();
                    continue;
                }
                switch (fieldName) {
                    case "instrumentId" -> instrumentId = parser.getIntValue();
                    case "bid" -> bid = parser.getDoubleValue();
                    case "ask" -> ask = parser.getDoubleValue();
                    case "exchangeTsNanos" -> exchangeTsNanos = parser.getLongValue();
                    default -> parser.skipChildren();
                }
            }
        }

        if (!Double.isFinite(bid) || !Double.isFinite(ask)) {
            throw new IllegalArgumentException("Tick message must contain finite bid and ask values");
        }

        target.instrumentId = instrumentId;
        target.bid = bid;
        target.ask = ask;
        target.exchangeTsNanos = exchangeTsNanos;
    }

    public static final class DecodedTick {
        private int instrumentId;
        private double bid;
        private double ask;
        private long exchangeTsNanos;

        public int getInstrumentId() {
            return instrumentId;
        }

        public double getBid() {
            return bid;
        }

        public double getAsk() {
            return ask;
        }

        public long getExchangeTsNanos() {
            return exchangeTsNanos;
        }
    }
}

