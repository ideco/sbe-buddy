package net.concini.sbebuddy.generator.corpus;

import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.BYTE;
import static net.concini.sbebuddy.generator.Fixtures.annotatedEnum;
import static net.concini.sbebuddy.generator.Fixtures.annotatedEnumValue;
import static net.concini.sbebuddy.generator.Fixtures.annotatedField;
import static net.concini.sbebuddy.generator.Fixtures.annotatedMessage;
import static net.concini.sbebuddy.generator.Fixtures.annotatedSchema;
import static net.concini.sbebuddy.generator.Fixtures.annotatedType;
import static net.concini.sbebuddy.generator.Fixtures.declared;
import static net.concini.sbebuddy.generator.Fixtures.enumeration;
import static net.concini.sbebuddy.generator.Fixtures.field;
import static net.concini.sbebuddy.generator.Fixtures.message;
import static net.concini.sbebuddy.generator.Fixtures.messageHeader;
import static net.concini.sbebuddy.generator.Fixtures.messageSchema;
import static net.concini.sbebuddy.generator.Fixtures.primitive;
import static net.concini.sbebuddy.generator.Fixtures.text;
import static net.concini.sbebuddy.generator.Fixtures.type;
import static net.concini.sbebuddy.generator.Fixtures.validValue;
import static uk.co.real_logic.sbe.PrimitiveType.CHAR;
import static uk.co.real_logic.sbe.xml.Presence.CONSTANT;

import net.concini.sbebuddy.generator.Annotated;
import net.concini.sbebuddy.generator.Fixtures.AnnotatedEnumBuilder;
import net.concini.sbebuddy.generator.Fixtures.AnnotatedTypeBuilder;
import net.concini.sbebuddy.generator.Schema;

/**
 * The three ways a value leaves the wire: a constant type holding its value as
 * text, a constant type naming a valid value, and a constant enum field.
 */
final class Constants {

	static final String PACKAGE_INFO = """
			@SbeSchema(id = 1, version = 0)
			package corpus.constants;

			import net.concini.sbebuddy.SbeSchema;
			""";

	static final String SOURCE = """
			package corpus.constants;

			import static net.concini.sbebuddy.Presence.CONSTANT;
			import static net.concini.sbebuddy.PrimitiveType.CHAR;

			import net.concini.sbebuddy.SbeEnum;
			import net.concini.sbebuddy.SbeEnumValue;
			import net.concini.sbebuddy.SbeField;
			import net.concini.sbebuddy.SbeMessage;
			import net.concini.sbebuddy.SbeType;

			@SbeEnum(primitiveType = CHAR)
			enum Side {

				@SbeEnumValue("B")
				Buy,

				@SbeEnumValue("S")
				Sell
			}

			@SbeType(primitiveType = CHAR, length = 3, presence = CONSTANT, value = "USD")
			final class Currency {
			}

			@SbeType(primitiveType = CHAR, presence = CONSTANT, valueRef = "Side.Buy")
			final class BuySide {
			}

			@SbeMessage(id = 1)
			record Constants(
					@SbeField(id = 1, type = Currency.class) String currency,
					@SbeField(id = 2, type = BuySide.class) byte buySide,
					@SbeField(id = 3, presence = CONSTANT, valueRef = "Side.Sell") Side side
			) {
			}
			""";

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

	static Annotated annotated() {
		AnnotatedEnumBuilder side = annotatedEnum("Side")
				.primitiveType(CHAR)
				.values(
						annotatedEnumValue("Buy", "B"),
						annotatedEnumValue("Sell", "S")
				);
		AnnotatedTypeBuilder currency = annotatedType("Currency", CHAR).length(3).presence(CONSTANT).value("USD");
		AnnotatedTypeBuilder buySide = annotatedType("BuySide", CHAR).presence(CONSTANT).valueRef("Side.Buy");
		return annotatedSchema("corpus.constants", 1, 0)
				.types(side, currency, buySide)
				.messages(
						annotatedMessage("Constants", 1).components(
								annotatedField("currency", 1, text()).type(currency),
								annotatedField("buySide", 2, primitive(BYTE)).type(buySide),
								annotatedField("side", 3, declared(side))
										.presence(CONSTANT)
										.valueRef("Side.Sell")
						)
				)
				.build();
	}
}
