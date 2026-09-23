package corpus.unmappedarrays;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.tests.SchemaCase;

import corpus.unmappedarrays.UnmappedArrays.Leg;
import corpus.unmappedarrays.sbe.MessageHeaderDecoder;
import corpus.unmappedarrays.sbe.PadDecoder;
import corpus.unmappedarrays.sbe.UnmappedArraysDecoder;

/**
 * Fixed-length fields no component carries: an array, an ASCII string and a
 * UTF-8 string on the message, an array as a composite's member and one in a
 * group's entry. The round trips carry the components; the test shows every
 * unmapped element holding its null value over bytes that held something else.
 */
final class UnmappedArraysTest implements SchemaCase {

	static final String ORACLE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.unmappedarrays" id="1" version="0">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			        <type name="Label" primitiveType="char" length="4" characterEncoding="UTF-8"/>
			        <composite name="Pad">
			            <type name="value" primitiveType="int32"/>
			            <type name="reserved" primitiveType="uint8" length="3"/>
			        </composite>
			        <type name="Samples" primitiveType="int32" length="4"/>
			        <type name="Symbol" primitiveType="char" length="6" characterEncoding="ASCII"/>
			        <composite name="groupSizeEncoding">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="numInGroup" primitiveType="uint16"/>
			        </composite>
			    </types>
			    <sbe:message name="UnmappedArrays" id="1">
			        <field name="orderId" id="1" type="int64"/>
			        <field name="samples" id="2" type="Samples"/>
			        <field name="symbol" id="3" type="Symbol"/>
			        <field name="label" id="4" type="Label"/>
			        <field name="pad" id="5" type="Pad"/>
			        <group name="legs" id="6">
			            <field name="legId" id="7" type="int32"/>
			            <field name="reserved" id="8" type="Samples"/>
			        </group>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private static final int OFFSET = 8;

	private static final UnmappedArrays VALUE = new UnmappedArrays(42L, new Pad(7), List.of(new Leg(1), new Leg(2)));

	@Override
	public String description() {
		return "UnmappedArrays: arrays and strings no component carries, on a message, a composite and a group";
	}

	@Override
	public String oracle() {
		return ORACLE;
	}

	@Override
	public List<RoundTrip<?>> roundTrips() {
		return List.of(
				new RoundTrip<>("the components around the unmapped arrays", new UnmappedArraysCodec(), VALUE),
				new RoundTrip<>(
						"no legs, so no entry's unmapped array", new UnmappedArraysCodec(),
						new UnmappedArrays(-1L, new Pad(Integer.MIN_VALUE), List.of())
				)
		);
	}

	@Test
	void everyUnmappedElementHoldsItsNullValue() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
		buffer.setMemory(0, buffer.capacity(), (byte) 0x5A);
		new UnmappedArraysCodec().encode(VALUE, buffer, OFFSET);

		MessageHeaderDecoder header = new MessageHeaderDecoder().wrap(buffer, OFFSET);
		UnmappedArraysDecoder decoder = new UnmappedArraysDecoder().wrap(
				buffer, OFFSET + MessageHeaderDecoder.ENCODED_LENGTH, header.blockLength(), header.version()
		);

		for (int i = 0; i < UnmappedArraysDecoder.samplesLength(); i++) {
			assertThat(decoder.samples(i)).isEqualTo(UnmappedArraysDecoder.samplesNullValue());
		}
		for (int i = 0; i < UnmappedArraysDecoder.symbolLength(); i++) {
			assertThat(decoder.symbol(i)).isEqualTo(UnmappedArraysDecoder.symbolNullValue());
		}
		for (int i = 0; i < UnmappedArraysDecoder.labelLength(); i++) {
			assertThat(decoder.label(i)).isEqualTo(UnmappedArraysDecoder.labelNullValue());
		}
		PadDecoder pad = decoder.pad();
		for (int i = 0; i < PadDecoder.reservedLength(); i++) {
			assertThat(pad.reserved(i)).isEqualTo(PadDecoder.reservedNullValue());
		}
		for (UnmappedArraysDecoder.LegsDecoder leg : decoder.legs()) {
			for (int i = 0; i < UnmappedArraysDecoder.LegsDecoder.reservedLength(); i++) {
				assertThat(leg.reserved(i)).isEqualTo(UnmappedArraysDecoder.LegsDecoder.reservedNullValue());
			}
		}
	}
}
