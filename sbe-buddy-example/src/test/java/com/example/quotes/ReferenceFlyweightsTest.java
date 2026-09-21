package com.example.quotes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import com.example.quotes.xmlref.MessageHeaderDecoder;
import com.example.quotes.xmlref.MessageHeaderEncoder;
import com.example.quotes.xmlref.QuoteDecoder;
import com.example.quotes.xmlref.QuoteEncoder;

/**
 * The bytes are sbe-tool's: what the codec writes, the flyweights sbe-tool
 * generated from the oracle read, and the reverse; and across versions, a
 * message from a version 1 writer decodes with its later fields absent, a
 * version 1 reader reads a current message whole, and a version 0 message is
 * below the baseline and refused. The frozen versions' flyweights are qualified
 * because every version names its classes alike.
 */
final class ReferenceFlyweightsTest {

	private static final int OFFSET = 16;

	@Test
	void theReferenceDecoderReadsWhatTheCodecWrites() {
		QuoteCodec codec = new QuoteCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
		int written = codec.encode(new Quote(42, 10_050, 10_075, 4_000_000_000L, 250, 7, 3L, 10_060.5), buffer, OFFSET);

		QuoteDecoder decoder = new QuoteDecoder().wrapAndApplyHeader(buffer, OFFSET, new MessageHeaderDecoder());

		assertThat(decoder.instrumentId()).isEqualTo(42);
		assertThat(decoder.bid()).isEqualTo(10_050);
		assertThat(decoder.ask()).isEqualTo(10_075);
		assertThat(decoder.bidSize()).isEqualTo(4_000_000_000L);
		assertThat(decoder.askSize()).isEqualTo(250);
		assertThat(decoder.sequence()).isEqualTo(7);
		assertThat(decoder.tradeCount()).isEqualTo(3);
		assertThat(decoder.vwap()).isEqualTo(10_060.5);
		assertThat(MessageHeaderDecoder.ENCODED_LENGTH + decoder.encodedLength()).isEqualTo(written);
	}

	@Test
	void anAbsentVwapIsTheNullValueOnTheWire() {
		QuoteCodec codec = new QuoteCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
		codec.encode(new Quote(42, 10_050, 10_075, 4_000_000_000L, 250, 7, 0L, null), buffer, OFFSET);

		QuoteDecoder decoder = new QuoteDecoder().wrapAndApplyHeader(buffer, OFFSET, new MessageHeaderDecoder());

		assertThat(decoder.vwap()).isNaN();
		assertThat(QuoteDecoder.vwapNullValue()).isNaN();
	}

	@Test
	void theCodecReadsWhatTheReferenceEncoderWrites() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
		QuoteEncoder encoder = new QuoteEncoder().wrapAndApplyHeader(buffer, OFFSET, new MessageHeaderEncoder());
		encoder.instrumentId(42).bid(10_050).ask(10_075).bidSize(4_000_000_000L).askSize(250).sequence(7)
				.tradeCount(3).vwap(10_060.5);
		QuoteCodec codec = new QuoteCodec();

		Quote decoded = codec.decode(buffer, OFFSET);

		assertThat(decoded).isEqualTo(new Quote(42, 10_050, 10_075, 4_000_000_000L, 250, 7, 3L, 10_060.5));
		assertThat(codec.lastDecodedLength()).isEqualTo(MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength());
	}

	@Test
	void aVersionOneMessageDecodesWithItsLaterFieldsAbsent() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
		com.example.quotes.xmlref.v1.QuoteEncoder encoder = new com.example.quotes.xmlref.v1.QuoteEncoder()
				.wrapAndApplyHeader(buffer, OFFSET, new com.example.quotes.xmlref.v1.MessageHeaderEncoder());
		encoder.instrumentId(42).bid(10_050).ask(10_075).bidSize(4_000_000_000L).askSize(250).sequence(7);
		QuoteCodec codec = new QuoteCodec();

		Quote decoded = codec.decode(buffer, OFFSET);

		assertThat(decoded).isEqualTo(new Quote(42, 10_050, 10_075, 4_000_000_000L, 250, 7, null, null));
		int shorter = com.example.quotes.xmlref.v1.MessageHeaderEncoder.ENCODED_LENGTH
				+ com.example.quotes.xmlref.v1.QuoteEncoder.BLOCK_LENGTH;
		assertThat(shorter).isLessThan(codec.encodedLength(decoded));
		assertThat(codec.lastDecodedLength()).isEqualTo(shorter);
		assertThat(codec.decodedLength(buffer, OFFSET)).isEqualTo(shorter);
	}

	@Test
	void aVersionOneReaderReadsACurrentMessageWhole() {
		QuoteCodec codec = new QuoteCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);
		int written = codec.encode(new Quote(42, 10_050, 10_075, 4_000_000_000L, 250, 7, 3L, 10_060.5), buffer, OFFSET);

		com.example.quotes.xmlref.v1.QuoteDecoder decoder = new com.example.quotes.xmlref.v1.QuoteDecoder()
				.wrapAndApplyHeader(buffer, OFFSET, new com.example.quotes.xmlref.v1.MessageHeaderDecoder());

		assertThat(decoder.instrumentId()).isEqualTo(42);
		assertThat(decoder.sequence()).isEqualTo(7);
		assertThat(decoder.actingVersion()).isEqualTo(2);
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
