package com.example.quotes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Set;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import com.example.quotes.xmlref.MessageHeaderDecoder;
import com.example.quotes.xmlref.MessageHeaderEncoder;
import com.example.quotes.xmlref.QuoteDecoder;
import com.example.quotes.xmlref.QuoteEncoder;

/**
 * The bytes are sbe-tool's: what the codec writes, the flyweights sbe-tool
 * generated from the oracle read, and the reverse; and across versions, a
 * message from an older writer decodes with its later fields absent, an older
 * reader reads a current message whole, and a version 0 message is below the
 * baseline and refused. The retired trade count is written as its null value
 * and never read; the constant price exponent is on no wire and in every
 * decoded record; the prices are mantissas on the wire and decimals in the
 * record, through the binding both ways. The frozen versions' flyweights, and
 * the reference enums whose simple names are the example's own, are qualified.
 */
final class ReferenceFlyweightsTest {

	private static final int OFFSET = 16;

	private static final BigDecimal BID = new BigDecimal("1.0050");

	private static final BigDecimal ASK = new BigDecimal("1.0075");

	private static final byte EXPONENT = -4;

	private static final long[] DEPTH = {4_000_000_000L, 900, 800, 700, 600};

	private static final Trade TRADE = new Trade(10_060, 300);

	private static final Quote QUOTE = new Quote(
			42, BID, ASK, 4_000_000_000L, 250, 7, 10_060.5, Venue.XNAS, MarketState.OPEN,
			EnumSet.of(QuoteFlag.INDICATIVE, QuoteFlag.LOCKED), "ACME", EXPONENT, DEPTH, TRADE
	);

	@Test
	void theReferenceDecoderReadsWhatTheCodecWrites() {
		QuoteCodec codec = new QuoteCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
		int written = codec.encode(QUOTE, buffer, OFFSET);

		QuoteDecoder decoder = new QuoteDecoder().wrapAndApplyHeader(buffer, OFFSET, new MessageHeaderDecoder());

		assertThat(decoder.instrumentId()).isEqualTo(42);
		assertThat(decoder.bid()).isEqualTo(10_050);
		assertThat(decoder.ask()).isEqualTo(10_075);
		assertThat(decoder.bidSize()).isEqualTo(4_000_000_000L);
		assertThat(decoder.askSize()).isEqualTo(250);
		assertThat(decoder.sequence()).isEqualTo(7);
		assertThat(decoder.tradeCount()).isEqualTo(QuoteDecoder.tradeCountNullValue());
		assertThat(decoder.vwap()).isEqualTo(10_060.5);
		assertThat(decoder.venue()).isEqualTo(com.example.quotes.xmlref.Venue.XNAS);
		assertThat(decoder.state()).isEqualTo(com.example.quotes.xmlref.MarketState.OPEN);
		assertThat(decoder.flags().indicative()).isTrue();
		assertThat(decoder.flags().crossed()).isFalse();
		assertThat(decoder.flags().locked()).isTrue();
		assertThat(decoder.symbol()).isEqualTo("ACME");
		byte[] symbol = new byte[QuoteDecoder.symbolLength()];
		decoder.getSymbol(symbol, 0);
		assertThat(symbol).containsExactly('A', 'C', 'M', 'E', 0, 0, 0, 0);
		assertThat(decoder.priceExponent()).isEqualTo(EXPONENT);
		assertThat(QuoteDecoder.priceExponentEncodingLength()).isZero();
		for (int level = 0; level < QuoteDecoder.bidDepthLength(); level++) {
			assertThat(decoder.bidDepth(level)).isEqualTo(DEPTH[level]);
		}
		assertThat(decoder.lastTrade().price()).isEqualTo(10_060);
		assertThat(decoder.lastTrade().size()).isEqualTo(300);
		assertThat(MessageHeaderDecoder.ENCODED_LENGTH + decoder.encodedLength()).isEqualTo(written);
	}

