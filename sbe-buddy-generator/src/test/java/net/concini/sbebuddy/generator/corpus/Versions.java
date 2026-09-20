package net.concini.sbebuddy.generator.corpus;

import static net.concini.sbebuddy.generator.Fixtures.choice;
import static net.concini.sbebuddy.generator.Fixtures.composite;
import static net.concini.sbebuddy.generator.Fixtures.data;
import static net.concini.sbebuddy.generator.Fixtures.enumeration;
import static net.concini.sbebuddy.generator.Fixtures.field;
import static net.concini.sbebuddy.generator.Fixtures.group;
import static net.concini.sbebuddy.generator.Fixtures.groupSizeEncoding;
import static net.concini.sbebuddy.generator.Fixtures.message;
import static net.concini.sbebuddy.generator.Fixtures.messageHeader;
import static net.concini.sbebuddy.generator.Fixtures.messageSchema;
import static net.concini.sbebuddy.generator.Fixtures.ref;
import static net.concini.sbebuddy.generator.Fixtures.set;
import static net.concini.sbebuddy.generator.Fixtures.type;
import static net.concini.sbebuddy.generator.Fixtures.validValue;
import static uk.co.real_logic.sbe.PrimitiveType.CHAR;
import static uk.co.real_logic.sbe.PrimitiveType.INT32;
import static uk.co.real_logic.sbe.PrimitiveType.UINT16;

import net.concini.sbebuddy.generator.Schema;

/**
 * Every node kind carrying both version attributes, under a schema old enough
 * to declare them: sbe-tool rejects a sinceVersion above the schema's version.
 */
final class Versions {

	static final String XML = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.versions" id="1" version="3" semanticVersion="FIX.5.0SP2">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			        <composite name="groupSizeEncoding">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="numInGroup" primitiveType="uint16"/>
			        </composite>
			        <composite name="varStringEncoding">
			            <type name="length" primitiveType="uint16"/>
			            <type name="varData" primitiveType="char" length="0" characterEncoding="UTF-8"/>
			        </composite>
			        <type name="Added" primitiveType="int32" sinceVersion="1" deprecated="3"/>
			        <enum name="Status" encodingType="uint8" sinceVersion="1" deprecated="3">
			            <validValue name="New" sinceVersion="1" deprecated="3">1</validValue>
			        </enum>
			        <set name="Flags" encodingType="uint8" sinceVersion="1" deprecated="3">
			            <choice name="urgent" sinceVersion="1" deprecated="3">0</choice>
			        </set>
			        <composite name="Pair" sinceVersion="1" deprecated="3">
			            <type name="first" primitiveType="int32"/>
			            <ref name="second" type="Added" offset="4" sinceVersion="1" deprecated="3"/>
			        </composite>
			    </types>
			    <sbe:message name="Versions" id="1" sinceVersion="1" deprecated="3">
			        <field name="added" id="1" type="Added" sinceVersion="1" deprecated="3"/>
			        <group name="extra" id="2" sinceVersion="2" deprecated="3">
			            <field name="pair" id="3" type="Pair" sinceVersion="2" deprecated="3"/>
			        </group>
			        <data name="note" id="4" type="varStringEncoding" sinceVersion="2" deprecated="3"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private Versions() {
	}

	static Schema schema() {
		return messageSchema("corpus.versions", 1, 3)
				.semanticVersion("FIX.5.0SP2")
				.types(
						messageHeader(),
						groupSizeEncoding(),
						composite("varStringEncoding").members(
								type("length", UINT16),
								type("varData", CHAR).length(0).characterEncoding("UTF-8")
						),
						type("Added", INT32).sinceVersion(1).deprecated(3),
						enumeration("Status", "uint8")
								.sinceVersion(1)
								.deprecated(3)
								.validValues(validValue("New", "1").sinceVersion(1).deprecated(3)),
						set("Flags", "uint8")
								.sinceVersion(1)
								.deprecated(3)
								.choices(choice("urgent", 0).sinceVersion(1).deprecated(3)),
						composite("Pair")
								.sinceVersion(1)
								.deprecated(3)
								.members(
										type("first", INT32),
										ref("second", "Added").offset(4).sinceVersion(1).deprecated(3)
								)
				)
				.messages(
						message("Versions", 1)
								.sinceVersion(1)
								.deprecated(3)
								.fields(field("added", 1, "Added").sinceVersion(1).deprecated(3))
								.groups(
										group("extra", 2)
												.sinceVersion(2)
												.deprecated(3)
												.fields(field("pair", 3, "Pair").sinceVersion(2).deprecated(3))
								)
								.data(data("note", 4, "varStringEncoding").sinceVersion(2).deprecated(3))
				)
				.build();
	}
}
