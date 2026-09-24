package com.example.trading;

import static org.assertj.core.api.Assertions.assertThat;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import com.example.trading.sbe.CancelOrderDecoder;
import com.example.trading.sbe.CancelOrderEncoder;
import com.example.trading.sbe.SessionHeaderDecoder;
import com.example.trading.sbe.SessionHeaderEncoder;

/**
 * The product seen working once: the flyweights the processor generated into
 * the {@code sbe} package encode a cancel and read it back. What they encode is
 * sbe-tool's business; that they exist, compile and run is ours.
 */
final class FlyweightsTest {

	@Test
	void aCancelRoundTripsThroughTheGeneratedFlyweights() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
		new CancelOrderEncoder().wrapAndApplyHeader(buffer, 0, new SessionHeaderEncoder())
				.origClOrdId(Samples.REPLACEMENT)
				.clOrdId(Samples.CANCEL)
				.symbol("ACME")
				.side(com.example.trading.sbe.Side.BUY) // the flyweight's enum, beside this package's own
				.transactTime(1_000L);

		CancelOrderDecoder decoder = new CancelOrderDecoder().wrapAndApplyHeader(buffer, 0, new SessionHeaderDecoder());

		assertThat(decoder.clOrdId()).isEqualTo(Samples.CANCEL);
		assertThat(decoder.symbol()).isEqualTo("ACME");
		assertThat(decoder.side()).isEqualTo(com.example.trading.sbe.Side.BUY);
		assertThat(decoder.transactTime()).isEqualTo(1_000L);
	}
}
