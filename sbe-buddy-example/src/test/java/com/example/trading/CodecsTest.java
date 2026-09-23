package com.example.trading;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import com.example.trading.sbe.MessageHeaderDecoder;
import com.example.trading.sbe.NewOrderDecoder;

/**
 * The codecs over the flyweights: an order with its legs and its note goes onto
 * the wire through its codec, reads back through the flyweights as written, and
 * decodes equal, every length agreeing; a cancel, a block alone, the same.
 */
final class CodecsTest {

	private static final int OFFSET = 16;

	@Test
	void anOrderRoundTripsAndTheFlyweightsReadWhatItsCodecWrites() {
		NewOrder order = new NewOrder(
				42, "ACME", Side.BUY, new Price(12_345, (byte) -2), 4_000_000_000L,
				List.of(new NewOrder.Leg(9, 1), new NewOrder.Leg(10, -1)), "Good for the day — then cancel"
		);
		NewOrderCodec codec = new NewOrderCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);

		int written = codec.encode(order, buffer, OFFSET);

		assertThat(written).isEqualTo(codec.encodedLength(order));
		NewOrderDecoder decoder = new NewOrderDecoder().wrapAndApplyHeader(buffer, OFFSET, new MessageHeaderDecoder());
		assertThat(decoder.symbol()).isEqualTo("ACME");
		NewOrderDecoder.LegsDecoder legs = decoder.legs();
		assertThat(legs.count()).isEqualTo(2);
		assertThat(legs.next().instrumentId()).isEqualTo(9);
		assertThat(legs.next().ratio()).isEqualTo(-1);
		assertThat(decoder.note()).isEqualTo("Good for the day — then cancel");
		assertThat(MessageHeaderDecoder.ENCODED_LENGTH + decoder.encodedLength()).isEqualTo(written);
		assertThat(codec.decodedLength(buffer, OFFSET)).isEqualTo(written);
		assertThat(codec.decode(buffer, OFFSET)).isEqualTo(order);
		assertThat(codec.lastDecodedLength()).isEqualTo(written);
	}

	@Test
	void aCancelRoundTrips() {
		CancelOrder cancel = new CancelOrder(42, "ACME");
		CancelOrderCodec codec = new CancelOrderCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);

		int written = codec.encode(cancel, buffer, OFFSET);

		assertThat(written).isEqualTo(codec.encodedLength(cancel));
		assertThat(codec.decode(buffer, OFFSET)).isEqualTo(cancel);
		assertThat(codec.lastDecodedLength()).isEqualTo(written);
	}
}
