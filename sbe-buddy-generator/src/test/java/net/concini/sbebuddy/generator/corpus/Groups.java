package net.concini.sbebuddy.generator.corpus;

import static net.concini.sbebuddy.generator.Fixtures.composite;
import static net.concini.sbebuddy.generator.Fixtures.data;
import static net.concini.sbebuddy.generator.Fixtures.field;
import static net.concini.sbebuddy.generator.Fixtures.group;
import static net.concini.sbebuddy.generator.Fixtures.groupSizeEncoding;
import static net.concini.sbebuddy.generator.Fixtures.message;
import static net.concini.sbebuddy.generator.Fixtures.messageHeader;
import static net.concini.sbebuddy.generator.Fixtures.messageSchema;
import static net.concini.sbebuddy.generator.Fixtures.type;
import static uk.co.real_logic.sbe.PrimitiveType.CHAR;
import static uk.co.real_logic.sbe.PrimitiveType.UINT16;
import static uk.co.real_logic.sbe.PrimitiveType.UINT8;

import net.concini.sbebuddy.generator.Schema;

/**
 * A group inside a group, a group holding var-data, and a group whose
 * dimensions are a composite of its own rather than the default.
 */
final class Groups {

	static final String XML = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.groups" id="1" version="0">
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
			        <composite name="smallGroupSizeEncoding">
			            <type name="blockLength" primitiveType="uint8"/>
			            <type name="numInGroup" primitiveType="uint8"/>
			        </composite>
			        <composite name="varStringEncoding">
			            <type name="length" primitiveType="uint16"/>
			            <type name="varData" primitiveType="char" length="0" characterEncoding="UTF-8"/>
			        </composite>
			    </types>
			    <sbe:message name="Groups" id="1">
			        <field name="orderId" id="1" type="int64"/>
			        <group name="legs" id="10" blockLength="8" semanticType="NoLegs" description="The legs of a multi-leg order">
			            <field name="legId" id="11" type="int32"/>
			            <group name="allocations" id="12" dimensionType="smallGroupSizeEncoding">
			                <field name="account" id="13" type="int32"/>
			            </group>
			            <data name="legNote" id="14" type="varStringEncoding"/>
			        </group>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private Groups() {
	}

	static Schema schema() {
		return messageSchema("corpus.groups", 1, 0)
				.types(
						messageHeader(),
						groupSizeEncoding(),
						composite("smallGroupSizeEncoding").members(
								type("blockLength", UINT8),
								type("numInGroup", UINT8)
						),
						composite("varStringEncoding").members(
								type("length", UINT16),
								type("varData", CHAR).length(0).characterEncoding("UTF-8")
						)
				)
				.messages(
						message("Groups", 1)
								.fields(field("orderId", 1, "int64"))
								.groups(
										group("legs", 10)
												.blockLength(8)
												.semanticType("NoLegs")
												.description("The legs of a multi-leg order")
												.fields(field("legId", 11, "int32"))
												.groups(
														group("allocations", 12)
																.dimensionType("smallGroupSizeEncoding")
																.fields(field("account", 13, "int32"))
												)
												.data(data("legNote", 14, "varStringEncoding"))
								)
				)
				.build();
	}
}
