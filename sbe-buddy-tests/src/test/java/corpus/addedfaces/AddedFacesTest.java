package corpus.addedfaces;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.tests.SchemaCase;

import corpus.addedfaces.sbe.AddedFacesEncoder;
import corpus.addedfaces.sbe.MessageHeaderEncoder;

/**
 * Optional fields whose face has no null value of its own, appended in version
 * 1: a composite and a set, each bound and unbound, a char array and an int
 * array. The round trips carry them at version 1, present and at the null each
 * binding chooses; the test holds a version 0 header, which leaves every one of
 * them null, where the flyweights hand back no composite, no set, an empty
 * string and the array's null value.
 */
final class AddedFacesTest implements SchemaCase {

	static final String ORACLE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.addedfaces" id="1" version="1">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			        <composite name="Money">
			            <type name="mantissa" primitiveType="int64" presence="optional"/>
			            <type name="exponent" primitiveType="int8" presence="constant">-2</type>
			        </composite>
			        <set name="Instruction" encodingType="uint8">
			            <choice name="POST_ONLY">0</choice>
			            <choice name="REDUCE_ONLY">1</choice>
			        </set>
			        <type name="Venue" primitiveType="char" length="4"/>
			        <type name="Levels" primitiveType="int32" length="3"/>
			    </types>
			    <sbe:message name="AddedFaces" id="1">
			        <field name="orderId" id="1" type="int64"/>
			        <field name="price" id="2" type="Money" presence="optional" sinceVersion="1"/>
			        <field name="fee" id="3" type="Money" presence="optional" sinceVersion="1"/>
			        <field name="instructions" id="4" type="Instruction" presence="optional" sinceVersion="1"/>
			        <field name="flags" id="5" type="Instruction" presence="optional" sinceVersion="1"/>
			        <field name="market" id="6" type="Venue" presence="optional" sinceVersion="1"/>
			        <field name="levels" id="7" type="Levels" presence="optional" sinceVersion="1"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private static final int OFFSET = 8;

	private static final AddedFaces PRESENT = new AddedFaces(
			7L, new BigDecimal("99.61"), new Money(125L, (byte) -2), EnumSet.of(Instruction.POST_ONLY),
			EnumSet.of(Instruction.REDUCE_ONLY), "XLON", new int[]{1, 2, 3}
	);

	@Override
	public String description() {
		return "AddedFaces: optional composite, set and array fields appended in version 1, null in a version 0 message";
	}

	@Override
	public String oracle() {
		return ORACLE;
	}

	@Override
	public List<RoundTrip<?>> roundTrips() {
		return List.of(
				new RoundTrip<>("every appended field present", new AddedFacesCodec(), PRESENT),
				new RoundTrip<>(
						"the bound fields at their binding's null, the unbound ones at their empty values",
						new AddedFacesCodec(),
						new AddedFaces(
								7L, null, new Money(null, (byte) -2), null, EnumSet.noneOf(Instruction.class), "",
								new int[3]
						)
				)
		);
	}

	@Test
	void aVersion0MessageLeavesEveryAppendedFieldNull() {
		AddedFacesCodec codec = new AddedFacesCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		codec.encode(PRESENT, buffer, OFFSET);
		new MessageHeaderEncoder().wrap(buffer, OFFSET)
				.version(0)
				.blockLength(AddedFacesEncoder.priceEncodingOffset());

		AddedFaces decoded = codec.decode(buffer, OFFSET);

		assertThat(decoded).usingRecursiveComparison()
				.isEqualTo(new AddedFaces(7L, null, null, null, null, null, null));
		assertThat(codec.lastDecodedLength())
				.isEqualTo(MessageHeaderEncoder.ENCODED_LENGTH + AddedFacesEncoder.priceEncodingOffset());
	}
}
