package com.example.quotes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
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
 * message from an older writer decodes with its later fields absent and its
 * later group and remark {@code null}, an older reader reads a current
 * message's block, or its group, and stops before what it does not know, which
 * is SBE's limit, and a version 0 message is below the baseline and refused.
 * The retired trade count is written as its null value and never read; the
 * constant price exponent is on no wire and in every decoded record; the prices
 * are mantissas on the wire and decimals in the record, through the binding
 * both ways, inside the group as outside it; the remark is UTF-8 both ways. The
 * frozen versions' flyweights, and the reference enums whose simple names are
 * the example's own, are qualified.
 */
final class ReferenceFlyweightsTest {

	private static final int OFFSET = 16;

	private static final BigDecimal BID = new BigDecimal("1.0050");

	private static final BigDecimal ASK = new BigDecimal("1.0075");

	private static final byte EXPONENT = -4;

	private static final long[] DEPTH = {4_000_000_000L, 900, 800, 700, 600};

	private static final Trade TRADE = new Trade(10_060, 300);

	private static final List<Contributor> CONTRIBUTORS = List.of(
			new Contributor(Venue.XNAS, BID, ASK, 4_000_000_000L, 250, 12L),
			new Contributor(Venue.XLON, new BigDecimal("1.0025"), new BigDecimal("1.0100"), 100, 4_000_000_000L, 3L)
	);

	private static final String REMARK = "Firm to 12:00 — size negotiable";

	private static final Quote QUOTE = new Quote(
			42, BID, ASK, 4_000_000_000L, 250, 7, 10_060.5, Venue.XNAS, MarketState.OPEN,
			EnumSet.of(QuoteFlag.INDICATIVE, QuoteFlag.LOCKED), "ACME", EXPONENT, DEPTH, TRADE, CONTRIBUTORS, REMARK
	);

	/** The header and the block: what a reader without the group consumes. */
	private static final int BLOCK = MessageHeaderEncoder.ENCODED_LENGTH + QuoteEncoder.BLOCK_LENGTH;

	@Test
	void theReferenceDecoderReadsWhatTheCodecWrites() {
		QuoteCodec codec = new QuoteCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
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
		QuoteDecoder.ContributorsDecoder contributors = decoder.contributors();
		assertThat(contributors.count()).isEqualTo(2);
		contributors.next();
		assertThat(contributors.venue()).isEqualTo(com.example.quotes.xmlref.Venue.XNAS);
		assertThat(contributors.bid()).isEqualTo(10_050);
		assertThat(contributors.ask()).isEqualTo(10_075);
		assertThat(contributors.bidSize()).isEqualTo(4_000_000_000L);
		assertThat(contributors.askSize()).isEqualTo(250);
		assertThat(contributors.bidOrders()).isEqualTo(12);
		contributors.next();
		assertThat(contributors.venue()).isEqualTo(com.example.quotes.xmlref.Venue.XLON);
		assertThat(contributors.bid()).isEqualTo(10_025);
		assertThat(contributors.ask()).isEqualTo(10_100);
		assertThat(contributors.bidSize()).isEqualTo(100);
		assertThat(contributors.askSize()).isEqualTo(4_000_000_000L);
		assertThat(contributors.bidOrders()).isEqualTo(3);
		assertThat(contributors.hasNext()).isFalse();
		assertThat(decoder.remark()).isEqualTo(REMARK);
		assertThat(MessageHeaderDecoder.ENCODED_LENGTH + decoder.encodedLength()).isEqualTo(written);
		assertThat(MessageHeaderDecoder.ENCODED_LENGTH + decoder.sbeDecodedLength()).isEqualTo(written);
	}

