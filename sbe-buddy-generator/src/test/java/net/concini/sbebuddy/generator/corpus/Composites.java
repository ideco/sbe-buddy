package net.concini.sbebuddy.generator.corpus;

import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.BYTE;
import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.LONG;
import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.SHORT;
import static net.concini.sbebuddy.generator.Fixtures.annotatedChoice;
import static net.concini.sbebuddy.generator.Fixtures.annotatedComposite;
import static net.concini.sbebuddy.generator.Fixtures.annotatedEnum;
import static net.concini.sbebuddy.generator.Fixtures.annotatedEnumValue;
import static net.concini.sbebuddy.generator.Fixtures.annotatedField;
import static net.concini.sbebuddy.generator.Fixtures.annotatedMessage;
import static net.concini.sbebuddy.generator.Fixtures.annotatedRef;
import static net.concini.sbebuddy.generator.Fixtures.annotatedSchema;
import static net.concini.sbebuddy.generator.Fixtures.annotatedSet;
import static net.concini.sbebuddy.generator.Fixtures.annotatedType;
import static net.concini.sbebuddy.generator.Fixtures.boxed;
import static net.concini.sbebuddy.generator.Fixtures.choice;
import static net.concini.sbebuddy.generator.Fixtures.composite;
import static net.concini.sbebuddy.generator.Fixtures.declared;
import static net.concini.sbebuddy.generator.Fixtures.enumeration;
import static net.concini.sbebuddy.generator.Fixtures.field;
import static net.concini.sbebuddy.generator.Fixtures.message;
import static net.concini.sbebuddy.generator.Fixtures.messageHeader;
import static net.concini.sbebuddy.generator.Fixtures.messageSchema;
import static net.concini.sbebuddy.generator.Fixtures.other;
import static net.concini.sbebuddy.generator.Fixtures.primitive;
import static net.concini.sbebuddy.generator.Fixtures.ref;
import static net.concini.sbebuddy.generator.Fixtures.set;
import static net.concini.sbebuddy.generator.Fixtures.type;
import static net.concini.sbebuddy.generator.Fixtures.validValue;
import static uk.co.real_logic.sbe.PrimitiveType.CHAR;
import static uk.co.real_logic.sbe.PrimitiveType.INT64;
import static uk.co.real_logic.sbe.PrimitiveType.INT8;
import static uk.co.real_logic.sbe.PrimitiveType.UINT64;
import static uk.co.real_logic.sbe.PrimitiveType.UINT8;
import static uk.co.real_logic.sbe.xml.Presence.CONSTANT;
import static uk.co.real_logic.sbe.xml.Presence.OPTIONAL;

import net.concini.sbebuddy.generator.Annotated;
import net.concini.sbebuddy.generator.Fixtures.AnnotatedCompositeBuilder;
import net.concini.sbebuddy.generator.Schema;

/**
 * Everything a composite may hold: an inline type, an inline enum and set, a
 * nested composite with an optional and a constant member, and a ref to a type
 * declared at the top level, which is the only place a ref resolves. Offsets
 * run in declaration order. A second field binds the same composite to a
 * BigDecimal through the face record.
 */
final class Composites {

	static final String PACKAGE_INFO = """
			@SbeSchema(id = 1, version = 0)
			package corpus.composites;

			import net.concini.sbebuddy.SbeSchema;
			""";

