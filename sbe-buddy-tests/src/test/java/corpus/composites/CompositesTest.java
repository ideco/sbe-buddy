package corpus.composites;

import static net.concini.sbebuddy.tests.WriterAssert.assertWritesTheCodecsBytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.tests.SchemaCase;

import corpus.composites.Quote.Flags;
import corpus.composites.Quote.Side;
import corpus.composites.Quote.Stamp;
import corpus.composites.sbe.CompositesEncoder;
import corpus.composites.sbe.MessageHeaderEncoder;
import corpus.composites.sbe.QuoteEncoder;

/**
 * Composites through the codec: the ref, the inline enum, set and composite,
 * the optional and the constant member, and the binding over the face record.
 * The round trips carry the edge values; the tests hold what the codec refuses,
 * on the way in and on the way out.
 */
final class CompositesTest implements SchemaCase {

	static final String ORACLE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.composites" id="1" version="0">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			        <composite name="Decimal" semanticType="Price" description="A price as mantissa and exponent">
			            <type name="mantissa" primitiveType="int64"/>
			            <type name="exponent" primitiveType="int8" offset="8"/>
			        </composite>
			        <composite name="Quote">
			            <ref name="bid" type="Decimal"/>
			            <ref name="ask" type="Decimal" offset="9"/>
			            <enum name="side" encodingType="char" offset="18">
			                <validValue name="Buy">B</validValue>
			                <validValue name="Sell">S</validValue>
			            </enum>
			            <set name="flags" encodingType="uint8" offset="19">
			                <choice name="firm">0</choice>
			            </set>
			            <composite name="stamp" offset="20" description="When the quote was made">
			                <type name="time" primitiveType="uint64"/>
			                <type name="precision" primitiveType="uint8" presence="optional" nullValue="255"/>
			                <type name="zone" primitiveType="char" presence="constant">Z</type>
			            </composite>
			        </composite>
			    </types>
			    <sbe:message name="Composites" id="1">
			        <field name="quote" id="1" type="Quote"/>
			        <field name="last" id="2" type="Decimal"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private static final int OFFSET = 8;

	private static final Quote QUOTE = new Quote(
			new Decimal(10_125, (byte) -2), new Decimal(10_150, (byte) -2), Side.Buy, EnumSet.of(Flags.firm),
			new Stamp(1_700_000_000_000L, (short) 3, (byte) 'Z')
	);

	private static final BigDecimal LAST = new BigDecimal("101.25");

	@Override
	public String description() {
		return "Composites: a ref, inline declarations, optional and constant members, a binding over the record";
	}

	@Override
	public String oracle() {
		return ORACLE;
	}

	@Override
	public List<RoundTrip<?>> roundTrips() {
		return List.of(
				new RoundTrip<>(
						"every member set, and a last price with two decimals", new CompositesCodec(),
						new Composites(QUOTE, LAST)
				),
				new RoundTrip<>(
						"an absent precision, no flags, and the mantissa and the exponent at their bounds",
						new CompositesCodec(),
						new Composites(
								new Quote(
										new Decimal(Long.MIN_VALUE, Byte.MIN_VALUE),
										new Decimal(Long.MAX_VALUE, Byte.MAX_VALUE),
										Side.Sell, EnumSet.noneOf(Flags.class), new Stamp(0, null, (byte) 'Z')
								),
								BigDecimal.valueOf(Long.MIN_VALUE, -Byte.MIN_VALUE)
						)
				),
				new RoundTrip<>(
						"a time with uint64's top bit set, which the long face carries as negative, and a precision at the byte before null",
						new CompositesCodec(),
						new Composites(
								new Quote(
										QUOTE.bid(), QUOTE.ask(), Side.Buy, EnumSet.of(Flags.firm),
										new Stamp(-1L, (short) 254, (byte) 'Z')
								),
								BigDecimal.ZERO
						)
				)
		);
	}

	@Test
	void aNullComponentOfTheMessageIsRefused() {
		CompositesCodec codec = new CompositesCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);

