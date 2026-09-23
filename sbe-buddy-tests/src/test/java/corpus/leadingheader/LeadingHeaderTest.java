package corpus.leadingheader;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.tests.SchemaCase;

import corpus.leadingheader.sbe.FramedEncoder;
import corpus.leadingheader.sbe.FramingHeaderDecoder;

/**
 * A header whose member of its own comes first, as a length prefix does, so
 * every standard member sits at another offset than in the standard header. The
 * tests show the codec reading and writing each at its own offset.
 */
final class LeadingHeaderTest implements SchemaCase {

	static final String ORACLE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.leadingheader" id="7" version="2" headerType="framingHeader">
			    <types>
			        <composite name="framingHeader">
			            <type name="frameLength" primitiveType="uint32"/>
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			    </types>
			    <sbe:message name="Framed" id="3">
			        <field name="orderId" id="1" type="int64"/>
			        <field name="quantity" id="2" type="int32"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private static final int OFFSET = 8;

	private static final Framed ORDER = new Framed(42L, 100);

	@Override
	public String description() {
		return "LeadingHeader: a header whose member of its own comes before the standard four";
	}

	@Override
	public String oracle() {
		return ORACLE;
	}

	@Override
	public List<RoundTrip<?>> roundTrips() {
		return List.of(new RoundTrip<>("a message behind a leading frame length", new FramedCodec(), ORDER));
	}

	@Test
	void theStandardFourSitAfterTheLeadingMember() {
		assertThat(FramingHeaderDecoder.blockLengthEncodingOffset()).isEqualTo(4);
		assertThat(FramingHeaderDecoder.versionEncodingOffset()).isEqualTo(10);
	}

	@Test
	void theHeaderReadsBackAtItsOwnOffsets() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		buffer.setMemory(0, buffer.capacity(), (byte) 0x5A);
		FramedCodec codec = new FramedCodec();
		int length = codec.encode(ORDER, new FramingHeader(1_000L, 0, 0, 0, 0), buffer, OFFSET);

		assertThat(codec.decodeHeader(buffer, OFFSET)).isEqualTo(
				new FramingHeader(
						1_000L, FramedEncoder.BLOCK_LENGTH, FramedEncoder.TEMPLATE_ID, FramedEncoder.SCHEMA_ID,
						FramedEncoder.SCHEMA_VERSION
				)
		);
		assertThat(codec.decode(buffer, OFFSET)).isEqualTo(ORDER);
		assertThat(codec.lastDecodedLength()).isEqualTo(length);
	}

	@Test
	void plainEncodeWritesTheLeadingMemberAsItsNullValue() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		buffer.setMemory(0, buffer.capacity(), (byte) 0x5A);
		new FramedCodec().encode(ORDER, buffer, OFFSET);

		assertThat(new FramingHeaderDecoder().wrap(buffer, OFFSET).frameLength())
				.isEqualTo(FramingHeaderDecoder.frameLengthNullValue());
	}
}
