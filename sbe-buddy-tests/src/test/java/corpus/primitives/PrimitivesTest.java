package corpus.primitives;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.tests.SchemaCase;

import corpus.primitives.sbe.MessageHeaderEncoder;

/**
 * Every primitive type as a field: the signed ones and the floats by the
 * default mapping, the rest said explicitly. The round trips carry the bounds
 * of every field, the unsigned ones through their wider Java face.
 */
final class PrimitivesTest implements SchemaCase {

	static final String ORACLE = """
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

	private static final int OFFSET = 8;

	@Override
	public String description() {
		return "Primitives: every primitive type as a field, the signed ones and the floats by the default mapping, the rest said explicitly";
	}

	@Override
	public String oracle() {
		return ORACLE;
	}

	@Override
	public List<RoundTrip<?>> roundTrips() {
		return List.of(
				new RoundTrip<>(
						"every field at zero", new PrimitivesCodec(),
						new Primitives((byte) 0, (byte) 0, (short) 0, 0, 0L, (short) 0, 0, 0L, 0L, 0f, 0d)
				),
				new RoundTrip<>(
						"every signed field at its minimum, the unsigned fields at zero", new PrimitivesCodec(),
						new Primitives(
								Byte.MIN_VALUE, Byte.MIN_VALUE, Short.MIN_VALUE, Integer.MIN_VALUE, Long.MIN_VALUE,
								(short) 0, 0, 0L, 0L, -Float.MAX_VALUE, -Double.MAX_VALUE
						)
				),
				new RoundTrip<>(
						"every signed field at its maximum, the unsigned fields with every bit set,"
								+ " the uint64 reading negative through the long face",
						new PrimitivesCodec(),
						new Primitives(
								Byte.MAX_VALUE, Byte.MAX_VALUE, Short.MAX_VALUE, Integer.MAX_VALUE, Long.MAX_VALUE,
								(short) 0xFF, 0xFFFF, 0xFFFFFFFFL, -1L, Float.MAX_VALUE, Double.MAX_VALUE
						)
				)
		);
	}

	@Test
	void anotherTemplateIsRefused() {
		PrimitivesCodec codec = new PrimitivesCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		codec.encode(
				new Primitives((byte) 0, (byte) 0, (short) 0, 0, 0L, (short) 0, 0, 0L, 0L, 0f, 0d), buffer, OFFSET
		);
		new MessageHeaderEncoder().wrap(buffer, OFFSET).templateId(2);

		assertThatThrownBy(() -> codec.decode(buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("not a Primitives: schemaId 1, templateId 2");
	}
}
