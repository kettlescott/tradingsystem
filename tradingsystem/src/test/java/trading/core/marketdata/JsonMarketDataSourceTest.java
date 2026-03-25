package trading.core.marketdata;

import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import core.marketdata.JsonMarketDataSource;
import core.marketdata.JsonTickDecoder;
import org.junit.jupiter.api.Test;
import core.event.TickEvent;
import core.event.TickEventFactory;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReferenceArray;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonMarketDataSourceTest {

	@Test
	void publishesNormalizedTicksIntoRingBuffer() throws Exception {
		Disruptor<TickEvent> disruptor = new Disruptor<>(
				new TickEventFactory(),
				16,
				Executors.defaultThreadFactory(),
				ProducerType.SINGLE,
				new BusySpinWaitStrategy()
		);
		AtomicReferenceArray<CapturedTick> captured = new AtomicReferenceArray<>(2);
		CountDownLatch received = new CountDownLatch(2);

		try {
			disruptor.handleEventsWith((event, sequence, endOfBatch) -> {
				if (sequence < 2) {
					captured.set((int) sequence, new CapturedTick(
							event.sequence,
							event.instrumentId,
							event.bid,
							event.ask,
							event.exchangeTsNanos,
							event.ingestTsNanos,
							event.riskAccepted
					));
					received.countDown();
				}
			});

			JsonMarketDataSource source = new JsonMarketDataSource(
					new BufferedReader(new StringReader("\n{\"bid\":100.0,\"ask\":101.0,\"exchangeTsNanos\":5}\n{\"instrumentId\":9,\"bid\":200.0,\"ask\":201.0,\"exchangeTsNanos\":6}\n")),
					new JsonTickDecoder(4)
			);

			source.publishTo(disruptor.start(), new AtomicBoolean(true));

			assertTrue(received.await(1, TimeUnit.SECONDS));

			CapturedTick first = captured.get(0);
			CapturedTick second = captured.get(1);

			assertEquals(0L, first.sequence());
			assertEquals(4, first.instrumentId());
			assertEquals(100.0, first.bid(), 0.0);
			assertEquals(101.0, first.ask(), 0.0);
			assertEquals(5L, first.exchangeTsNanos());
			assertTrue(first.ingestTsNanos() > 0L);
			assertFalse(first.riskAccepted());

			assertEquals(1L, second.sequence());
			assertEquals(9, second.instrumentId());
			assertEquals(200.0, second.bid(), 0.0);
			assertEquals(201.0, second.ask(), 0.0);
			assertEquals(6L, second.exchangeTsNanos());
			assertTrue(second.ingestTsNanos() > 0L);
			assertFalse(second.riskAccepted());
		} finally {
			disruptor.shutdown();
		}
	}

	private record CapturedTick(
			long sequence,
			int instrumentId,
			double bid,
			double ask,
			long exchangeTsNanos,
			long ingestTsNanos,
			boolean riskAccepted
	) {
	}
}

