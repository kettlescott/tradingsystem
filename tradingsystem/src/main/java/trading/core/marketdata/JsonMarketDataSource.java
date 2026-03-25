package core.marketdata;

import com.lmax.disruptor.RingBuffer;
import core.event.TickEvent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.concurrent.atomic.AtomicBoolean;

public class JsonMarketDataSource implements MarketDataSource {

    private final BufferedReader reader;
    private final JsonTickDecoder decoder;
    private final JsonTickDecoder.DecodedTick decodedTick = new JsonTickDecoder.DecodedTick();

    public JsonMarketDataSource(BufferedReader reader, JsonTickDecoder decoder) {
        this.reader = reader;
        this.decoder = decoder;
    }

    @Override
    public void publishTo(RingBuffer<TickEvent> ringBuffer, AtomicBoolean running) {
        try {
            String message;
            while (running.get() && (message = reader.readLine()) != null) {
                if (message.isBlank()) {
                    continue;
                }

                decoder.decode(message, decodedTick);

                long sequence = ringBuffer.next();
                try {
                    TickEvent event = ringBuffer.get(sequence);
                    TickEventPublisher.populateMarketTick(
                            event,
                            sequence,
                            decodedTick.getInstrumentId(),
                            decodedTick.getBid(),
                            decodedTick.getAsk(),
                            decodedTick.getExchangeTsNanos(),
                            System.nanoTime()
                    );
                } finally {
                    ringBuffer.publish(sequence);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read JSON market data", e);
        }
    }

    @Override
    public void close() {
        try {
            reader.close();
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to close JSON market data source", e);
        }
    }
}

