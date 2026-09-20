package net.concini.sbebuddy.generator.corpus;

import static net.concini.sbebuddy.generator.Fixtures.choice;
import static net.concini.sbebuddy.generator.Fixtures.field;
import static net.concini.sbebuddy.generator.Fixtures.message;
import static net.concini.sbebuddy.generator.Fixtures.messageHeader;
import static net.concini.sbebuddy.generator.Fixtures.messageSchema;
import static net.concini.sbebuddy.generator.Fixtures.set;
import static net.concini.sbebuddy.generator.Fixtures.type;
import static uk.co.real_logic.sbe.PrimitiveType.UINT8;

import net.concini.sbebuddy.generator.Schema;

/**
 * Bit sets over a primitive and over a named type; a choice's text is the bit's
 * zero-based position, not its mask.
 */
final class Sets {

	static final String XML = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.sets" id="1" version="0">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			        <type name="FlagsEncoding" primitiveType="uint8"/>
			        <set name="Permissions" encodingType="uint16" semanticType="MultipleCharValue" description="What the account may do">
			            <choice name="canTrade" description="May submit orders">0</choice>
			            <choice name="canQuote" description="May submit quotes">1</choice>
			            <choice name="canCancel">2</choice>
			        </set>
			        <set name="Handling" encodingType="FlagsEncoding">
			            <choice name="urgent">0</choice>
			            <choice name="manual">7</choice>
			        </set>
			    </types>
			    <sbe:message name="Sets" id="1">
			        <field name="permissions" id="1" type="Permissions"/>
			        <field name="handling" id="2" type="Handling"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private Sets() {
	}

	static Schema schema() {
		return messageSchema("corpus.sets", 1, 0)
				.types(
						messageHeader(),
						type("FlagsEncoding", UINT8),
						set("Permissions", "uint16")
								.semanticType("MultipleCharValue")
								.description("What the account may do")
								.choices(
										choice("canTrade", 0).description("May submit orders"),
										choice("canQuote", 1).description("May submit quotes"),
										choice("canCancel", 2)
								),
						set("Handling", "FlagsEncoding").choices(
								choice("urgent", 0),
								choice("manual", 7)
						)
				)
				.messages(
						message("Sets", 1).fields(
								field("permissions", 1, "Permissions"),
								field("handling", 2, "Handling")
						)
				)
				.build();
	}
}
