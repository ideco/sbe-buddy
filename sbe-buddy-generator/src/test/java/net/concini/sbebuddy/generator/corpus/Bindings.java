package net.concini.sbebuddy.generator.corpus;

import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.LONG;
import static net.concini.sbebuddy.generator.Fixtures.annotatedField;
import static net.concini.sbebuddy.generator.Fixtures.annotatedMessage;
import static net.concini.sbebuddy.generator.Fixtures.annotatedSchema;
import static net.concini.sbebuddy.generator.Fixtures.annotatedType;
import static net.concini.sbebuddy.generator.Fixtures.boxed;
import static net.concini.sbebuddy.generator.Fixtures.bytes;
import static net.concini.sbebuddy.generator.Fixtures.field;
import static net.concini.sbebuddy.generator.Fixtures.message;
import static net.concini.sbebuddy.generator.Fixtures.messageHeader;
import static net.concini.sbebuddy.generator.Fixtures.messageSchema;
import static net.concini.sbebuddy.generator.Fixtures.other;
import static net.concini.sbebuddy.generator.Fixtures.text;
import static net.concini.sbebuddy.generator.Fixtures.type;
import static uk.co.real_logic.sbe.PrimitiveType.CHAR;
import static uk.co.real_logic.sbe.PrimitiveType.INT64;
import static uk.co.real_logic.sbe.PrimitiveType.UINT8;
import static uk.co.real_logic.sbe.xml.Presence.OPTIONAL;

import net.concini.sbebuddy.generator.Annotated;
import net.concini.sbebuddy.generator.Fixtures.AnnotatedTypeBuilder;
import net.concini.sbebuddy.generator.Schema;

/**
 * Bindings between a record's own types and the wire's faces: a binding shared
 * by a named type and a bare primitive, one over an optional field, one to a
 * wrapper record over a string and one to a record over a byte array. The
 * oracle is what the same schema writes without any of them.
 */
final class Bindings {

	static final String PACKAGE_INFO = """
			@SbeSchema(id = 1, version = 0)
			package corpus.bindings;

			import net.concini.sbebuddy.SbeSchema;
			""";

	static final String SOURCE = """
			package corpus.bindings;

			import static net.concini.sbebuddy.Presence.OPTIONAL;
			import static net.concini.sbebuddy.PrimitiveType.CHAR;
			import static net.concini.sbebuddy.PrimitiveType.INT64;
			import static net.concini.sbebuddy.PrimitiveType.UINT8;

			import java.math.BigDecimal;

			import net.concini.sbebuddy.SbeField;
			import net.concini.sbebuddy.SbeMessage;
			import net.concini.sbebuddy.SbeType;
			import net.concini.sbebuddy.TypeBinding;

			@SbeType(primitiveType = INT64)
			final class Cents {
			}

			final class CentsBinding implements TypeBinding<BigDecimal, Long> {

				public Long toWire(BigDecimal value) {
					return value.movePointRight(2).longValueExact();
				}

				public BigDecimal fromWire(Long wire) {
					return BigDecimal.valueOf(wire, 2);
				}
			}

			@SbeType(primitiveType = CHAR, length = 6)
			final class Symbol {
			}

			record Ticker(String value) {
			}

			final class TickerBinding implements TypeBinding<Ticker, String> {

				public String toWire(Ticker value) {
					return value.value();
				}

				public Ticker fromWire(String wire) {
					return new Ticker(wire);
				}
			}

			@SbeType(primitiveType = UINT8, length = 3)
			final class Rgb {
			}

			record Colour(int red, int green, int blue) {
			}

			final class ColourBinding implements TypeBinding<Colour, byte[]> {

				public byte[] toWire(Colour value) {
					return new byte[] {(byte) value.red(), (byte) value.green(), (byte) value.blue()};
				}

				public Colour fromWire(byte[] wire) {
					return new Colour(wire[0] & 0xFF, wire[1] & 0xFF, wire[2] & 0xFF);
				}
			}

			@SbeMessage(id = 1)
			record Bindings(
					@SbeField(id = 1, type = Cents.class, binding = CentsBinding.class) BigDecimal price,
					@SbeField(id = 2, primitiveType = INT64, binding = CentsBinding.class) BigDecimal fee,
					@SbeField(id = 3, primitiveType = INT64, presence = OPTIONAL, binding = CentsBinding.class) BigDecimal rebate,
					@SbeField(id = 4, type = Symbol.class, binding = TickerBinding.class) Ticker symbol,
					@SbeField(id = 5, type = Rgb.class, binding = ColourBinding.class) Colour colour
			) {
			}
			""";

