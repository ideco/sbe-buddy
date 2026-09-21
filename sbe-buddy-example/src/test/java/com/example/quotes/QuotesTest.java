package com.example.quotes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.generator.SchemaXmlAssert;

import com.example.quotes.sbe.MessageHeaderEncoder;

/**
 * The codec seen working in the real build: a record goes onto the wire through
 * the generated codec and comes back equal, at an offset, with every length the
 * contract promises agreeing, with and without its optional field; what it
 * refuses, it refuses with the exception the contract names.
 */
final class QuotesTest {

	private static final String RESOURCE = "/com/example/quotes/schema.xml";

	private static final int OFFSET = 16;

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
		assertRoundTrip(new Quote(42, 10_050, 10_075, 4_000_000_000L, 250, 7, 3L, 10_060.5));
	}

	@Test
	void aQuoteWithoutAVwapRoundTrips() {
		assertRoundTrip(new Quote(42, 10_050, 10_075, 4_000_000_000L, 250, 7, 0L, null));
	}

	@Test
	void aNullTradeCountIsRefused() {
		QuoteCodec codec = new QuoteCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
		Quote quote = new Quote(42, 10_050, 10_075, 4_000_000_000L, 250, 7, null, null);

		assertThatThrownBy(() -> codec.encode(quote, buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("tradeCount is required");
	}

	@Test
	void anotherTemplateIsRefused() {
		QuoteCodec codec = new QuoteCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
		codec.encode(new Quote(1, 2, 3, 4, 5, 6, 7L, null), buffer, 0);
		new MessageHeaderEncoder().wrap(buffer, 0).templateId(99);

		assertThatThrownBy(() -> codec.decode(buffer, 0))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("templateId 99");
	}

	private static void assertRoundTrip(Quote quote) {
		QuoteCodec codec = new QuoteCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);

		int written = codec.encode(quote, buffer, OFFSET);

		assertThat(written).isEqualTo(codec.encodedLength(quote));
		assertThat(codec.decodedLength(buffer, OFFSET)).isEqualTo(written);
		assertThat(codec.decode(buffer, OFFSET)).isEqualTo(quote);
		assertThat(codec.lastDecodedLength()).isEqualTo(written);
	}
}