	static final String SOURCE = """
			package corpus.composites;

			import static net.concini.sbebuddy.Presence.CONSTANT;
			import static net.concini.sbebuddy.Presence.OPTIONAL;
			import static net.concini.sbebuddy.PrimitiveType.CHAR;
			import static net.concini.sbebuddy.PrimitiveType.INT64;
			import static net.concini.sbebuddy.PrimitiveType.INT8;
			import static net.concini.sbebuddy.PrimitiveType.UINT64;
			import static net.concini.sbebuddy.PrimitiveType.UINT8;

			import java.math.BigDecimal;

			import net.concini.sbebuddy.SbeChoice;
			import net.concini.sbebuddy.SbeComposite;
			import net.concini.sbebuddy.SbeEnum;
			import net.concini.sbebuddy.SbeEnumValue;
			import net.concini.sbebuddy.SbeField;
			import net.concini.sbebuddy.SbeMessage;
			import net.concini.sbebuddy.SbeRef;
			import net.concini.sbebuddy.SbeSet;
			import net.concini.sbebuddy.SbeType;
			import net.concini.sbebuddy.TypeBinding;

			@SbeComposite(semanticType = "Price", description = "A price as mantissa and exponent")
			record Decimal(
					@SbeType(primitiveType = INT64) long mantissa,
					@SbeType(primitiveType = INT8, offset = 8) byte exponent
			) {
			}

			final class DecimalBinding implements TypeBinding<BigDecimal, Decimal> {

				public Decimal toWire(BigDecimal value) {
					return new Decimal(value.unscaledValue().longValueExact(), (byte) -value.scale());
				}

				public BigDecimal fromWire(Decimal wire) {
					return BigDecimal.valueOf(wire.mantissa(), -wire.exponent());
				}
			}

			@SbeComposite
			record Quote(
					@SbeRef Decimal bid,
					@SbeRef(offset = 9) Decimal ask,
					Side side,
					Flags flags,
					Stamp stamp
			) {

				@SbeEnum(primitiveType = CHAR, offset = 18)
				enum Side {

					@SbeEnumValue("B")
					Buy,

					@SbeEnumValue("S")
					Sell
				}

				@SbeSet(primitiveType = UINT8, offset = 19)
				enum Flags {

					@SbeChoice(0)
					firm
				}

				@SbeComposite(offset = 20, description = "When the quote was made")
				record Stamp(
						@SbeType(primitiveType = UINT64) long time,
						@SbeType(primitiveType = UINT8, presence = OPTIONAL, nullValue = "255") Short precision,
						@SbeType(primitiveType = CHAR, presence = CONSTANT, value = "Z") byte zone
				) {
				}
			}

			@SbeMessage(id = 1)
			record Composites(
					@SbeField(id = 1) Quote quote,
					@SbeField(id = 2, type = Decimal.class, binding = DecimalBinding.class) BigDecimal last
			) {
			}
			""";

	static final String XML = """
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

	/**
	 * What the codec must be: a write and read pair per composite type, nested ones
	 * and the ref's target first, each member with the shape a field has over the
	 * composite's flyweight, and the bound field through the face record.
	 */
	static final String CODEC = """
			package corpus.composites;

			/** Generated by sbe-buddy from the schema of this package; do not edit. */
			@javax.annotation.processing.Generated("net.concini.sbebuddy")
			public final class CompositesCodec implements net.concini.sbebuddy.Codec<corpus.composites.Composites> {

				private final corpus.composites.sbe.MessageHeaderEncoder headerEncoder = new corpus.composites.sbe.MessageHeaderEncoder();
				private final corpus.composites.sbe.MessageHeaderDecoder headerDecoder = new corpus.composites.sbe.MessageHeaderDecoder();
				private final corpus.composites.sbe.CompositesEncoder encoder = new corpus.composites.sbe.CompositesEncoder();
				private final corpus.composites.sbe.CompositesDecoder decoder = new corpus.composites.sbe.CompositesDecoder();
				private final corpus.composites.DecimalBinding decimalBinding = new corpus.composites.DecimalBinding();
				private int lastDecodedLength;

				public CompositesCodec() {
				}

				@Override
				public int encodedLength(corpus.composites.Composites value) {
					return corpus.composites.sbe.MessageHeaderEncoder.ENCODED_LENGTH + corpus.composites.sbe.CompositesEncoder.BLOCK_LENGTH;
				}

				@Override
				public int encode(corpus.composites.Composites value, org.agrona.MutableDirectBuffer buffer, int offset) {
					encoder.wrapAndApplyHeader(buffer, offset, headerEncoder);
					if (value.quote() == null) {
						throw new IllegalArgumentException("quote is required");
					}
					writeQuote(value.quote(), encoder.quote());
					if (value.last() == null) {
						throw new IllegalArgumentException("last is required");
					}
					writeDecimal(decimalBinding.toWire(value.last()), encoder.last());
					return corpus.composites.sbe.MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength();
				}