	@Test
	void anAbsentVwapIsTheNullValueOnTheWire() {
		QuoteCodec codec = new QuoteCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
		codec.encode(
				new Quote(
						42, BID, ASK, 4_000_000_000L, 250, 7, null, Venue.XNAS, MarketState.OPEN, Set.of(),
						"ACME",
						EXPONENT, DEPTH, TRADE
				),
				buffer, OFFSET
		);

		QuoteDecoder decoder = new QuoteDecoder().wrapAndApplyHeader(buffer, OFFSET, new MessageHeaderDecoder());

		assertThat(decoder.vwap()).isNaN();
		assertThat(QuoteDecoder.vwapNullValue()).isNaN();
		assertThat(decoder.flags().isEmpty()).isTrue();
	}

	@Test
	void theCodecReadsWhatTheReferenceEncoderWritesAndSkipsTheTradeCount() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
		QuoteEncoder encoder = new QuoteEncoder().wrapAndApplyHeader(buffer, OFFSET, new MessageHeaderEncoder());
		encoder.instrumentId(42).bid(10_050).ask(10_075).bidSize(4_000_000_000L).askSize(250).sequence(7)
				.tradeCount(3).vwap(10_060.5).venue(com.example.quotes.xmlref.Venue.XNYS)
				.state(com.example.quotes.xmlref.MarketState.HALTED);
		encoder.flags().crossed(true);
		encoder.symbol("ACME").bidDepth(0, 4_000_000_000L).bidDepth(1, 900).bidDepth(2, 800).bidDepth(3, 700)
				.bidDepth(4, 600);
		encoder.lastTrade().price(10_060).size(300);
		QuoteCodec codec = new QuoteCodec();

		Quote decoded = codec.decode(buffer, OFFSET);

		assertThat(decoded).usingRecursiveComparison().isEqualTo(
				new Quote(
						42, BID, ASK, 4_000_000_000L, 250, 7, 10_060.5, Venue.XNYS, MarketState.HALTED,
						EnumSet.of(QuoteFlag.CROSSED), "ACME", EXPONENT, DEPTH, TRADE
				)
		);
		assertThat(codec.lastDecodedLength()).isEqualTo(MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
	}

	@Test
	void aVenueNoConstantNamesDecodesToTheUnknownValue() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
		QuoteEncoder encoder = new QuoteEncoder().wrapAndApplyHeader(buffer, OFFSET, new MessageHeaderEncoder());
		encoder.instrumentId(42).sequence(7).state(com.example.quotes.xmlref.MarketState.OPEN);
		// A venue a later schema version added, written as the raw wire value.
		buffer.putByte(OFFSET + MessageHeaderEncoder.ENCODED_LENGTH + QuoteEncoder.venueEncodingOffset(), (byte) 9);

		Quote decoded = new QuoteCodec().decode(buffer, OFFSET);

