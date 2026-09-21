package net.concini.sbebuddy.generator.corpus;

import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.DOUBLE;
import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.INT;
import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.LONG;
import static net.concini.sbebuddy.generator.Fixtures.annotatedField;
import static net.concini.sbebuddy.generator.Fixtures.annotatedMessage;
import static net.concini.sbebuddy.generator.Fixtures.annotatedSchema;
import static net.concini.sbebuddy.generator.Fixtures.boxed;
import static net.concini.sbebuddy.generator.Fixtures.field;
import static net.concini.sbebuddy.generator.Fixtures.message;
import static net.concini.sbebuddy.generator.Fixtures.messageHeader;
import static net.concini.sbebuddy.generator.Fixtures.messageSchema;
import static net.concini.sbebuddy.generator.Fixtures.primitive;
import static uk.co.real_logic.sbe.xml.Presence.OPTIONAL;

import net.concini.sbebuddy.generator.Annotated;
import net.concini.sbebuddy.generator.Schema;

/**
 * Presence declared on the field itself rather than on a named type; the
 * primitive's null value applies.
 */
final class OptionalFields {

	static final String PACKAGE_INFO = """
			@SbeSchema(id = 1, version = 0)
			package corpus.optionalfields;

			import net.concini.sbebuddy.SbeSchema;
			""";

	static final String SOURCE = """
			package corpus.optionalfields;

			import static net.concini.sbebuddy.Presence.OPTIONAL;

			import net.concini.sbebuddy.SbeField;
			import net.concini.sbebuddy.SbeMessage;

			@SbeMessage(id = 1)
			record OptionalFields(
					@SbeField(id = 1) long orderId,
					@SbeField(id = 2, presence = OPTIONAL) Integer quantity,
					@SbeField(id = 3, presence = OPTIONAL, description = "Absent for a market order") Double price
			) {
			}
			""";

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

	static Annotated annotated() {
		return annotatedSchema("corpus.optionalfields", 1, 0)
				.messages(
						annotatedMessage("OptionalFields", 1).components(
								annotatedField("orderId", 1, primitive(LONG)),
								annotatedField("quantity", 2, boxed(INT)).presence(OPTIONAL),
								annotatedField("price", 3, boxed(DOUBLE))
										.presence(OPTIONAL)
										.description("Absent for a market order")
						)
				)
				.build();
	}
}