				@Override
				public corpus.composites.Composites decode(org.agrona.DirectBuffer buffer, int offset) {
					headerDecoder.wrap(buffer, offset);
					if (headerDecoder.schemaId() != corpus.composites.sbe.CompositesDecoder.SCHEMA_ID
							|| headerDecoder.templateId() != corpus.composites.sbe.CompositesDecoder.TEMPLATE_ID) {
						throw new IllegalArgumentException(
								"not a Composites: schemaId " + headerDecoder.schemaId() + ", templateId " + headerDecoder.templateId());
					}
					decoder.wrap(buffer, offset + corpus.composites.sbe.MessageHeaderDecoder.ENCODED_LENGTH, headerDecoder.blockLength(), headerDecoder.version());
					corpus.composites.Composites value = new corpus.composites.Composites(
							readQuote(decoder.quote()),
							decimalBinding.fromWire(readDecimal(decoder.last()))
					);
					lastDecodedLength = corpus.composites.sbe.MessageHeaderDecoder.ENCODED_LENGTH + decoder.encodedLength();
					return value;
				}

				@Override
				public int lastDecodedLength() {
					return lastDecodedLength;
				}

				@Override
				public int decodedLength(org.agrona.DirectBuffer buffer, int offset) {
					headerDecoder.wrap(buffer, offset);
					return corpus.composites.sbe.MessageHeaderDecoder.ENCODED_LENGTH + headerDecoder.blockLength();
				}

				private static void writeDecimal(corpus.composites.Decimal value, corpus.composites.sbe.DecimalEncoder encoder) {
					encoder.mantissa(value.mantissa());
					encoder.exponent(value.exponent());
				}

				private static corpus.composites.Decimal readDecimal(corpus.composites.sbe.DecimalDecoder decoder) {
					return new corpus.composites.Decimal(
							decoder.mantissa(),
							decoder.exponent()
					);
				}

				private static corpus.composites.sbe.Side encodeSide(corpus.composites.Quote.Side value) {
					return switch (value) {
						case Buy -> corpus.composites.sbe.Side.Buy;
						case Sell -> corpus.composites.sbe.Side.Sell;
					};
				}

				private static corpus.composites.Quote.Side decodeSide(byte raw) {
					return switch (raw) {
						case (byte)66 -> corpus.composites.Quote.Side.Buy;
						case (byte)83 -> corpus.composites.Quote.Side.Sell;
						default -> throw new IllegalArgumentException("Side has no value " + raw);
					};
				}

				private static void encodeFlags(java.util.Set<corpus.composites.Quote.Flags> value, corpus.composites.sbe.FlagsEncoder wire) {
					wire.clear();
					wire.firm(value.contains(corpus.composites.Quote.Flags.firm));
				}

				private static java.util.Set<corpus.composites.Quote.Flags> decodeFlags(corpus.composites.sbe.FlagsDecoder wire) {
					if ((wire.getRaw() & ~(1L << 0)) != 0) {
						throw new IllegalArgumentException("Flags has a bit no choice names: " + wire.getRaw());
					}
					java.util.Set<corpus.composites.Quote.Flags> value = java.util.EnumSet.noneOf(corpus.composites.Quote.Flags.class);
					if (wire.firm()) {
						value.add(corpus.composites.Quote.Flags.firm);
					}
					return value;
				}

				private static void writeStamp(corpus.composites.Quote.Stamp value, corpus.composites.sbe.StampEncoder encoder) {
					encoder.time(value.time());
					encoder.precision(value.precision() == null ? corpus.composites.sbe.StampEncoder.precisionNullValue() : value.precision());
					if (value.zone() != encoder.zone()) {
						throw new IllegalArgumentException("zone is the constant " + encoder.zone());
					}
				}

				private static corpus.composites.Quote.Stamp readStamp(corpus.composites.sbe.StampDecoder decoder) {
					return new corpus.composites.Quote.Stamp(
							decoder.time(),
							decoder.precision() == corpus.composites.sbe.StampDecoder.precisionNullValue() ? null : decoder.precision(),
							decoder.zone()
					);
				}

