package corpus.vardata;

import java.util.List;

import net.concini.sbebuddy.tests.SchemaCase;

/**
 * Variable-length data: text with a character encoding, opaque bytes without
 * one, a length type wide enough to need its own maxValue, and var-data inside
 * a group's entry. {@code codecs = false}: the oracle check only, until
 * increment 15.
 */
final class VarDataTest implements SchemaCase {

	static final String ORACLE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.vardata" id="1" version="0">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			        <composite name="varBlobEncoding">
			            <type name="length" primitiveType="uint32" maxValue="1073741824"/>
			            <type name="varData" primitiveType="uint8" length="0"/>
			        </composite>
			        <composite name="varByteEncoding">
			            <type name="length" primitiveType="uint8"/>
			            <type name="varData" primitiveType="uint8" length="0"/>
			        </composite>
			        <composite name="varStringEncoding">
			            <type name="length" primitiveType="uint16"/>
			            <type name="varData" primitiveType="char" length="0" characterEncoding="UTF-8"/>
			        </composite>
			        <composite name="groupSizeEncoding">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="numInGroup" primitiveType="uint16"/>
			        </composite>
			    </types>
			    <sbe:message name="VarData" id="1">
			        <field name="orderId" id="1" type="int32"/>
			        <group name="attachments" id="5">
			            <field name="kind" id="6" type="int32"/>
			            <data name="content" id="7" type="varByteEncoding" offset="4"/>
			        </group>
			        <data name="note" id="2" type="varStringEncoding" semanticType="String" description="A free-text note"/>
			        <data name="payload" id="3" type="varBlobEncoding"/>
			        <data name="signature" id="4" type="varByteEncoding"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	@Override
	public String description() {
		return "VarData: text with a character encoding, opaque bytes without one, var-data inside a group's entry";
	}

	@Override
	public String oracle() {
		return ORACLE;
	}

	@Override
	public List<RoundTrip<?>> roundTrips() {
		return List.of();
	}
}
