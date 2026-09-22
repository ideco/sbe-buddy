package corpus.header;

import java.util.List;

import net.concini.sbebuddy.tests.SchemaCase;

/**
 * A header of the schema's own naming and shape. sbe-tool requires the four
 * standard members and ignores anything else the header carries.
 * {@code codecs = false}: the oracle check only, until increment 16.
 */
final class HeaderTest implements SchemaCase {

	static final String ORACLE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.header" id="1" version="0" headerType="applicationHeader">
			    <types>
			        <composite name="applicationHeader" description="The standard header and a sequence number">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			            <type name="sequenceNumber" primitiveType="uint32"/>
			        </composite>
			    </types>
			    <sbe:message name="Header" id="1">
			        <field name="orderId" id="1" type="int64"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	@Override
	public String description() {
		return "Header: a header of the schema's own naming and shape";
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