				private static void writeQuote(corpus.composites.Quote value, corpus.composites.sbe.QuoteEncoder encoder) {
					if (value.bid() == null) {
						throw new IllegalArgumentException("bid is required");
					}
					writeDecimal(value.bid(), encoder.bid());
					if (value.ask() == null) {
						throw new IllegalArgumentException("ask is required");
					}
					writeDecimal(value.ask(), encoder.ask());
					if (value.side() == null) {
						throw new IllegalArgumentException("side is required");
					}
					encoder.side(encodeSide(value.side()));
					if (value.flags() == null) {
						throw new IllegalArgumentException("flags is required");
					}
					encodeFlags(value.flags(), encoder.flags());
					if (value.stamp() == null) {
						throw new IllegalArgumentException("stamp is required");
					}
					writeStamp(value.stamp(), encoder.stamp());
				}

				private static corpus.composites.Quote readQuote(corpus.composites.sbe.QuoteDecoder decoder) {
					return new corpus.composites.Quote(
							readDecimal(decoder.bid()),
							readDecimal(decoder.ask()),
							decodeSide(decoder.sideRaw()),
							decodeFlags(decoder.flags()),
							readStamp(decoder.stamp())
					);
				}
			}
			""";

	private Composites() {
	}

	static Schema schema() {
		return messageSchema("corpus.composites", 1, 0)
				.types(
						messageHeader(),
						composite("Decimal")
								.semanticType("Price")
								.description("A price as mantissa and exponent")
								.members(
										type("mantissa", INT64),
										type("exponent", INT8).offset(8)
								),
						composite("Quote").members(
								ref("bid", "Decimal"),
								ref("ask", "Decimal").offset(9),
								enumeration("side", "char")
										.offset(18)
										.validValues(
												validValue("Buy", "B"),
												validValue("Sell", "S")
										),
								set("flags", "uint8").offset(19).choices(choice("firm", 0)),
								composite("stamp")
										.offset(20)
										.description("When the quote was made")
										.members(
												type("time", UINT64),
												type("precision", UINT8).presence(OPTIONAL).nullValue("255"),
												type("zone", CHAR).presence(CONSTANT).value("Z")
										)
						)
				)
				.messages(
						message("Composites", 1).fields(field("quote", 1, "Quote"), field("last", 2, "Decimal"))
				)
				.build();
	}

	static Annotated annotated() {
		AnnotatedCompositeBuilder decimal = annotatedComposite("Decimal")
				.qualifiedName("corpus.composites.Decimal")
				.semanticType("Price")
				.description("A price as mantissa and exponent")
				.members(
						annotatedType("mantissa", INT64).javaType(primitive(LONG)),
						annotatedType("exponent", INT8).offset(8).javaType(primitive(BYTE))
				);
		AnnotatedCompositeBuilder quote = annotatedComposite("Quote").qualifiedName("corpus.composites.Quote").members(
				annotatedRef("bid", declared(decimal)),
				annotatedRef("ask", declared(decimal)).offset(9),
				annotatedEnum("side")
						.qualifiedName("corpus.composites.Quote.Side")
						.primitiveType(CHAR)
						.offset(18)
						.values(
								annotatedEnumValue("Buy", "B"),
								annotatedEnumValue("Sell", "S")
						),
				annotatedSet("flags").qualifiedName("corpus.composites.Quote.Flags").primitiveType(UINT8).offset(19)
						.choices(annotatedChoice("firm", 0)),
				annotatedComposite("stamp")
						.qualifiedName("corpus.composites.Quote.Stamp")
						.offset(20)
						.description("When the quote was made")
						.members(
								annotatedType("time", UINT64).javaType(primitive(LONG)),
								annotatedType("precision", UINT8).presence(OPTIONAL).nullValue("255")
										.javaType(boxed(SHORT)),
								annotatedType("zone", CHAR).presence(CONSTANT).value("Z").javaType(primitive(BYTE))
						)
		);
		return annotatedSchema("corpus.composites", 1, 0)
				.types(decimal, quote)
				.messages(
						annotatedMessage("Composites", 1).components(
								annotatedField("quote", 1, declared(quote)),
								annotatedField("last", 2, other("java.math.BigDecimal")).type(decimal)
										.binding("corpus.composites.DecimalBinding", declared(decimal))
						)
				)
				.build();
	}
}
