package net.concini.sbebuddy.generator.corpus;

import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.BYTE;
import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.DOUBLE;
import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.FLOAT;
import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.INT;
import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.LONG;
import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.SHORT;
import static net.concini.sbebuddy.generator.Fixtures.annotatedField;
import static net.concini.sbebuddy.generator.Fixtures.annotatedMessage;
import static net.concini.sbebuddy.generator.Fixtures.annotatedSchema;
import static net.concini.sbebuddy.generator.Fixtures.field;
import static net.concini.sbebuddy.generator.Fixtures.message;
import static net.concini.sbebuddy.generator.Fixtures.messageHeader;
import static net.concini.sbebuddy.generator.Fixtures.messageSchema;
import static net.concini.sbebuddy.generator.Fixtures.primitive;
import static uk.co.real_logic.sbe.PrimitiveType.CHAR;
import static uk.co.real_logic.sbe.PrimitiveType.UINT16;
import static uk.co.real_logic.sbe.PrimitiveType.UINT32;
import static uk.co.real_logic.sbe.PrimitiveType.UINT64;
import static uk.co.real_logic.sbe.PrimitiveType.UINT8;

import net.concini.sbebuddy.generator.Annotated;
import net.concini.sbebuddy.generator.Schema;

/**
 * Every primitive type as a field: the signed ones and the floats by the
 * default mapping, the rest said explicitly.
 */
final class Primitives {

	static final String PACKAGE_INFO = """
			@SbeSchema(id = 1, version = 0)
			package corpus.primitives;

			import net.concini.sbebuddy.SbeSchema;
			""";

	static final String SOURCE = """
			package corpus.primitives;

			import static net.concini.sbebuddy.PrimitiveType.CHAR;
			import static net.concini.sbebuddy.PrimitiveType.UINT16;
			import static net.concini.sbebuddy.PrimitiveType.UINT32;
			import static net.concini.sbebuddy.PrimitiveType.UINT64;
			import static net.concini.sbebuddy.PrimitiveType.UINT8;

			import net.concini.sbebuddy.SbeField;
			import net.concini.sbebuddy.SbeMessage;

			@SbeMessage(id = 1)
			record Primitives(
					@SbeField(id = 1, primitiveType = CHAR) byte aChar,
					@SbeField(id = 2) byte anInt8,
					@SbeField(id = 3) short anInt16,
					@SbeField(id = 4) int anInt32,
					@SbeField(id = 5) long anInt64,
					@SbeField(id = 6, primitiveType = UINT8) short aUint8,
					@SbeField(id = 7, primitiveType = UINT16) int aUint16,
					@SbeField(id = 8, primitiveType = UINT32) long aUint32,
					@SbeField(id = 9, primitiveType = UINT64) long aUint64,
					@SbeField(id = 10) float aFloat,
					@SbeField(id = 11) double aDouble
			) {
			}
			""";

	static final String XML = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.primitives" id="1" version="0">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			    </types>
			    <sbe:message name="Primitives" id="1">
			        <field name="aChar" id="1" type="char"/>
			        <field name="anInt8" id="2" type="int8"/>
			        <field name="anInt16" id="3" type="int16"/>
			        <field name="anInt32" id="4" type="int32"/>
			        <field name="anInt64" id="5" type="int64"/>
			        <field name="aUint8" id="6" type="uint8"/>
			        <field name="aUint16" id="7" type="uint16"/>
			        <field name="aUint32" id="8" type="uint32"/>
			        <field name="aUint64" id="9" type="uint64"/>
			        <field name="aFloat" id="10" type="float"/>
			        <field name="aDouble" id="11" type="double"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private Primitives() {
	}

	static Schema schema() {
		return messageSchema("corpus.primitives", 1, 0)
				.types(messageHeader())
				.messages(
						message("Primitives", 1).fields(
								field("aChar", 1, "char"),
								field("anInt8", 2, "int8"),
								field("anInt16", 3, "int16"),
								field("anInt32", 4, "int32"),
								field("anInt64", 5, "int64"),
								field("aUint8", 6, "uint8"),
								field("aUint16", 7, "uint16"),
								field("aUint32", 8, "uint32"),
								field("aUint64", 9, "uint64"),
								field("aFloat", 10, "float"),
								field("aDouble", 11, "double")
						)
				)
				.build();
	}

	static Annotated annotated() {
		return annotatedSchema("corpus.primitives", 1, 0)
				.messages(
						annotatedMessage("Primitives", 1).components(
								annotatedField("aChar", 1, primitive(BYTE)).primitiveType(CHAR),
								annotatedField("anInt8", 2, primitive(BYTE)),
								annotatedField("anInt16", 3, primitive(SHORT)),
								annotatedField("anInt32", 4, primitive(INT)),
								annotatedField("anInt64", 5, primitive(LONG)),
								annotatedField("aUint8", 6, primitive(SHORT)).primitiveType(UINT8),
								annotatedField("aUint16", 7, primitive(INT)).primitiveType(UINT16),
								annotatedField("aUint32", 8, primitive(LONG)).primitiveType(UINT32),
								annotatedField("aUint64", 9, primitive(LONG)).primitiveType(UINT64),
								annotatedField("aFloat", 10, primitive(FLOAT)),
								annotatedField("aDouble", 11, primitive(DOUBLE))
						)
				)
				.build();
	}
}
