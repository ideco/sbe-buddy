package net.concini.sbebuddy.generator.corpus;

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
import static net.concini.sbebuddy.generator.Fixtures.choice;
import static net.concini.sbebuddy.generator.Fixtures.composite;
import static net.concini.sbebuddy.generator.Fixtures.declared;
import static net.concini.sbebuddy.generator.Fixtures.enumeration;
import static net.concini.sbebuddy.generator.Fixtures.field;
import static net.concini.sbebuddy.generator.Fixtures.message;
import static net.concini.sbebuddy.generator.Fixtures.messageHeader;
import static net.concini.sbebuddy.generator.Fixtures.messageSchema;
import static net.concini.sbebuddy.generator.Fixtures.ref;
import static net.concini.sbebuddy.generator.Fixtures.set;
import static net.concini.sbebuddy.generator.Fixtures.type;
import static net.concini.sbebuddy.generator.Fixtures.validValue;
import static uk.co.real_logic.sbe.PrimitiveType.CHAR;
import static uk.co.real_logic.sbe.PrimitiveType.INT64;
import static uk.co.real_logic.sbe.PrimitiveType.INT8;
import static uk.co.real_logic.sbe.PrimitiveType.UINT64;
import static uk.co.real_logic.sbe.PrimitiveType.UINT8;

import net.concini.sbebuddy.generator.Annotated;
import net.concini.sbebuddy.generator.Fixtures.AnnotatedCompositeBuilder;
import net.concini.sbebuddy.generator.Schema;

/**
 * Everything a composite may hold: an inline type, an inline enum and set, a
 * nested composite, and a ref to a type declared at the top level, which is the
 * only place a ref resolves. Offsets run in declaration order.
 */
final class Composites {

	static final String PACKAGE_INFO = """
			@SbeSchema(id = 1, version = 0)
			package corpus.composites;

			import net.concini.sbebuddy.SbeSchema;
			""";

	static final String SOURCE = """
			package corpus.composites;

			import static net.concini.sbebuddy.PrimitiveType.CHAR;
			import static net.concini.sbebuddy.PrimitiveType.INT64;
			import static net.concini.sbebuddy.PrimitiveType.INT8;
			import static net.concini.sbebuddy.PrimitiveType.UINT64;
			import static net.concini.sbebuddy.PrimitiveType.UINT8;

			import net.concini.sbebuddy.SbeChoice;
			import net.concini.sbebuddy.SbeComposite;
			import net.concini.sbebuddy.SbeEnum;
			import net.concini.sbebuddy.SbeEnumValue;
			import net.concini.sbebuddy.SbeField;
			import net.concini.sbebuddy.SbeMessage;
			import net.concini.sbebuddy.SbeRef;
			import net.concini.sbebuddy.SbeSet;
			import net.concini.sbebuddy.SbeType;

			@SbeComposite(semanticType = "Price", description = "A price as mantissa and exponent")
			record Decimal(
					@SbeType(primitiveType = INT64) long mantissa,
					@SbeType(primitiveType = INT8, offset = 8) byte exponent
			) {
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
				record Stamp(@SbeType(primitiveType = UINT64) long time) {
				}
			}

			@SbeMessage(id = 1)
			record Composites(
					@SbeField(id = 1) Quote quote
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
			            </composite>
			        </composite>
			    </types>
			    <sbe:message name="Composites" id="1">
			        <field name="quote" id="1" type="Quote"/>
			    </sbe:message>
			</sbe:messageSchema>
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
										.members(type("time", UINT64))
						)
				)
				.messages(
						message("Composites", 1).fields(field("quote", 1, "Quote"))
				)
				.build();
	}

	static Annotated annotated() {
		AnnotatedCompositeBuilder decimal = annotatedComposite("Decimal")
				.semanticType("Price")
				.description("A price as mantissa and exponent")
				.members(
						annotatedType("mantissa", INT64),
						annotatedType("exponent", INT8).offset(8)
				);
		AnnotatedCompositeBuilder quote = annotatedComposite("Quote").members(
				annotatedRef("bid", declared(decimal)),
				annotatedRef("ask", declared(decimal)).offset(9),
				annotatedEnum("side")
						.primitiveType(CHAR)
						.offset(18)
						.values(
								annotatedEnumValue("Buy", "B"),
								annotatedEnumValue("Sell", "S")
						),
				annotatedSet("flags").primitiveType(UINT8).offset(19).choices(annotatedChoice("firm", 0)),
				annotatedComposite("stamp")
						.offset(20)
						.description("When the quote was made")
						.members(annotatedType("time", UINT64))
		);
		return annotatedSchema("corpus.composites", 1, 0)
				.types(decimal, quote)
				.messages(
						annotatedMessage("Composites", 1).components(
								annotatedField("quote", 1, declared(quote))
						)
				)
				.build();
	}
}
