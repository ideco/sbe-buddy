package corpus.versions;

import java.util.List;

import net.concini.sbebuddy.tests.SchemaCase;

/**
 * Every node kind carrying both version attributes, under a schema old enough
 * to declare them. {@code codecs = false}: the oracle check only.
 */
final class VersionsTest implements SchemaCase {

	static final String ORACLE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.versions" id="1" version="3" semanticVersion="FIX.5.0SP2">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			        <type name="Added" primitiveType="int32" sinceVersion="1" deprecated="3"/>
			        <set name="Flags" encodingType="uint8" sinceVersion="1" deprecated="3">
			            <choice name="urgent" sinceVersion="1" deprecated="3">0</choice>
			        </set>
			        <composite name="Pair" sinceVersion="1" deprecated="3">
			            <type name="first" primitiveType="int32"/>
			            <ref name="second" type="Added" offset="4" sinceVersion="1" deprecated="3"/>
			        </composite>
			        <enum name="Status" encodingType="uint8" sinceVersion="1" deprecated="3">
			            <validValue name="New" sinceVersion="1" deprecated="3">1</validValue>
			        </enum>
			        <composite name="varStringEncoding">
			            <type name="length" primitiveType="uint16"/>
			            <type name="varData" primitiveType="char" length="0" characterEncoding="UTF-8"/>
			        </composite>
			        <composite name="groupSizeEncoding">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="numInGroup" primitiveType="uint16"/>
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

	@Override
	public String description() {
		return "Versions: every node kind carrying both version attributes, under a schema old enough to declare them";
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