	@Test
	void anAbsentVwapIsTheNullValueOnTheWire() {
		QuoteCodec codec = new QuoteCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
		codec.encode(
				new Quote(
						42, BID, ASK, 4_000_000_000L, 250, 7, null, Venue.XNAS, MarketState.OPEN, Set.of(),
						"ACME",
						EXPONENT, DEPTH, TRADE, List.of(), ""
				),
				buffer, OFFSET
		);

		QuoteDecoder decoder = new QuoteDecoder().wrapAndApplyHeader(buffer, OFFSET, new MessageHeaderDecoder());

		assertThat(decoder.vwap()).isNaN();
		assertThat(QuoteDecoder.vwapNullValue()).isNaN();
		assertThat(decoder.flags().isEmpty()).isTrue();
		assertThat(decoder.contributors().count()).isZero();
		assertThat(decoder.remarkLength()).isZero();
	}

	@Test
	void theCodecReadsWhatTheReferenceEncoderWritesAndSkipsTheTradeCount() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
		QuoteEncoder encoder = new QuoteEncoder().wrapAndApplyHeader(buffer, OFFSET, new MessageHeaderEncoder());
		encoder.instrumentId(42).bid(10_050).ask(10_075).bidSize(4_000_000_000L).askSize(250).sequence(7)
				.tradeCount(3).vwap(10_060.5).venue(com.example.quotes.xmlref.Venue.XNYS)
				.state(com.example.quotes.xmlref.MarketState.HALTED);
		encoder.flags().crossed(true);
		encoder.symbol("ACME").bidDepth(0, 4_000_000_000L).bidDepth(1, 900).bidDepth(2, 800).bidDepth(3, 700)
				.bidDepth(4, 600);
		encoder.lastTrade().price(10_060).size(300);
		QuoteEncoder.ContributorsEncoder contributors = encoder.contributorsCount(2);
		contributors.next().venue(com.example.quotes.xmlref.Venue.XNAS).bid(10_050).ask(10_075)
				.bidSize(4_000_000_000L).askSize(250).bidOrders(12);
		contributors.next().venue(com.example.quotes.xmlref.Venue.XLON).bid(10_025).ask(10_100).bidSize(100)
				.askSize(4_000_000_000L).bidOrders(3);
		encoder.remark(REMARK);
		QuoteCodec codec = new QuoteCodec();

		Quote decoded = codec.decode(buffer, OFFSET);

