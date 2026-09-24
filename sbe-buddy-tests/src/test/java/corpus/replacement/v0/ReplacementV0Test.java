package corpus.replacement.v0;

import java.util.List;

import net.concini.sbebuddy.tests.SchemaCase;

/**
 * The replacement schema as its first version wrote it. Its reader meets the
 * later version's new template; the crossing is the current version's test.
 */
final class ReplacementV0Test implements SchemaCase {

	static final String ORACLE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.replacement.v0" id="7" version="0">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			        <composite name="Price">
			            <type name="mantissa" primitiveType="int64"/>
			            <type name="exponent" primitiveType="int8"/>
			        </composite>
			    </types>
			    <sbe:message name="Quote" id="1">
			        <field name="instrumentId" id="1" type="int64"/>
			        <field name="bid" id="2" type="Price"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private static final Quote QUOTE = new Quote(5L, new Price(10_125L, (byte) -2));

	@Override
	public String description() {
		return "ReplacementV0: the first version of a schema whose message is replaced, a quote over a composite";
	}

	@Override
	public String oracle() {
		return ORACLE;
	}

	@Override
	public List<RoundTrip<?>> roundTrips() {
		return List.of(
				new RoundTrip<>("a quote through its own codec", new QuoteCodec(), QUOTE),
				new RoundTrip<>("a quote through a union of one", new QuoteUpdateCodec(), QUOTE)
		);
	}
}
