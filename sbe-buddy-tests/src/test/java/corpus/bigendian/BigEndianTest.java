package corpus.bigendian;

import java.util.List;

import net.concini.sbebuddy.tests.SchemaCase;

/**
 * A schema on the wire the other way round. {@code codecs = false}: the oracle
 * check only, until increment 17.
 */
final class BigEndianTest implements SchemaCase {

	static final String ORACLE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.bigendian" id="1" version="0" byteOrder="bigEndian">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			    </types>
			    <sbe:message name="BigEndian" id="1">
			        <field name="orderId" id="1" type="int64"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	@Override
	public String description() {
		return "BigEndian: a schema on the wire the other way round";
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
