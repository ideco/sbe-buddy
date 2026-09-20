package net.concini.sbebuddy.generator.corpus;

import static net.concini.sbebuddy.generator.Fixtures.choice;
import static net.concini.sbebuddy.generator.Fixtures.composite;
import static net.concini.sbebuddy.generator.Fixtures.enumeration;
import static net.concini.sbebuddy.generator.Fixtures.field;
import static net.concini.sbebuddy.generator.Fixtures.message;
import static net.concini.sbebuddy.generator.Fixtures.messageHeader;
import static net.concini.sbebuddy.generator.Fixtures.messageSchema;
import static net.concini.sbebuddy.generator.Fixtures.ref;
import static net.concini.sbebuddy.generator.Fixtures.set;
import static net.concini.sbebuddy.generator.Fixtures.type;
import static net.concini.sbebuddy.generator.Fixtures.validValue;
import static uk.co.real_logic.sbe.PrimitiveType.INT64;
import static uk.co.real_logic.sbe.PrimitiveType.INT8;
import static uk.co.real_logic.sbe.PrimitiveType.UINT64;

import net.concini.sbebuddy.generator.Schema;

/**
 * Everything a composite may hold: an inline type, an inline enum and set, a
 * nested composite, and a ref to a type declared at the top level, which is the
 * only place a ref resolves. Offsets run in declaration order.
 */
final class Composites {

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
}