		assertThat(decoded.venue()).isEqualTo(Venue.OTHER);
	}

	@Test
	void aStateNoConstantNamesIsRefused() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
		QuoteEncoder encoder = new QuoteEncoder().wrapAndApplyHeader(buffer, OFFSET, new MessageHeaderEncoder());
		encoder.instrumentId(42).sequence(7).venue(com.example.quotes.xmlref.Venue.XNAS);
		buffer.putByte(OFFSET + MessageHeaderEncoder.ENCODED_LENGTH + QuoteEncoder.stateEncodingOffset(), (byte) 'X');
		QuoteCodec codec = new QuoteCodec();

		assertThatThrownBy(() -> codec.decode(buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("MarketState has no value 88");
	}

	@Test
	void aFlagBitNoChoiceNamesIsRefused() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
		QuoteEncoder encoder = new QuoteEncoder().wrapAndApplyHeader(buffer, OFFSET, new MessageHeaderEncoder());
		encoder.instrumentId(42).sequence(7).venue(com.example.quotes.xmlref.Venue.XNAS)
				.state(com.example.quotes.xmlref.MarketState.OPEN);
		encoder.flags().setRaw((short) 8);
		QuoteCodec codec = new QuoteCodec();

		assertThatThrownBy(() -> codec.decode(buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("QuoteFlag has a bit no choice names: 8");
	}

	@Test
	void aVersionFiveMessageDecodesWithoutALastTrade() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
		com.example.quotes.xmlref.v5.QuoteEncoder encoder = new com.example.quotes.xmlref.v5.QuoteEncoder()
				.wrapAndApplyHeader(buffer, OFFSET, new com.example.quotes.xmlref.v5.MessageHeaderEncoder());
		encoder.instrumentId(42).bid(10_050).ask(10_075).bidSize(4_000_000_000L).askSize(250).sequence(7)
				.vwap(10_060.5).venue(com.example.quotes.xmlref.v5.Venue.XNAS)
				.state(com.example.quotes.xmlref.v5.MarketState.OPEN);
		encoder.flags().indicative(true).locked(true);
		encoder.symbol("ACME").bidDepth(0, 4_000_000_000L).bidDepth(1, 900).bidDepth(2, 800).bidDepth(3, 700)
				.bidDepth(4, 600);
		QuoteCodec codec = new QuoteCodec();

		Quote decoded = codec.decode(buffer, OFFSET);

		assertThat(decoded).usingRecursiveComparison().isEqualTo(
				new Quote(
						42, BID, ASK, 4_000_000_000L, 250, 7, 10_060.5, Venue.XNAS, MarketState.OPEN,
						EnumSet.of(QuoteFlag.INDICATIVE, QuoteFlag.LOCKED), "ACME", EXPONENT, DEPTH, null
				)
		);
		int shorter = com.example.quotes.xmlref.v5.MessageHeaderEncoder.ENCODED_LENGTH
				+ com.example.quotes.xmlref.v5.QuoteEncoder.BLOCK_LENGTH;
		assertThat(shorter).isLessThan(codec.encodedLength(QUOTE));
		assertThat(codec.lastDecodedLength()).isEqualTo(shorter);
	}

	@Test
	void aVersionFiveReaderReadsACurrentMessageWhole() {
		QuoteCodec codec = new QuoteCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
		int written = codec.encode(QUOTE, buffer, OFFSET);

		com.example.quotes.xmlref.v5.QuoteDecoder decoder = new com.example.quotes.xmlref.v5.QuoteDecoder()
				.wrapAndApplyHeader(buffer, OFFSET, new com.example.quotes.xmlref.v5.MessageHeaderDecoder());

		assertThat(decoder.symbol()).isEqualTo("ACME");
		assertThat(decoder.actingVersion()).isEqualTo(6);
		assertThat(com.example.quotes.xmlref.v5.MessageHeaderDecoder.ENCODED_LENGTH + decoder.encodedLength())
				.isEqualTo(written);
	}

	@Test
	void aVersionFourMessageDecodesWithTheConstantAndItsArraysAbsent() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
		com.example.quotes.xmlref.v4.QuoteEncoder encoder = new com.example.quotes.xmlref.v4.QuoteEncoder()
				.wrapAndApplyHeader(buffer, OFFSET, new com.example.quotes.xmlref.v4.MessageHeaderEncoder());
		encoder.instrumentId(42).bid(10_050).ask(10_075).bidSize(4_000_000_000L).askSize(250).sequence(7)
				.vwap(10_060.5).venue(com.example.quotes.xmlref.v4.Venue.XNAS)
				.state(com.example.quotes.xmlref.v4.MarketState.OPEN);
		encoder.flags().indicative(true).locked(true);
		QuoteCodec codec = new QuoteCodec();

		Quote decoded = codec.decode(buffer, OFFSET);

		assertThat(decoded).isEqualTo(
				new Quote(
						42, BID, ASK, 4_000_000_000L, 250, 7, 10_060.5, Venue.XNAS, MarketState.OPEN,
						EnumSet.of(QuoteFlag.INDICATIVE, QuoteFlag.LOCKED), null, EXPONENT, null, null
				)
		);
		int shorter = com.example.quotes.xmlref.v4.MessageHeaderEncoder.ENCODED_LENGTH
				+ com.example.quotes.xmlref.v4.QuoteEncoder.BLOCK_LENGTH;
		assertThat(shorter).isLessThan(codec.encodedLength(QUOTE));
		assertThat(codec.lastDecodedLength()).isEqualTo(shorter);
	}

	@Test
	void aVersionFourReaderReadsACurrentMessageWhole() {
		QuoteCodec codec = new QuoteCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
		int written = codec.encode(QUOTE, buffer, OFFSET);

		com.example.quotes.xmlref.v4.QuoteDecoder decoder = new com.example.quotes.xmlref.v4.QuoteDecoder()
				.wrapAndApplyHeader(buffer, OFFSET, new com.example.quotes.xmlref.v4.MessageHeaderDecoder());

		assertThat(decoder.tradeCount()).isEqualTo(com.example.quotes.xmlref.v4.QuoteDecoder.tradeCountNullValue());
		assertThat(decoder.flags().locked()).isTrue();
		assertThat(decoder.actingVersion()).isEqualTo(6);
		assertThat(com.example.quotes.xmlref.v4.MessageHeaderDecoder.ENCODED_LENGTH + decoder.encodedLength())
				.isEqualTo(written);
	}

	@Test
	void aVersionThreeMessageWithATradeCountDecodesWithoutIt() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
		com.example.quotes.xmlref.v3.QuoteEncoder encoder = new com.example.quotes.xmlref.v3.QuoteEncoder()
				.wrapAndApplyHeader(buffer, OFFSET, new com.example.quotes.xmlref.v3.MessageHeaderEncoder());
		encoder.instrumentId(42).bid(10_050).ask(10_075).bidSize(4_000_000_000L).askSize(250).sequence(7)
				.tradeCount(3).vwap(10_060.5).venue(com.example.quotes.xmlref.v3.Venue.XNAS)
				.state(com.example.quotes.xmlref.v3.MarketState.OPEN);
		encoder.flags().indicative(true).locked(true);
		QuoteCodec codec = new QuoteCodec();

		Quote decoded = codec.decode(buffer, OFFSET);

		assertThat(decoded).isEqualTo(
				new Quote(
						42, BID, ASK, 4_000_000_000L, 250, 7, 10_060.5, Venue.XNAS, MarketState.OPEN,
						EnumSet.of(QuoteFlag.INDICATIVE, QuoteFlag.LOCKED), null, EXPONENT, null, null
				)
		);
		assertThat(codec.lastDecodedLength()).isLessThan(codec.encodedLength(QUOTE));
	}

	@Test
	void aVersionThreeReaderReadsACurrentMessageWhole() {
		QuoteCodec codec = new QuoteCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
		int written = codec.encode(QUOTE, buffer, OFFSET);

		com.example.quotes.xmlref.v3.QuoteDecoder decoder = new com.example.quotes.xmlref.v3.QuoteDecoder()
				.wrapAndApplyHeader(buffer, OFFSET, new com.example.quotes.xmlref.v3.MessageHeaderDecoder());

		assertThat(decoder.tradeCount()).isEqualTo(com.example.quotes.xmlref.v3.QuoteDecoder.tradeCountNullValue());
		assertThat(decoder.venue()).isEqualTo(com.example.quotes.xmlref.v3.Venue.XNAS);
		assertThat(decoder.actingVersion()).isEqualTo(6);
		assertThat(com.example.quotes.xmlref.v3.MessageHeaderDecoder.ENCODED_LENGTH + decoder.encodedLength())
				.isEqualTo(written);
	}

	@Test
	void aVersionTwoMessageDecodesWithItsLaterFieldsAbsent() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
		com.example.quotes.xmlref.v2.QuoteEncoder encoder = new com.example.quotes.xmlref.v2.QuoteEncoder()
				.wrapAndApplyHeader(buffer, OFFSET, new com.example.quotes.xmlref.v2.MessageHeaderEncoder());
		encoder.instrumentId(42).bid(10_050).ask(10_075).bidSize(4_000_000_000L).askSize(250).sequence(7)
				.tradeCount(3).vwap(10_060.5);
		QuoteCodec codec = new QuoteCodec();

		Quote decoded = codec.decode(buffer, OFFSET);

		assertThat(decoded).isEqualTo(
				new Quote(42, BID, ASK, 4_000_000_000L, 250, 7, 10_060.5, null, null, null, null, EXPONENT, null, null)
		);
		int shorter = com.example.quotes.xmlref.v2.MessageHeaderEncoder.ENCODED_LENGTH
				+ com.example.quotes.xmlref.v2.QuoteEncoder.BLOCK_LENGTH;
		assertThat(shorter).isLessThan(codec.encodedLength(QUOTE));
		assertThat(codec.lastDecodedLength()).isEqualTo(shorter);
	}

	@Test
	void aVersionOneMessageDecodesWithItsLaterFieldsAbsent() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
		com.example.quotes.xmlref.v1.QuoteEncoder encoder = new com.example.quotes.xmlref.v1.QuoteEncoder()
				.wrapAndApplyHeader(buffer, OFFSET, new com.example.quotes.xmlref.v1.MessageHeaderEncoder());
		encoder.instrumentId(42).bid(10_050).ask(10_075).bidSize(4_000_000_000L).askSize(250).sequence(7);
		QuoteCodec codec = new QuoteCodec();

		Quote decoded = codec.decode(buffer, OFFSET);

		assertThat(decoded).isEqualTo(
				new Quote(42, BID, ASK, 4_000_000_000L, 250, 7, null, null, null, null, null, EXPONENT, null, null)
		);
		int shorter = com.example.quotes.xmlref.v1.MessageHeaderEncoder.ENCODED_LENGTH
				+ com.example.quotes.xmlref.v1.QuoteEncoder.BLOCK_LENGTH;
		assertThat(codec.lastDecodedLength()).isEqualTo(shorter);
		assertThat(codec.decodedLength(buffer, OFFSET)).isEqualTo(shorter);
	}

	@Test
	void aVersionOneReaderReadsACurrentMessageWhole() {
		QuoteCodec codec = new QuoteCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
		int written = codec.encode(QUOTE, buffer, OFFSET);

		com.example.quotes.xmlref.v1.QuoteDecoder decoder = new com.example.quotes.xmlref.v1.QuoteDecoder()
				.wrapAndApplyHeader(buffer, OFFSET, new com.example.quotes.xmlref.v1.MessageHeaderDecoder());

		assertThat(decoder.instrumentId()).isEqualTo(42);
		assertThat(decoder.sequence()).isEqualTo(7);
		assertThat(decoder.actingVersion()).isEqualTo(6);
		// The acting block length is the header's, so the longer block is consumed
		// whole.
		assertThat(com.example.quotes.xmlref.v1.MessageHeaderDecoder.ENCODED_LENGTH + decoder.encodedLength())
				.isEqualTo(written);
	}

	@Test
	void aVersionZeroMessageIsBelowTheBaselineAndRefused() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
		new com.example.quotes.xmlref.v0.QuoteEncoder()
				.wrapAndApplyHeader(buffer, OFFSET, new com.example.quotes.xmlref.v0.MessageHeaderEncoder())
				.instrumentId(42)
				.bid(10_050)
				.ask(10_075)
				.bidSize(4_000_000_000L)
				.askSize(250);
		QuoteCodec codec = new QuoteCodec();

		assertThatThrownBy(() -> codec.decode(buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Quote version 0 is below the baseline 1");
	}
}
