package net.concini.sbebuddy.generator.corpus;

import static net.concini.sbebuddy.generator.Fixtures.field;
import static net.concini.sbebuddy.generator.Fixtures.message;
import static net.concini.sbebuddy.generator.Fixtures.messageHeader;
import static net.concini.sbebuddy.generator.Fixtures.messageSchema;
import static uk.co.real_logic.sbe.xml.Presence.OPTIONAL;

import net.concini.sbebuddy.generator.Schema;

/**
 * Presence declared on the field itself rather than on a named type; the
 * primitive's null value applies.
 */
final class OptionalFields {

	static final String XML = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.optionalfields" id="1" version="0">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			    </types>
			    <sbe:message name="OptionalFields" id="1">
			        <field name="orderId" id="1" type="int64"/>
			        <field name="quantity" id="2" type="int32" presence="optional"/>
			        <field name="price" id="3" type="double" presence="optional" description="Absent for a market order"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private OptionalFields() {
	}

	static Schema schema() {
		return messageSchema("corpus.optionalfields", 1, 0)
				.types(messageHeader())
				.messages(
						message("OptionalFields", 1).fields(
								field("orderId", 1, "int64"),
								field("quantity", 2, "int32").presence(OPTIONAL),
								field("price", 3, "double").presence(OPTIONAL).description("Absent for a market order")
						)
				)
				.build();
	}
}
