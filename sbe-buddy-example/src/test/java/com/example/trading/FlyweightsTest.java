package com.example.trading;

import static org.assertj.core.api.Assertions.assertThat;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import com.example.trading.sbe.MessageHeaderDecoder;
import com.example.trading.sbe.MessageHeaderEncoder;
import com.example.trading.sbe.NewOrderDecoder;
import com.example.trading.sbe.NewOrderEncoder;

/**
 * The product seen working once: the flyweights the processor generated into
 * the {@code sbe} package encode an order and read it back. What they encode is
 * sbe-tool's business; that they exist, compile and run is ours.
 */
final class FlyweightsTest {

	@Test
	void anOrderRoundTripsThroughTheGeneratedFlyweights() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
		NewOrderEncoder encoder = new NewOrderEncoder();
		encoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
				.orderId(42)
				.symbol("ACME")
				.quantity(7);
		// The enum's simple name is also this package's record, so it is reached
		// through the decoder rather than imported.
		encoder.side(com.example.trading.sbe.Side.BUY);
		encoder.price().mantissa(12345).exponent((byte) -2);
		encoder.legsCount(1).next().instrumentId(9).ratio(1);
		encoder.note("hi");

		NewOrderDecoder decoder = new NewOrderDecoder();
		decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());

		assertThat(decoder.orderId()).isEqualTo(42);
		assertThat(decoder.symbol()).isEqualTo("ACME");
		assertThat(decoder.side().name()).isEqualTo("BUY");
		assertThat(decoder.price().mantissa()).isEqualTo(12345);
		assertThat(decoder.price().exponent()).isEqualTo((byte) -2);
		assertThat(decoder.quantity()).isEqualTo(7);
		NewOrderDecoder.LegsDecoder legs = decoder.legs();
		assertThat(legs.count()).isEqualTo(1);
		assertThat(legs.next().instrumentId()).isEqualTo(9);
		assertThat(decoder.note()).isEqualTo("hi");
		assertThat(encoder.encodedLength()).isEqualTo(decoder.encodedLength());
	}
}
