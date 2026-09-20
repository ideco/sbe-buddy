package net.concini.sbebuddy.generator.corpus;

import static net.concini.sbebuddy.generator.Fixtures.field;
import static net.concini.sbebuddy.generator.Fixtures.message;
import static net.concini.sbebuddy.generator.Fixtures.messageHeader;
import static net.concini.sbebuddy.generator.Fixtures.messageSchema;
import static net.concini.sbebuddy.generator.Fixtures.type;
import static uk.co.real_logic.sbe.PrimitiveType.INT32;
import static uk.co.real_logic.sbe.PrimitiveType.UINT8;

import net.concini.sbebuddy.generator.Schema;

/**
 * Fixed-length arrays of a primitive other than char, which is a string, not an
 * array.
 */
final class Arrays {

	static final String XML = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.arrays" id="1" version="0">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			        <type name="Rgb" primitiveType="uint8" length="3"/>
			        <type name="Samples" primitiveType="int32" length="4" description="Four readings, oldest first"/>
			    </types>
			    <sbe:message name="Arrays" id="1">
			        <field name="colour" id="1" type="Rgb"/>
			        <field name="samples" id="2" type="Samples"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private Arrays() {
	}

	static Schema schema() {
		return messageSchema("corpus.arrays", 1, 0)
				.types(
						messageHeader(),
						type("Rgb", UINT8).length(3),
						type("Samples", INT32).length(4).description("Four readings, oldest first")
				)
				.messages(
						message("Arrays", 1).fields(
								field("colour", 1, "Rgb"),
								field("samples", 2, "Samples")
						)
				)
				.build();
	}
}