	static final String XML = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.bindings" id="1" version="0">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			        <type name="Cents" primitiveType="int64"/>
			        <type name="Rgb" primitiveType="uint8" length="3"/>
			        <type name="Symbol" primitiveType="char" length="6"/>
			    </types>
			    <sbe:message name="Bindings" id="1">
			        <field name="price" id="1" type="Cents"/>
			        <field name="fee" id="2" type="int64"/>
			        <field name="rebate" id="3" type="int64" presence="optional"/>
			        <field name="symbol" id="4" type="Symbol"/>
			        <field name="colour" id="5" type="Rgb"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	/**
	 * What the codec must be: one instance per binding class, the component mapped
	 * before the flyweight and after it, and the null value decided before the
	 * binding is called.
	 */
	static final String CODEC = """
			package corpus.bindings;

			/** Generated by sbe-buddy from the schema of this package; do not edit. */
			@javax.annotation.processing.Generated("net.concini.sbebuddy")
			public final class BindingsCodec implements net.concini.sbebuddy.Codec<corpus.bindings.Bindings> {

				private final corpus.bindings.sbe.MessageHeaderEncoder headerEncoder = new corpus.bindings.sbe.MessageHeaderEncoder();
				private final corpus.bindings.sbe.MessageHeaderDecoder headerDecoder = new corpus.bindings.sbe.MessageHeaderDecoder();
				private final corpus.bindings.sbe.BindingsEncoder encoder = new corpus.bindings.sbe.BindingsEncoder();
				private final corpus.bindings.sbe.BindingsDecoder decoder = new corpus.bindings.sbe.BindingsDecoder();
				private final corpus.bindings.CentsBinding centsBinding = new corpus.bindings.CentsBinding();
				private final corpus.bindings.TickerBinding tickerBinding = new corpus.bindings.TickerBinding();
				private final corpus.bindings.ColourBinding colourBinding = new corpus.bindings.ColourBinding();
				private int lastDecodedLength;

				public BindingsCodec() {
				}

				@Override
				public int encodedLength(corpus.bindings.Bindings value) {
					return corpus.bindings.sbe.MessageHeaderEncoder.ENCODED_LENGTH + corpus.bindings.sbe.BindingsEncoder.BLOCK_LENGTH;
				}

				@Override
				public int encode(corpus.bindings.Bindings value, org.agrona.MutableDirectBuffer buffer, int offset) {
					encoder.wrapAndApplyHeader(buffer, offset, headerEncoder);
					if (value.price() == null) {
						throw new IllegalArgumentException("price is required");
					}
					encoder.price(centsBinding.toWire(value.price()));
					if (value.fee() == null) {
						throw new IllegalArgumentException("fee is required");
					}
					encoder.fee(centsBinding.toWire(value.fee()));
					encoder.rebate(value.rebate() == null ? corpus.bindings.sbe.BindingsEncoder.rebateNullValue() : centsBinding.toWire(value.rebate()));
					if (value.symbol() == null) {
						throw new IllegalArgumentException("symbol is required");
					}
					encoder.symbol(ascii(tickerBinding.toWire(value.symbol()), corpus.bindings.sbe.BindingsEncoder.symbolLength(), "symbol"));
					if (value.colour() == null) {
						throw new IllegalArgumentException("colour is required");
					}
					writeColour(colourBinding.toWire(value.colour()), encoder);
					return corpus.bindings.sbe.MessageHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength();
				}

				@Override
				public corpus.bindings.Bindings decode(org.agrona.DirectBuffer buffer, int offset) {
					headerDecoder.wrap(buffer, offset);
					if (headerDecoder.schemaId() != corpus.bindings.sbe.BindingsDecoder.SCHEMA_ID
							|| headerDecoder.templateId() != corpus.bindings.sbe.BindingsDecoder.TEMPLATE_ID) {
						throw new IllegalArgumentException(
								"not a Bindings: schemaId " + headerDecoder.schemaId() + ", templateId " + headerDecoder.templateId());
					}
					decoder.wrap(buffer, offset + corpus.bindings.sbe.MessageHeaderDecoder.ENCODED_LENGTH, headerDecoder.blockLength(), headerDecoder.version());
					corpus.bindings.Bindings value = new corpus.bindings.Bindings(
							centsBinding.fromWire(decoder.price()),
							centsBinding.fromWire(decoder.fee()),
							decoder.rebate() == corpus.bindings.sbe.BindingsDecoder.rebateNullValue() ? null : centsBinding.fromWire(decoder.rebate()),
							tickerBinding.fromWire(decoder.symbol()),
							colourBinding.fromWire(readColour(decoder))
					);
					lastDecodedLength = corpus.bindings.sbe.MessageHeaderDecoder.ENCODED_LENGTH + decoder.encodedLength();
					return value;
				}

				@Override
				public int lastDecodedLength() {
					return lastDecodedLength;
				}

				@Override
				public int decodedLength(org.agrona.DirectBuffer buffer, int offset) {
					headerDecoder.wrap(buffer, offset);
					return corpus.bindings.sbe.MessageHeaderDecoder.ENCODED_LENGTH + headerDecoder.blockLength();
				}

				private static String ascii(String value, int length, String field) {
					if (value.length() > length) {
						throw new IllegalArgumentException(field + " is longer than " + length + ": " + value);
					}
					for (int i = 0; i < value.length(); i++) {
						if (value.charAt(i) > 127) {
							throw new IllegalArgumentException(field + " is not ASCII: " + value);
						}
					}
					return value;
				}

				private static void writeColour(byte[] value, corpus.bindings.sbe.BindingsEncoder encoder) {
					if (value.length != corpus.bindings.sbe.BindingsEncoder.colourLength()) {
						throw new IllegalArgumentException(
								"colour must be " + corpus.bindings.sbe.BindingsEncoder.colourLength() + " long, not " + value.length);
					}
					encoder.putColour(value, 0, value.length);
				}

				private static byte[] readColour(corpus.bindings.sbe.BindingsDecoder decoder) {
					byte[] value = new byte[corpus.bindings.sbe.BindingsDecoder.colourLength()];
					decoder.getColour(value, 0, value.length);
					return value;
				}
			}
			""";

