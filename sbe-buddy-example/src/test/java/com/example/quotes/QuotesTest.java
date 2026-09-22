package com.example.quotes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.generator.SchemaXmlAssert;

import com.example.quotes.sbe.MessageHeaderEncoder;

/**
 * The codec seen working in the real build: a record goes onto the wire through
 * the generated codec and comes back equal, at an offset, with every length the
 * contract promises agreeing, with and without its optional field; what it
 * refuses, it refuses with the exception the contract names: a string that does
 * not fit, an array of the wrong length, a constant the record disagrees with;
 * what a binding refuses passes through as the binding's own exception.
 */
final class QuotesTest {

	private static final String RESOURCE = "/com/example/quotes/schema.xml";

	private static final int OFFSET = 16;

	private static final BigDecimal BID = new BigDecimal("1.0050");

	private static final BigDecimal ASK = new BigDecimal("1.0075");

	private static final byte EXPONENT = -4;

	private static final long[] DEPTH = {4_000_000_000L, 900, 800, 700, 600};

	@Test
	void theSchemaInTheJarIsTheOracle() throws IOException {
		try (InputStream schema = QuotesTest.class.getResourceAsStream(RESOURCE)) {
			assertThat(schema).as("the processor wrote %s", RESOURCE).isNotNull();
			SchemaXmlAssert.assertThat(new String(schema.readAllBytes(), StandardCharsets.UTF_8))
					.matches(Files.readString(Path.of("src/main/sbe/quotes.xml")));
		}
	}

	@Test
	void aQuoteRoundTripsThroughItsCodec() {
		assertRoundTrip(
				new Quote(
						42, BID, ASK, 4_000_000_000L, 250, 7, 10_060.5, Venue.XNAS, MarketState.OPEN,
						EnumSet.of(QuoteFlag.INDICATIVE, QuoteFlag.LOCKED), "ACME", EXPONENT, DEPTH
				)
		);
	}

	@Test
	void aQuoteWithoutAVwapAndWithoutFlagsRoundTrips() {
		assertRoundTrip(
				new Quote(
						42, BID, ASK, 4_000_000_000L, 250, 7, null, Venue.XLON, MarketState.CLOSED, Set.of(), "",
						EXPONENT, new long[5]
				)
		);
	}

	@Test
	void aNullVenueIsRefused() {
		QuoteCodec codec = new QuoteCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
		Quote quote = new Quote(
				42, BID, ASK, 4_000_000_000L, 250, 7, null, null, MarketState.OPEN, Set.of(), "ACME", EXPONENT,
				DEPTH
		);

		assertThatThrownBy(() -> codec.encode(quote, buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("venue is required");
	}

	@Test
	void theUnknownVenueHasNoWireFormAndIsRefused() {
		QuoteCodec codec = new QuoteCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
		Quote quote = new Quote(
				42, BID, ASK, 4_000_000_000L, 250, 7, null, Venue.OTHER, MarketState.OPEN, Set.of(), "ACME",
				EXPONENT, DEPTH
		);

		assertThatThrownBy(() -> codec.encode(quote, buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Venue.OTHER has no wire form");
	}

	@Test
	void aSymbolLongerThanItsFieldIsRefused() {
		QuoteCodec codec = new QuoteCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
		Quote quote = quoteWith("LONGNAME1", EXPONENT, DEPTH);

		assertThatThrownBy(() -> codec.encode(quote, buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("symbol is longer than 8: LONGNAME1");
	}

	@Test
	void aSymbolOutsideAsciiIsRefused() {
		QuoteCodec codec = new QuoteCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
		Quote quote = quoteWith("ACMÉ", EXPONENT, DEPTH);

		assertThatThrownBy(() -> codec.encode(quote, buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("symbol is not ASCII: ACMÉ");
	}

	@Test
	void aDepthOfTheWrongLengthIsRefused() {
		QuoteCodec codec = new QuoteCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
		Quote quote = quoteWith("ACME", EXPONENT, new long[4]);

		assertThatThrownBy(() -> codec.encode(quote, buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("bidDepth must be 5 long, not 4");
	}

	@Test
	void anExponentOtherThanTheConstantIsRefused() {
		QuoteCodec codec = new QuoteCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
		Quote quote = quoteWith("ACME", (byte) -2, DEPTH);

		assertThatThrownBy(() -> codec.encode(quote, buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("priceExponent is the constant -4");
	}

	@Test
	void aPriceWithMoreDecimalsThanTheExponentAllowsIsTheBindingsOwnException() {
		QuoteCodec codec = new QuoteCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
		Quote quote = new Quote(
				42, new BigDecimal("1.00505"), ASK, 4_000_000_000L, 250, 7, null, Venue.XNAS, MarketState.OPEN,
				Set.of(), "ACME", EXPONENT, DEPTH
		);

		assertThatThrownBy(() -> codec.encode(quote, buffer, OFFSET)).isInstanceOf(ArithmeticException.class);
	}

	@Test
	void anotherTemplateIsRefused() {
		QuoteCodec codec = new QuoteCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
		codec.encode(quoteWith("ACME", EXPONENT, DEPTH), buffer, 0);
		new MessageHeaderEncoder().wrap(buffer, 0).templateId(99);

		assertThatThrownBy(() -> codec.decode(buffer, 0))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("templateId 99");
	}

	private static Quote quoteWith(String symbol, byte priceExponent, long[] bidDepth) {
		return new Quote(
				42, BID, ASK, 4_000_000_000L, 250, 7, null, Venue.XNAS, MarketState.OPEN, Set.of(), symbol,
				priceExponent, bidDepth
		);
	}

	private static void assertRoundTrip(Quote quote) {
		QuoteCodec codec = new QuoteCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);

		int written = codec.encode(quote, buffer, OFFSET);

		assertThat(written).isEqualTo(codec.encodedLength(quote));
		assertThat(codec.decodedLength(buffer, OFFSET)).isEqualTo(written);
		// An array component compares by identity in a record's own equals.
		assertThat(codec.decode(buffer, OFFSET)).usingRecursiveComparison().isEqualTo(quote);
		assertThat(codec.lastDecodedLength()).isEqualTo(written);
	}
}
