package net.concini.sbebuddy.generator.corpus;

import static net.concini.sbebuddy.generator.Fixtures.enumeration;
import static net.concini.sbebuddy.generator.Fixtures.field;
import static net.concini.sbebuddy.generator.Fixtures.message;
import static net.concini.sbebuddy.generator.Fixtures.messageHeader;
import static net.concini.sbebuddy.generator.Fixtures.messageSchema;
import static net.concini.sbebuddy.generator.Fixtures.type;
import static net.concini.sbebuddy.generator.Fixtures.validValue;
import static uk.co.real_logic.sbe.PrimitiveType.CHAR;
import static uk.co.real_logic.sbe.xml.Presence.CONSTANT;

import net.concini.sbebuddy.generator.Schema;

/**
 * The three ways a value leaves the wire: a constant type holding its value as
 * text, a constant type naming a valid value, and a constant enum field.
 */
final class Constants {

	static final String XML = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.constants" id="1" version="0">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			        <enum name="Side" encodingType="char">
			            <validValue name="Buy">B</validValue>
			            <validValue name="Sell">S</validValue>
			        </enum>
			        <type name="Currency" primitiveType="char" length="3" presence="constant">USD</type>
			        <type name="BuySide" primitiveType="char" presence="constant" valueRef="Side.Buy"/>
			    </types>
			    <sbe:message name="Constants" id="1">
			        <field name="currency" id="1" type="Currency"/>
			        <field name="buySide" id="2" type="BuySide"/>
			        <field name="side" id="3" type="Side" presence="constant" valueRef="Side.Sell"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private Constants() {
	}

	static Schema schema() {
		return messageSchema("corpus.constants", 1, 0)
				.types(
						messageHeader(),
						enumeration("Side", "char").validValues(
								validValue("Buy", "B"),
								validValue("Sell", "S")
						),
						type("Currency", CHAR).length(3).presence(CONSTANT).value("USD"),
						type("BuySide", CHAR).presence(CONSTANT).valueRef("Side.Buy")
				)
				.messages(
						message("Constants", 1).fields(
								field("currency", 1, "Currency"),
								field("buySide", 2, "BuySide"),
								field("side", 3, "Side").presence(CONSTANT).valueRef("Side.Sell")
						)
				)
				.build();
	}
}