	private Bindings() {
	}

	static Schema schema() {
		return messageSchema("corpus.bindings", 1, 0)
				.types(
						messageHeader(),
						type("Cents", INT64),
						type("Rgb", UINT8).length(3),
						type("Symbol", CHAR).length(6)
				)
				.messages(
						message("Bindings", 1).fields(
								field("price", 1, "Cents"),
								field("fee", 2, "int64"),
								field("rebate", 3, "int64").presence(OPTIONAL),
								field("symbol", 4, "Symbol"),
								field("colour", 5, "Rgb")
						)
				)
				.build();
	}

	static Annotated annotated() {
		AnnotatedTypeBuilder cents = annotatedType("Cents", INT64);
		AnnotatedTypeBuilder rgb = annotatedType("Rgb", UINT8).length(3);
		AnnotatedTypeBuilder symbol = annotatedType("Symbol", CHAR).length(6);
		return annotatedSchema("corpus.bindings", 1, 0)
				.types(cents, rgb, symbol)
				.messages(
						annotatedMessage("Bindings", 1).components(
								annotatedField("price", 1, other("java.math.BigDecimal")).type(cents)
										.binding("corpus.bindings.CentsBinding", boxed(LONG)),
								annotatedField("fee", 2, other("java.math.BigDecimal")).primitiveType(INT64)
										.binding("corpus.bindings.CentsBinding", boxed(LONG)),
								annotatedField("rebate", 3, other("java.math.BigDecimal")).primitiveType(INT64)
										.presence(OPTIONAL)
										.binding("corpus.bindings.CentsBinding", boxed(LONG)),
								annotatedField("symbol", 4, other("corpus.bindings.Ticker")).type(symbol)
										.binding("corpus.bindings.TickerBinding", text()),
								annotatedField("colour", 5, other("corpus.bindings.Colour")).type(rgb)
										.binding("corpus.bindings.ColourBinding", bytes())
						)
				)
				.build();
	}
}
