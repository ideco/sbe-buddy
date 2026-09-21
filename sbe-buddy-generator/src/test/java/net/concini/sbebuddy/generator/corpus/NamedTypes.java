package net.concini.sbebuddy.generator.corpus;

import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.LONG;
import static net.concini.sbebuddy.generator.Fixtures.annotatedField;
import static net.concini.sbebuddy.generator.Fixtures.annotatedMessage;
import static net.concini.sbebuddy.generator.Fixtures.annotatedSchema;
import static net.concini.sbebuddy.generator.Fixtures.annotatedType;
import static net.concini.sbebuddy.generator.Fixtures.boxed;
import static net.concini.sbebuddy.generator.Fixtures.field;
import static net.concini.sbebuddy.generator.Fixtures.message;
import static net.concini.sbebuddy.generator.Fixtures.messageHeader;
import static net.concini.sbebuddy.generator.Fixtures.messageSchema;
import static net.concini.sbebuddy.generator.Fixtures.primitive;
import static net.concini.sbebuddy.generator.Fixtures.text;
import static net.concini.sbebuddy.generator.Fixtures.type;
import static uk.co.real_logic.sbe.PrimitiveType.CHAR;
import static uk.co.real_logic.sbe.PrimitiveType.INT64;
import static uk.co.real_logic.sbe.PrimitiveType.UINT32;
import static uk.co.real_logic.sbe.xml.Presence.OPTIONAL;

import net.concini.sbebuddy.generator.Annotated;
import net.concini.sbebuddy.generator.Fixtures.AnnotatedTypeBuilder;
import net.concini.sbebuddy.generator.Schema;

/**
 * Every attribute a named type carries. A field takes its presence from its
 * type, so the optional type makes an optional field without saying so.
 */
final class NamedTypes {

	static final String PACKAGE_INFO = """
			@SbeSchema(id = 1, version = 0)
			package corpus.namedtypes;

			import net.concini.sbebuddy.SbeSchema;
			""";

	static final String SOURCE = """
			package corpus.namedtypes;

			import static net.concini.sbebuddy.Presence.OPTIONAL;
			import static net.concini.sbebuddy.PrimitiveType.CHAR;
			import static net.concini.sbebuddy.PrimitiveType.INT64;
			import static net.concini.sbebuddy.PrimitiveType.UINT32;

			import net.concini.sbebuddy.SbeField;
			import net.concini.sbebuddy.SbeMessage;
			import net.concini.sbebuddy.SbeType;

			@SbeType(
					primitiveType = CHAR, length = 6, characterEncoding = "ASCII", semanticType = "String",
					description = "An instrument symbol"
			)
			final class Symbol {
			}

			@SbeType(primitiveType = INT64, minValue = "0", maxValue = "9223372036854775806", semanticType = "Price")
			final class Price {
			}

			@SbeType(
					primitiveType = UINT32, presence = OPTIONAL, nullValue = "4294967294",
					description = "Absent when the size is not disclosed"
			)
			final class Quantity {
			}

			@SbeMessage(id = 1)
			record NamedTypes(
					@SbeField(id = 1, type = Symbol.class) String symbol,
					@SbeField(id = 2, type = Price.class) long price,
					@SbeField(id = 3, type = Quantity.class) Long quantity
			) {
			}
			""";

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

	static Annotated annotated() {
		AnnotatedTypeBuilder symbol = annotatedType("Symbol", CHAR)
				.length(6)
				.characterEncoding("ASCII")
				.semanticType("String")
				.description("An instrument symbol");
		AnnotatedTypeBuilder price = annotatedType("Price", INT64)
				.minValue("0")
				.maxValue("9223372036854775806")
				.semanticType("Price");
		AnnotatedTypeBuilder quantity = annotatedType("Quantity", UINT32)
				.presence(OPTIONAL)
				.nullValue("4294967294")
				.description("Absent when the size is not disclosed");
		return annotatedSchema("corpus.namedtypes", 1, 0)
				.types(symbol, price, quantity)
				.messages(
						annotatedMessage("NamedTypes", 1).components(
								annotatedField("symbol", 1, text()).type(symbol),
								annotatedField("price", 2, primitive(LONG)).type(price),
								annotatedField("quantity", 3, boxed(LONG)).type(quantity)
						)
				)
				.build();
	}
}
