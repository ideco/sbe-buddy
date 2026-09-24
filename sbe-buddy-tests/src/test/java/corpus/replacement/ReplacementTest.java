package corpus.replacement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.Codec;
import net.concini.sbebuddy.tests.SchemaCase;

/**
 * The replacement schema at version 1, crossed with its version 0: a new
 * template over a new composite replaces a message whose composite had to grow.
 * The version 0 reader tells the new template from its own and refuses it, the
 * current reader takes both, and the current writer still sends the old
 * template to old readers.
 */
final class ReplacementTest implements SchemaCase {

	static final String ORACLE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.replacement" id="7" version="1">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			        <composite name="Price">
			            <type name="mantissa" primitiveType="int64"/>
			            <type name="exponent" primitiveType="int8"/>
			        </composite>
			        <composite name="Level">
			            <type name="mantissa" primitiveType="int64"/>
			            <type name="exponent" primitiveType="int8"/>
			            <type name="quantity" primitiveType="int32"/>
			        </composite>
			    </types>
			    <sbe:message name="Quote" id="1">
			        <field name="instrumentId" id="1" type="int64"/>
			        <field name="bid" id="2" type="Price"/>
			    </sbe:message>
			    <sbe:message name="QuoteV2" id="2" sinceVersion="1">
			        <field name="instrumentId" id="1" type="int64"/>
			        <field name="bid" id="2" type="Level"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private static final int OFFSET = 8;

	private static final Quote QUOTE = new Quote(5L, new Price(10_125L, (byte) -2));

	private static final QuoteV2 QUOTE_V2 = new QuoteV2(5L, new Level(10_125L, (byte) -2, 300));

	// The version 0 types share their names with this version's, so they are
	// written qualified.
	private static final corpus.replacement.v0.Quote VERSION_0_QUOTE = new corpus.replacement.v0.Quote(
			5L, new corpus.replacement.v0.Price(10_125L, (byte) -2)
	);

	@Override
	public String description() {
		return "Replacement: a message replaced by a new template over a new composite, "
				+ "both in one union, crossed with its version 0";
	}

	@Override
	public String oracle() {
		return ORACLE;
	}

	@Override
	public List<RoundTrip<?>> roundTrips() {
		return List.of(
				new RoundTrip<>("the old quote through its own codec", new QuoteCodec(), QUOTE),
				new RoundTrip<>("the new quote through its own codec", new QuoteV2Codec(), QUOTE_V2),
				new RoundTrip<>("the old quote through the union", new QuoteUpdateCodec(), QUOTE),
				new RoundTrip<>("the new quote through the union", new QuoteUpdateCodec(), QUOTE_V2)
		);
	}

	@Test
	void aVersion0ReaderTellsTheNewTemplateFromItsOwn() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		new QuoteUpdateCodec().encode(QUOTE_V2, buffer, OFFSET);
		corpus.replacement.v0.QuoteUpdateCodec reader = new corpus.replacement.v0.QuoteUpdateCodec();

		assertThat(reader.canDecode(buffer, OFFSET)).isFalse();
		assertThat(new corpus.replacement.v0.QuoteCodec().canDecode(buffer, OFFSET)).isFalse();
		assertThatThrownBy(() -> reader.decode(buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("not a QuoteUpdate: schemaId 7, templateId 2; its templates are 1");
	}

	@Test
	void aVersion0ReaderReadsTheOldTemplateTheCurrentWriterSends() {
		assertThat(cross(new QuoteUpdateCodec(), QUOTE, new corpus.replacement.v0.QuoteUpdateCodec()))
				.isEqualTo(VERSION_0_QUOTE);
	}

	@Test
	void theCurrentReaderReadsAVersion0Message() {
		assertThat(cross(new corpus.replacement.v0.QuoteUpdateCodec(), VERSION_0_QUOTE, new QuoteUpdateCodec()))
				.isEqualTo(QUOTE);
	}

	@Test
	void aCallerSwitchesOverBothQuotes() {
		List<Long> quantities = List.<QuoteUpdate>of(QUOTE, QUOTE_V2).stream()
				.map(update -> switch (update) {
					case Quote quote -> 0L;
					case QuoteV2 quote -> (long) quote.bid().quantity();
				})
				.toList();

		assertThat(quantities).containsExactly(0L, 300L);
	}

	/**
	 * One version's message read by another's codec, which takes it and consumes
	 * exactly what was written.
	 */
	private static <W, R> R cross(Codec<W, ?> writer, W value, Codec<R, ?> reader) {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		int length = writer.encode(value, buffer, OFFSET);

		assertThat(reader.canDecode(buffer, OFFSET)).as("canDecode").isTrue();
		assertThat(reader.decodedLength(buffer, OFFSET)).as("decodedLength").isEqualTo(length);
		R read = reader.decode(buffer, OFFSET);
		assertThat(reader.lastDecodedLength()).as("lastDecodedLength").isEqualTo(length);
		return read;
	}
}