		assertThat(decoded).usingRecursiveComparison().isEqualTo(
				new Quote(
						42, BID, ASK, 4_000_000_000L, 250, 7, 10_060.5, Venue.XNYS, MarketState.HALTED,
						EnumSet.of(QuoteFlag.CROSSED), "ACME", EXPONENT, DEPTH, TRADE, CONTRIBUTORS, REMARK
				)
		);
		int written = MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength();
		assertThat(codec.lastDecodedLength()).isEqualTo(written);
		assertThat(codec.decodedLength(buffer, OFFSET)).isEqualTo(written);
	}

	@Test
	void aVenueNoConstantNamesDecodesToTheUnknownValue() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
		QuoteEncoder encoder = new QuoteEncoder().wrapAndApplyHeader(buffer, OFFSET, new MessageHeaderEncoder());
		encoder.instrumentId(42).sequence(7).state(com.example.quotes.xmlref.MarketState.OPEN);
		encoder.contributorsCount(0);
		// A venue a later schema version added, written as the raw wire value.
		buffer.putByte(OFFSET + MessageHeaderEncoder.ENCODED_LENGTH + QuoteEncoder.venueEncodingOffset(), (byte) 9);

		Quote decoded = new QuoteCodec().decode(buffer, OFFSET);

		assertThat(decoded.venue()).isEqualTo(Venue.OTHER);
	}

	@Test
	void aStateNoConstantNamesIsRefused() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
		QuoteEncoder encoder = new QuoteEncoder().wrapAndApplyHeader(buffer, OFFSET, new MessageHeaderEncoder());
		encoder.instrumentId(42).sequence(7).venue(com.example.quotes.xmlref.Venue.XNAS);
		encoder.contributorsCount(0);
		buffer.putByte(OFFSET + MessageHeaderEncoder.ENCODED_LENGTH + QuoteEncoder.stateEncodingOffset(), (byte) 'X');
		QuoteCodec codec = new QuoteCodec();

		assertThatThrownBy(() -> codec.decode(buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("MarketState has no value 88");
	}

	@Test
	void aFlagBitNoChoiceNamesIsRefused() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
		QuoteEncoder encoder = new QuoteEncoder().wrapAndApplyHeader(buffer, OFFSET, new MessageHeaderEncoder());
		encoder.instrumentId(42).sequence(7).venue(com.example.quotes.xmlref.Venue.XNAS)
				.state(com.example.quotes.xmlref.MarketState.OPEN);
		encoder.flags().setRaw((short) 8);
		encoder.contributorsCount(0);
		QuoteCodec codec = new QuoteCodec();

		assertThatThrownBy(() -> codec.decode(buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("QuoteFlag has a bit no choice names: 8");
	}

	@Test
	void aVersionEightMessageDecodesWithoutTheOrderCounts() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
		com.example.quotes.xmlref.v8.QuoteEncoder encoder = new com.example.quotes.xmlref.v8.QuoteEncoder()
				.wrapAndApplyHeader(buffer, OFFSET, new com.example.quotes.xmlref.v8.MessageHeaderEncoder());
		encoder.instrumentId(42).bid(10_050).ask(10_075).bidSize(4_000_000_000L).askSize(250).sequence(7)
				.vwap(10_060.5).venue(com.example.quotes.xmlref.v8.Venue.XNAS)
				.state(com.example.quotes.xmlref.v8.MarketState.OPEN);
		encoder.flags().indicative(true).locked(true);
		encoder.symbol("ACME").bidDepth(0, 4_000_000_000L).bidDepth(1, 900).bidDepth(2, 800).bidDepth(3, 700)
				.bidDepth(4, 600);
		encoder.lastTrade().price(10_060).size(300);
		com.example.quotes.xmlref.v8.QuoteEncoder.ContributorsEncoder contributors = encoder.contributorsCount(2);
		contributors.next().venue(com.example.quotes.xmlref.v8.Venue.XNAS).bid(10_050).ask(10_075)
				.bidSize(4_000_000_000L).askSize(250);
		contributors.next().venue(com.example.quotes.xmlref.v8.Venue.XLON).bid(10_025).ask(10_100).bidSize(100)
				.askSize(4_000_000_000L);
		encoder.remark(REMARK);
		QuoteCodec codec = new QuoteCodec();

		Quote decoded = codec.decode(buffer, OFFSET);

		assertThat(decoded).usingRecursiveComparison().isEqualTo(
				new Quote(
						42, BID, ASK, 4_000_000_000L, 250, 7, 10_060.5, Venue.XNAS, MarketState.OPEN,
						EnumSet.of(QuoteFlag.INDICATIVE, QuoteFlag.LOCKED), "ACME", EXPONENT, DEPTH, TRADE,
						List.of(
								new Contributor(Venue.XNAS, BID, ASK, 4_000_000_000L, 250, null),
								new Contributor(
										Venue.XLON, new BigDecimal("1.0025"), new BigDecimal("1.0100"), 100,
										4_000_000_000L, null
								)
						),
						REMARK
				)
		);
		int written = com.example.quotes.xmlref.v8.MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength();
		assertThat(codec.lastDecodedLength()).isEqualTo(written);
		assertThat(codec.decodedLength(buffer, OFFSET)).isEqualTo(written);
	}

	@Test
	void aVersionEightReaderStepsOverTheOrderCountInEveryEntry() {
		QuoteCodec codec = new QuoteCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
		int written = codec.encode(QUOTE, buffer, OFFSET);

		com.example.quotes.xmlref.v8.QuoteDecoder decoder = new com.example.quotes.xmlref.v8.QuoteDecoder()
				.wrapAndApplyHeader(buffer, OFFSET, new com.example.quotes.xmlref.v8.MessageHeaderDecoder());

		assertThat(decoder.actingVersion()).isEqualTo(9);
		com.example.quotes.xmlref.v8.QuoteDecoder.ContributorsDecoder contributors = decoder.contributors();
		assertThat(contributors.count()).isEqualTo(2);
		assertThat(contributors.next().askSize()).isEqualTo(250);
		assertThat(contributors.next().askSize()).isEqualTo(4_000_000_000L);
		// The entries' block length steps over the appended field, so what follows
		// the group is where a version 8 reader looks for it.
		assertThat(decoder.remark()).isEqualTo(REMARK);
		assertThat(com.example.quotes.xmlref.v8.MessageHeaderDecoder.ENCODED_LENGTH + decoder.encodedLength())
				.isEqualTo(written);
	}

	@Test
	void aVersionSevenMessageDecodesWithoutARemark() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
		com.example.quotes.xmlref.v7.QuoteEncoder encoder = new com.example.quotes.xmlref.v7.QuoteEncoder()
				.wrapAndApplyHeader(buffer, OFFSET, new com.example.quotes.xmlref.v7.MessageHeaderEncoder());
		encoder.instrumentId(42).bid(10_050).ask(10_075).bidSize(4_000_000_000L).askSize(250).sequence(7)
				.vwap(10_060.5).venue(com.example.quotes.xmlref.v7.Venue.XNAS)
				.state(com.example.quotes.xmlref.v7.MarketState.OPEN);
		encoder.flags().indicative(true).locked(true);
		encoder.symbol("ACME").bidDepth(0, 4_000_000_000L).bidDepth(1, 900).bidDepth(2, 800).bidDepth(3, 700)
				.bidDepth(4, 600);
		encoder.lastTrade().price(10_060).size(300);
		encoder.contributorsCount(1).next().venue(com.example.quotes.xmlref.v7.Venue.XNAS).bid(10_050).ask(10_075)
				.bidSize(4_000_000_000L).askSize(250);
		QuoteCodec codec = new QuoteCodec();

		Quote decoded = codec.decode(buffer, OFFSET);

		assertThat(decoded).usingRecursiveComparison().isEqualTo(
				new Quote(
						42, BID, ASK, 4_000_000_000L, 250, 7, 10_060.5, Venue.XNAS, MarketState.OPEN,
						EnumSet.of(QuoteFlag.INDICATIVE, QuoteFlag.LOCKED), "ACME", EXPONENT, DEPTH, TRADE,
						List.of(new Contributor(Venue.XNAS, BID, ASK, 4_000_000_000L, 250, null)), null
				)
		);
		int written = com.example.quotes.xmlref.v7.MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength();
		assertThat(codec.lastDecodedLength()).isEqualTo(written);
		assertThat(codec.decodedLength(buffer, OFFSET)).isEqualTo(written);
	}

	@Test
	void aVersionSevenReaderReadsACurrentMessagesGroupAndStopsBeforeTheRemark() {
		QuoteCodec codec = new QuoteCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
		int written = codec.encode(QUOTE, buffer, OFFSET);

		com.example.quotes.xmlref.v7.QuoteDecoder decoder = new com.example.quotes.xmlref.v7.QuoteDecoder()
				.wrapAndApplyHeader(buffer, OFFSET, new com.example.quotes.xmlref.v7.MessageHeaderDecoder());

		assertThat(decoder.actingVersion()).isEqualTo(9);
		com.example.quotes.xmlref.v7.QuoteDecoder.ContributorsDecoder contributors = decoder.contributors();
		assertThat(contributors.count()).isEqualTo(2);
		assertThat(contributors.next().bid()).isEqualTo(10_050);
		assertThat(contributors.next().bid()).isEqualTo(10_025);
		// The group is consumed whole; the remark behind it is beyond what a reader
		// without it can skip, which is SBE's limit.
		int remark = QuoteEncoder.remarkHeaderLength() + REMARK.getBytes(StandardCharsets.UTF_8).length;
		assertThat(com.example.quotes.xmlref.v7.MessageHeaderDecoder.ENCODED_LENGTH + decoder.encodedLength())
				.isEqualTo(written - remark);
	}

	@Test
	void aVersionSixMessageDecodesWithoutContributors() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
		com.example.quotes.xmlref.v6.QuoteEncoder encoder = new com.example.quotes.xmlref.v6.QuoteEncoder()
				.wrapAndApplyHeader(buffer, OFFSET, new com.example.quotes.xmlref.v6.MessageHeaderEncoder());
		encoder.instrumentId(42).bid(10_050).ask(10_075).bidSize(4_000_000_000L).askSize(250).sequence(7)
				.vwap(10_060.5).venue(com.example.quotes.xmlref.v6.Venue.XNAS)
				.state(com.example.quotes.xmlref.v6.MarketState.OPEN);
		encoder.flags().indicative(true).locked(true);
		encoder.symbol("ACME").bidDepth(0, 4_000_000_000L).bidDepth(1, 900).bidDepth(2, 800).bidDepth(3, 700)
				.bidDepth(4, 600);
		encoder.lastTrade().price(10_060).size(300);
		QuoteCodec codec = new QuoteCodec();

		Quote decoded = codec.decode(buffer, OFFSET);

		assertThat(decoded).usingRecursiveComparison().isEqualTo(
				new Quote(
						42, BID, ASK, 4_000_000_000L, 250, 7, 10_060.5, Venue.XNAS, MarketState.OPEN,
						EnumSet.of(QuoteFlag.INDICATIVE, QuoteFlag.LOCKED), "ACME", EXPONENT, DEPTH, TRADE, null, null
				)
		);
		int shorter = com.example.quotes.xmlref.v6.MessageHeaderEncoder.ENCODED_LENGTH
				+ com.example.quotes.xmlref.v6.QuoteEncoder.BLOCK_LENGTH;
		assertThat(shorter).isLessThan(codec.encodedLength(QUOTE));
		assertThat(codec.lastDecodedLength()).isEqualTo(shorter);
		assertThat(codec.decodedLength(buffer, OFFSET)).isEqualTo(shorter);
	}

	@Test
	void aVersionSixReaderReadsACurrentMessagesBlockAndStopsBeforeTheGroup() {
		QuoteCodec codec = new QuoteCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
		int written = codec.encode(QUOTE, buffer, OFFSET);

		com.example.quotes.xmlref.v6.QuoteDecoder decoder = new com.example.quotes.xmlref.v6.QuoteDecoder()
				.wrapAndApplyHeader(buffer, OFFSET, new com.example.quotes.xmlref.v6.MessageHeaderDecoder());

		assertThat(decoder.symbol()).isEqualTo("ACME");
		assertThat(decoder.lastTrade().price()).isEqualTo(10_060);
		assertThat(decoder.actingVersion()).isEqualTo(9);
		// The block is consumed whole; the group behind it is beyond what a reader
		// without it can skip, which is SBE's limit.
		assertThat(com.example.quotes.xmlref.v6.MessageHeaderDecoder.ENCODED_LENGTH + decoder.encodedLength())
				.isEqualTo(BLOCK)
				.isLessThan(written);
	}

	@Test
	void aVersionFiveMessageDecodesWithoutALastTrade() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
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
						EnumSet.of(QuoteFlag.INDICATIVE, QuoteFlag.LOCKED), "ACME", EXPONENT, DEPTH, null, null, null
				)
		);
		int shorter = com.example.quotes.xmlref.v5.MessageHeaderEncoder.ENCODED_LENGTH
				+ com.example.quotes.xmlref.v5.QuoteEncoder.BLOCK_LENGTH;
		assertThat(shorter).isLessThan(codec.encodedLength(QUOTE));
		assertThat(codec.lastDecodedLength()).isEqualTo(shorter);
	}

	@Test
	void aVersionFiveReaderReadsACurrentMessagesBlock() {
		QuoteCodec codec = new QuoteCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
		int written = codec.encode(QUOTE, buffer, OFFSET);

		com.example.quotes.xmlref.v5.QuoteDecoder decoder = new com.example.quotes.xmlref.v5.QuoteDecoder()
				.wrapAndApplyHeader(buffer, OFFSET, new com.example.quotes.xmlref.v5.MessageHeaderDecoder());

		assertThat(decoder.symbol()).isEqualTo("ACME");
		assertThat(decoder.actingVersion()).isEqualTo(9);
		assertThat(com.example.quotes.xmlref.v5.MessageHeaderDecoder.ENCODED_LENGTH + decoder.encodedLength())
				.isEqualTo(BLOCK)
				.isLessThan(written);
	}

	@Test
	void aVersionFourMessageDecodesWithTheConstantAndItsArraysAbsent() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
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
						EnumSet.of(QuoteFlag.INDICATIVE, QuoteFlag.LOCKED), null, EXPONENT, null, null, null, null
				)
		);
		int shorter = com.example.quotes.xmlref.v4.MessageHeaderEncoder.ENCODED_LENGTH
				+ com.example.quotes.xmlref.v4.QuoteEncoder.BLOCK_LENGTH;
		assertThat(shorter).isLessThan(codec.encodedLength(QUOTE));
		assertThat(codec.lastDecodedLength()).isEqualTo(shorter);
	}

	@Test
	void aVersionFourReaderReadsACurrentMessagesBlock() {
		QuoteCodec codec = new QuoteCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
		int written = codec.encode(QUOTE, buffer, OFFSET);

		com.example.quotes.xmlref.v4.QuoteDecoder decoder = new com.example.quotes.xmlref.v4.QuoteDecoder()
				.wrapAndApplyHeader(buffer, OFFSET, new com.example.quotes.xmlref.v4.MessageHeaderDecoder());

		assertThat(decoder.tradeCount()).isEqualTo(com.example.quotes.xmlref.v4.QuoteDecoder.tradeCountNullValue());
		assertThat(decoder.venue()).isEqualTo(com.example.quotes.xmlref.v4.Venue.XNAS);
		assertThat(decoder.actingVersion()).isEqualTo(9);
		assertThat(com.example.quotes.xmlref.v4.MessageHeaderDecoder.ENCODED_LENGTH + decoder.encodedLength())
				.isEqualTo(BLOCK)
				.isLessThan(written);
	}

	@Test
	void aVersionThreeMessageWithATradeCountDecodesWithoutIt() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
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
						EnumSet.of(QuoteFlag.INDICATIVE, QuoteFlag.LOCKED), null, EXPONENT, null, null, null, null
				)
		);
		assertThat(codec.lastDecodedLength()).isLessThan(codec.encodedLength(QUOTE));
	}

	@Test
	void aVersionThreeReaderReadsACurrentMessagesBlock() {
		QuoteCodec codec = new QuoteCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
		int written = codec.encode(QUOTE, buffer, OFFSET);

		com.example.quotes.xmlref.v3.QuoteDecoder decoder = new com.example.quotes.xmlref.v3.QuoteDecoder()
				.wrapAndApplyHeader(buffer, OFFSET, new com.example.quotes.xmlref.v3.MessageHeaderDecoder());

		assertThat(decoder.tradeCount()).isEqualTo(com.example.quotes.xmlref.v3.QuoteDecoder.tradeCountNullValue());
		assertThat(decoder.venue()).isEqualTo(com.example.quotes.xmlref.v3.Venue.XNAS);
		assertThat(decoder.actingVersion()).isEqualTo(9);
		assertThat(com.example.quotes.xmlref.v3.MessageHeaderDecoder.ENCODED_LENGTH + decoder.encodedLength())
				.isEqualTo(BLOCK)
				.isLessThan(written);
	}

	@Test
	void aVersionTwoMessageDecodesWithItsLaterFieldsAbsent() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
		com.example.quotes.xmlref.v2.QuoteEncoder encoder = new com.example.quotes.xmlref.v2.QuoteEncoder()
				.wrapAndApplyHeader(buffer, OFFSET, new com.example.quotes.xmlref.v2.MessageHeaderEncoder());
		encoder.instrumentId(42).bid(10_050).ask(10_075).bidSize(4_000_000_000L).askSize(250).sequence(7)
				.tradeCount(3).vwap(10_060.5);
		QuoteCodec codec = new QuoteCodec();

		Quote decoded = codec.decode(buffer, OFFSET);

		assertThat(decoded).isEqualTo(
				new Quote(
						42, BID, ASK, 4_000_000_000L, 250, 7, 10_060.5, null, null, null, null, EXPONENT, null, null,
						null, null
				)
		);
		int shorter = com.example.quotes.xmlref.v2.MessageHeaderEncoder.ENCODED_LENGTH
				+ com.example.quotes.xmlref.v2.QuoteEncoder.BLOCK_LENGTH;
		assertThat(shorter).isLessThan(codec.encodedLength(QUOTE));
		assertThat(codec.lastDecodedLength()).isEqualTo(shorter);
	}

	@Test
	void aVersionOneMessageDecodesWithItsLaterFieldsAbsent() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
		com.example.quotes.xmlref.v1.QuoteEncoder encoder = new com.example.quotes.xmlref.v1.QuoteEncoder()
				.wrapAndApplyHeader(buffer, OFFSET, new com.example.quotes.xmlref.v1.MessageHeaderEncoder());
		encoder.instrumentId(42).bid(10_050).ask(10_075).bidSize(4_000_000_000L).askSize(250).sequence(7);
		QuoteCodec codec = new QuoteCodec();

		Quote decoded = codec.decode(buffer, OFFSET);

		assertThat(decoded).isEqualTo(
				new Quote(
						42, BID, ASK, 4_000_000_000L, 250, 7, null, null, null, null, null, EXPONENT, null, null, null,
						null
				)
		);
		int shorter = com.example.quotes.xmlref.v1.MessageHeaderEncoder.ENCODED_LENGTH
				+ com.example.quotes.xmlref.v1.QuoteEncoder.BLOCK_LENGTH;
		assertThat(codec.lastDecodedLength()).isEqualTo(shorter);
		assertThat(codec.decodedLength(buffer, OFFSET)).isEqualTo(shorter);
	}

	@Test
	void aVersionOneReaderReadsACurrentMessagesBlock() {
		QuoteCodec codec = new QuoteCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
		int written = codec.encode(QUOTE, buffer, OFFSET);

		com.example.quotes.xmlref.v1.QuoteDecoder decoder = new com.example.quotes.xmlref.v1.QuoteDecoder()
				.wrapAndApplyHeader(buffer, OFFSET, new com.example.quotes.xmlref.v1.MessageHeaderDecoder());

		assertThat(decoder.instrumentId()).isEqualTo(42);
		assertThat(decoder.sequence()).isEqualTo(7);
		assertThat(decoder.actingVersion()).isEqualTo(9);
		// The acting block length is the header's, so the longer block is consumed
		// whole; the group behind it is not.
		assertThat(com.example.quotes.xmlref.v1.MessageHeaderDecoder.ENCODED_LENGTH + decoder.encodedLength())
				.isEqualTo(BLOCK)
				.isLessThan(written);
	}

	@Test
	void aVersionZeroMessageIsBelowTheBaselineAndRefused() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
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
