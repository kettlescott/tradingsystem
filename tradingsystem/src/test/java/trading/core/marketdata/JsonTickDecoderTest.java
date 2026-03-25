package trading.core.marketdata;

import core.marketdata.JsonTickDecoder;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTickDecoderTest {

    @Test
    void decodesBidAskAndDefaultsInstrumentId() throws IOException {
        JsonTickDecoder decoder = new JsonTickDecoder(7);
        JsonTickDecoder.DecodedTick decodedTick = new JsonTickDecoder.DecodedTick();

        decoder.decode("{\"bid\":100.5,\"ask\":101.25,\"exchangeTsNanos\":123456}", decodedTick);

        assertEquals(7, decodedTick.getInstrumentId());
        assertEquals(100.5, decodedTick.getBid(), 0.0);
        assertEquals(101.25, decodedTick.getAsk(), 0.0);
        assertEquals(123456L, decodedTick.getExchangeTsNanos());
    }

    @Test
    void allowsInstrumentIdOverrideFromMessage() throws IOException {
        JsonTickDecoder decoder = new JsonTickDecoder(7);
        JsonTickDecoder.DecodedTick decodedTick = new JsonTickDecoder.DecodedTick();

        decoder.decode("{\"instrumentId\":11,\"bid\":200.0,\"ask\":200.5}", decodedTick);

        assertEquals(11, decodedTick.getInstrumentId());
        assertEquals(200.0, decodedTick.getBid(), 0.0);
        assertEquals(200.5, decodedTick.getAsk(), 0.0);
        assertTrue(decodedTick.getExchangeTsNanos() > 0L);
    }

    @Test
    void rejectsMessagesMissingBidOrAsk() {
        JsonTickDecoder decoder = new JsonTickDecoder(1);
        JsonTickDecoder.DecodedTick decodedTick = new JsonTickDecoder.DecodedTick();

        assertThrows(IllegalArgumentException.class,
                () -> decoder.decode("{\"bid\":100.0}", decodedTick));
        assertThrows(IllegalArgumentException.class,
                () -> decoder.decode("{\"ask\":101.0}", decodedTick));
    }
}