		assertThatThrownBy(() -> codec.encode(new Composites(null, LAST), buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("quote is required");
		assertThatThrownBy(() -> codec.encode(new Composites(QUOTE, null), buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("last is required");
	}

	@Test
	void aNullMemberOfTheCompositeIsRefused() {
		CompositesCodec codec = new CompositesCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		Quote quote = new Quote(QUOTE.bid(), QUOTE.ask(), null, QUOTE.flags(), QUOTE.stamp());

		assertThatThrownBy(() -> codec.encode(new Composites(quote, LAST), buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("side is required");
	}

	@Test
	void aConstantMemberMustHoldTheConstant() {
		CompositesCodec codec = new CompositesCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		Stamp stamp = new Stamp(QUOTE.stamp().time(), QUOTE.stamp().precision(), (byte) 'X');
		Quote quote = new Quote(QUOTE.bid(), QUOTE.ask(), QUOTE.side(), QUOTE.flags(), stamp);

		assertThatThrownBy(() -> codec.encode(new Composites(quote, LAST), buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("zone is the constant " + (byte) 'Z');
	}

	@Test
	void aSideTheSchemaDoesNotKnowIsRefusedOnDecode() {
		CompositesCodec codec = new CompositesCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		codec.encode(new Composites(QUOTE, LAST), buffer, OFFSET);
		buffer.putByte(memberOffset(QuoteEncoder.sideEncodingOffset()), (byte) 'X');

		assertThatThrownBy(() -> codec.decode(buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Side has no value " + (byte) 'X');
	}

	@Test
	void aFlagBitNoChoiceNamesIsRefusedOnDecode() {
		CompositesCodec codec = new CompositesCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		codec.encode(new Composites(QUOTE, LAST), buffer, OFFSET);
		buffer.putByte(memberOffset(QuoteEncoder.flagsEncodingOffset()), (byte) 0b10);

		assertThatThrownBy(() -> codec.decode(buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Flags has a bit no choice names: 2");
	}

	@Test
	void anotherTemplateIsRefused() {
		CompositesCodec codec = new CompositesCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		codec.encode(new Composites(QUOTE, LAST), buffer, OFFSET);
		new MessageHeaderEncoder().wrap(buffer, OFFSET).templateId(2);

		assertThatThrownBy(() -> codec.decode(buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("not a Composites: schemaId 1, templateId 2");
	}

	@Test
	void theBindingsOwnExceptionPassesThrough() {
		CompositesCodec codec = new CompositesCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		Composites beyondTheMantissa = new Composites(QUOTE, new BigDecimal("12345678901234567890"));

		assertThatThrownBy(() -> codec.encode(beyondTheMantissa, buffer, OFFSET))
				.isInstanceOf(ArithmeticException.class);
		assertThat(codec.encodedLength(beyondTheMantissa)).isEqualTo(codec.encodedLength(new Composites(QUOTE, LAST)));
	}

	/**
	 * Where a member of the quote lies in the buffer, by the flyweights' own
	 * offsets.
	 */
	private static int memberOffset(int memberEncodingOffset) {
		return OFFSET + MessageHeaderEncoder.ENCODED_LENGTH + CompositesEncoder.quoteEncodingOffset()
				+ memberEncodingOffset;
	}

	/**
	 * A composite is a chain of its members, a ref's and a nested composite's their
	 * own, the constant left out; the set its choices, then its end.
	 */
	@Test
	void theWriterWritesTheCodecsBytesThroughEveryMember() {
		assertWritesTheCodecsBytes(
				new CompositesCodec(), new Composites(QUOTE, LAST),
				(buffer, offset) -> new CompositesWriter().wrap(buffer, offset)
						.quote()
						.bid().mantissa(10_125).exponent((byte) -2)
						.ask().mantissa(10_150).exponent((byte) -2)
						.side(corpus.composites.sbe.Side.Buy)
						.flags().firm(true).end()
						.stamp().time(1_700_000_000_000L).precision((short) 3)
						.last().mantissa(10_125).exponent((byte) -2)
						.length()
		);
	}

	@Test
	void anOptionalMemberIsSetNullByItsOwnStep() {
		Quote quote = new Quote(
				QUOTE.bid(), QUOTE.ask(), Side.Buy, EnumSet.of(Flags.firm), new Stamp(0, null, (byte) 'Z')
		);

		assertWritesTheCodecsBytes(
				new CompositesCodec(), new Composites(quote, LAST),
				(buffer, offset) -> new CompositesWriter().wrap(buffer, offset)
						.quote()
						.bid().mantissa(10_125).exponent((byte) -2)
						.ask().mantissa(10_150).exponent((byte) -2)
						.side(corpus.composites.sbe.Side.Buy)
						.flags().firm(true).end()
						.stamp().time(0).precisionNull()
						.last().mantissa(10_125).exponent((byte) -2)
						.length()
		);
	}
}
