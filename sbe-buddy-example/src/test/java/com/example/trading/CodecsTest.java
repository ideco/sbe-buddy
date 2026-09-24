package com.example.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.Codec;

/**
 * Every message through its own codec, the whole contract: the length it
 * announces is what it writes, and it decodes equal. The header's sequence
 * number goes out from a header and as its null value without one, and comes
 * back whole; what the bindings refuse names the field.
 */
final class CodecsTest {

	private static final int OFFSET = 16;

	@Test
	void everyMessageRoundTripsThroughItsCodec() {
		roundTrip(new NewOrderCodec(), Samples.LIMIT_ORDER);
		roundTrip(new NewOrderCodec(), Samples.MARKET_ORDER);
		roundTrip(new ReplaceOrderCodec(), Samples.REPLACE);
		roundTrip(new CancelOrderCodec(), Samples.CANCEL_ORDER);
		roundTrip(new ExecutionReportCodec(), Samples.FILL);
		roundTrip(new ExecutionReportCodec(), Samples.ACKNOWLEDGEMENT);
		roundTrip(new CancelRejectCodec(), Samples.CANCEL_REJECT);
		roundTrip(new RejectCodec(), Samples.REJECT);
	}

	@Test
	void theSequenceNumberTravelsInTheHeader() {
		NewOrderCodec codec = new NewOrderCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);

		codec.encode(Samples.LIMIT_ORDER, new SessionHeader(0, 0, 0, 0, 4711), buffer, OFFSET);
		assertThat(codec.decodeHeader(buffer, OFFSET).sequenceNumber()).isEqualTo(4711);

		codec.encode(Samples.LIMIT_ORDER, buffer, OFFSET);
		assertThat(codec.decodeHeader(buffer, OFFSET).sequenceNumber()).isEqualTo(0xFFFF_FFFFL);
	}

	@Test
	void aRequiredPriceIsRefusedByItsBindingNamingTheField() {
		ExecutionReport report = new ExecutionReport(
				"VENUE-0000000042", Samples.ORDER, "EXEC-00000000007", ExecType.TRADE, OrdStatus.FILLED, "ACME",
				Side.BUY, 0, 700, 700, null, null, Samples.FILL.tradeDate(), Samples.FILL.maturity(), Samples.TIME,
				Samples.fills(new ExecutionReport.Fill("EXEC-00000000005", new BigDecimal("99.61005"), 700))
		);

		assertThatThrownBy(() -> new ExecutionReportCodec().encode(report, new UnsafeBuffer(new byte[256]), OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("fillPx has no wire form with 4 decimals: 99.61005");
	}

	@Test
	void aTimeBefore1970IsRefusedByItsBinding() {
		CancelOrder early = new CancelOrder(
				Samples.REPLACEMENT, Samples.CANCEL, "ACME", Side.BUY, Instant.parse("1969-12-31T23:59:59Z")
		);

		assertThatThrownBy(() -> new CancelOrderCodec().encode(early, new UnsafeBuffer(new byte[128]), OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("transactTime is before 1970: 1969-12-31T23:59:59Z");
	}

	@Test
	void aCharacterIso88591CannotHoldIsRefused() {
		CancelReject reject = new CancelReject(
				"VENUE-0000000042", Samples.CANCEL, Samples.REPLACEMENT, OrdStatus.FILLED, CxlRejReason.OTHER, "€ 5"
		);

		assertThatThrownBy(() -> new CancelRejectCodec().encode(reject, new UnsafeBuffer(new byte[128]), OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("text cannot be written in ISO-8859-1: € 5");
	}

	private static <T> void roundTrip(Codec<T, SessionHeader> codec, T value) {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[512]);

		int written = codec.encode(value, buffer, OFFSET);

		assertThat(written).as("encodedLength").isEqualTo(codec.encodedLength(value));
		assertThat(codec.canDecode(buffer, OFFSET)).as("canDecode").isTrue();
		assertThat(codec.decodedLength(buffer, OFFSET)).as("decodedLength").isEqualTo(written);
		assertThat(codec.decode(buffer, OFFSET)).isEqualTo(value);
		assertThat(codec.lastDecodedLength()).as("lastDecodedLength").isEqualTo(written);
	}
}
