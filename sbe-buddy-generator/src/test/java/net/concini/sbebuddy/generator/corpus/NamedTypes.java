package net.concini.sbebuddy.generator.corpus;

import static net.concini.sbebuddy.generator.Fixtures.field;
import static net.concini.sbebuddy.generator.Fixtures.message;
import static net.concini.sbebuddy.generator.Fixtures.messageHeader;
import static net.concini.sbebuddy.generator.Fixtures.messageSchema;
import static net.concini.sbebuddy.generator.Fixtures.type;
import static uk.co.real_logic.sbe.PrimitiveType.CHAR;
import static uk.co.real_logic.sbe.PrimitiveType.INT64;
import static uk.co.real_logic.sbe.PrimitiveType.UINT32;
import static uk.co.real_logic.sbe.xml.Presence.OPTIONAL;

import net.concini.sbebuddy.generator.Schema;

/**
 * Every attribute a named type carries. A field takes its presence from its
 * type, so the optional type makes an optional field without saying so.
 */
final class NamedTypes {

	static final String XML = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.namedtypes" id="1" version="0">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			        <type name="Symbol" primitiveType="char" length="6" characterEncoding="ASCII" semanticType="String" description="An instrument symbol"/>
			        <type name="Price" primitiveType="int64" minValue="0" maxValue="9223372036854775806" semanticType="Price"/>
			        <type name="Quantity" primitiveType="uint32" presence="optional" nullValue="4294967294" description="Absent when the size is not disclosed"/>
			    </types>
			    <sbe:message name="NamedTypes" id="1">
			        <field name="symbol" id="1" type="Symbol"/>
			        <field name="price" id="2" type="Price"/>
			        <field name="quantity" id="3" type="Quantity"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private NamedTypes() {
	}

	static Schema schema() {
		return messageSchema("corpus.namedtypes", 1, 0)
				.types(
						messageHeader(),
						type("Symbol", CHAR)
								.length(6)
								.characterEncoding("ASCII")
								.semanticType("String")
								.description("An instrument symbol"),
						type("Price", INT64)
								.minValue("0")
								.maxValue("9223372036854775806")
								.semanticType("Price"),
						type("Quantity", UINT32)
								.presence(OPTIONAL)
								.nullValue("4294967294")
								.description("Absent when the size is not disclosed")
				)
				.messages(
						message("NamedTypes", 1).fields(
								field("symbol", 1, "Symbol"),
								field("price", 2, "Price"),
								field("quantity", 3, "Quantity")
						)
				)
				.build();
	}
}
