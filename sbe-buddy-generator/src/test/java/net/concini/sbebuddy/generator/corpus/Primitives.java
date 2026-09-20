package net.concini.sbebuddy.generator.corpus;

import static net.concini.sbebuddy.generator.Fixtures.field;
import static net.concini.sbebuddy.generator.Fixtures.message;
import static net.concini.sbebuddy.generator.Fixtures.messageHeader;
import static net.concini.sbebuddy.generator.Fixtures.messageSchema;

import net.concini.sbebuddy.generator.Schema;

/** Every primitive type as a field. */
final class Primitives {

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
}
