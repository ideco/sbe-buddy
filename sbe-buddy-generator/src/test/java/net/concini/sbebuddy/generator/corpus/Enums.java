package net.concini.sbebuddy.generator.corpus;

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
import static net.concini.sbebuddy.generator.Fixtures.type;
import static net.concini.sbebuddy.generator.Fixtures.validValue;
import static uk.co.real_logic.sbe.PrimitiveType.CHAR;
import static uk.co.real_logic.sbe.PrimitiveType.UINT8;

import net.concini.sbebuddy.generator.Annotated;
import net.concini.sbebuddy.generator.Fixtures.AnnotatedEnumBuilder;
import net.concini.sbebuddy.generator.Fixtures.AnnotatedTypeBuilder;
import net.concini.sbebuddy.generator.Schema;

/**
 * Enumerations over a primitive and over a named type, which sbe-tool resolves
 * to the primitive it encodes.
 */
final class Enums {

	static final String PACKAGE_INFO = """
			@SbeSchema(id = 1, version = 0, codecs = false)
			package corpus.enums;

			import net.concini.sbebuddy.SbeSchema;
			""";

	static final String SOURCE = """
			package corpus.enums;

			import static net.concini.sbebuddy.PrimitiveType.CHAR;
			import static net.concini.sbebuddy.PrimitiveType.UINT8;

			import net.concini.sbebuddy.SbeEnum;
			import net.concini.sbebuddy.SbeEnumValue;
			import net.concini.sbebuddy.SbeField;
			import net.concini.sbebuddy.SbeMessage;
			import net.concini.sbebuddy.SbeType;

			@SbeType(primitiveType = UINT8)
			final class StatusCode {
			}

			@SbeEnum(primitiveType = CHAR, semanticType = "Side", description = "Which side of the market an order takes")
			enum Side {

				@SbeEnumValue(value = "B", description = "The order buys")
				Buy,

				@SbeEnumValue(value = "S", description = "The order sells")
				Sell
			}

			@SbeEnum(encodingType = StatusCode.class)
			enum OrderStatus {

				@SbeEnumValue("0")
				New,

				@SbeEnumValue("1")
				PartiallyFilled,

				@SbeEnumValue("2")
				Filled
			}

			@SbeMessage(id = 1)
			record Enums(
					@SbeField(id = 1) Side side,
					@SbeField(id = 2) OrderStatus status
			) {
			}
			""";

	static final String XML = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.enums" id="1" version="0">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			        <type name="StatusCode" primitiveType="uint8"/>
			        <enum name="Side" encodingType="char" semanticType="Side" description="Which side of the market an order takes">
			            <validValue name="Buy" description="The order buys">B</validValue>
			            <validValue name="Sell" description="The order sells">S</validValue>
			        </enum>
			        <enum name="OrderStatus" encodingType="StatusCode">
			            <validValue name="New">0</validValue>
			            <validValue name="PartiallyFilled">1</validValue>
			            <validValue name="Filled">2</validValue>
			        </enum>
			    </types>
			    <sbe:message name="Enums" id="1">
			        <field name="side" id="1" type="Side"/>
			        <field name="status" id="2" type="OrderStatus"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private Enums() {
	}

	static Schema schema() {
		return messageSchema("corpus.enums", 1, 0)
				.types(
						messageHeader(),
						type("StatusCode", UINT8),
						enumeration("Side", "char")
								.semanticType("Side")
								.description("Which side of the market an order takes")
								.validValues(
										validValue("Buy", "B").description("The order buys"),
										validValue("Sell", "S").description("The order sells")
								),
						enumeration("OrderStatus", "StatusCode").validValues(
								validValue("New", "0"),
								validValue("PartiallyFilled", "1"),
								validValue("Filled", "2")
						)
				)
				.messages(
						message("Enums", 1).fields(
								field("side", 1, "Side"),
								field("status", 2, "OrderStatus")
						)
				)
				.build();
	}

	static Annotated annotated() {
		AnnotatedTypeBuilder statusCode = annotatedType("StatusCode", UINT8);
		AnnotatedEnumBuilder side = annotatedEnum("Side")
				.primitiveType(CHAR)
				.semanticType("Side")
				.description("Which side of the market an order takes")
				.values(
						annotatedEnumValue("Buy", "B").description("The order buys"),
						annotatedEnumValue("Sell", "S").description("The order sells")
				);
		AnnotatedEnumBuilder orderStatus = annotatedEnum("OrderStatus")
				.encodingType(statusCode)
				.values(
						annotatedEnumValue("New", "0"),
						annotatedEnumValue("PartiallyFilled", "1"),
						annotatedEnumValue("Filled", "2")
				);
		return annotatedSchema("corpus.enums", 1, 0).codecs(false)
				.types(statusCode, side, orderStatus)
				.messages(
						annotatedMessage("Enums", 1).components(
								annotatedField("side", 1, declared(side)),
								annotatedField("status", 2, declared(orderStatus))
						)
				)
				.build();
	}
}
