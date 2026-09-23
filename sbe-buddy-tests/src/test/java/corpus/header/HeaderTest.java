package corpus.header;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.tests.SchemaCase;

import corpus.header.sbe.ApplicationHeaderDecoder;
import corpus.header.sbe.ApplicationHeaderEncoder;
import corpus.header.sbe.HeaderEncoder;

/**
 * A header of the schema's own naming and shape: the four standard members
 * sbe-tool requires, and members of its own. Plain {@code encode} writes those
 * as their null value, {@code encode} with a header writes them from it, and
 * the standard four are always the message's own.
 */
final class HeaderTest implements SchemaCase {

	static final String ORACLE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.header" id="1" version="0" headerType="applicationHeader">
			    <types>
			        <composite name="applicationHeader" description="The standard header, a sequence number and a sender">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			            <type name="sequenceNumber" primitiveType="uint32"/>
			            <type name="sender" primitiveType="char" length="4" characterEncoding="ASCII"/>
			        </composite>
			    </types>
			    <sbe:message name="Header" id="1">
			        <field name="orderId" id="1" type="int64"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private static final int OFFSET = 8;

	private static final Header ORDER = new Header(42L);

	@Override
	public String description() {
		return "Header: a header of the schema's own naming and shape, with members of its own";
	}

	@Override
	public String oracle() {
		return ORACLE;
	}

	@Override
	public List<RoundTrip<?>> roundTrips() {
		return List.of(
				new RoundTrip<>("a message framed in the schema's own header", new HeaderCodec(), ORDER),
				new RoundTrip<>("the bounds of its one field", new HeaderCodec(), new Header(Long.MIN_VALUE))
		);
	}

	@Test
	void plainEncodeWritesTheHeadersOwnMembersAsTheirNullValue() {
		UnsafeBuffer buffer = dirtyBuffer();
		new HeaderCodec().encode(ORDER, buffer, OFFSET);

		ApplicationHeaderDecoder header = new ApplicationHeaderDecoder().wrap(buffer, OFFSET);
		assertThat(header.sequenceNumber()).isEqualTo(ApplicationHeaderDecoder.sequenceNumberNullValue());
		for (int i = 0; i < ApplicationHeaderDecoder.senderLength(); i++) {
			assertThat(header.sender(i)).isEqualTo(ApplicationHeaderDecoder.senderNullValue());
		}
	}

	@Test
	void encodeWithAHeaderWritesItsOwnMembersAndNeverTheStandardFour() {
		UnsafeBuffer buffer = dirtyBuffer();
		HeaderCodec codec = new HeaderCodec();
		codec.encode(ORDER, new ApplicationHeader(99, 99, 99, 99, 4_294_967_294L, "EXCH"), buffer, OFFSET);

		assertThat(codec.decodeHeader(buffer, OFFSET)).isEqualTo(
				new ApplicationHeader(
						HeaderEncoder.BLOCK_LENGTH, HeaderEncoder.TEMPLATE_ID, HeaderEncoder.SCHEMA_ID,
						HeaderEncoder.SCHEMA_VERSION, 4_294_967_294L, "EXCH"
				)
		);
		assertThat(codec.decode(buffer, OFFSET)).isEqualTo(ORDER);
	}

	@Test
	void decodeHeaderChecksNothing() {
		UnsafeBuffer buffer = dirtyBuffer();
		HeaderCodec codec = new HeaderCodec();
		codec.encode(ORDER, new ApplicationHeader(0, 0, 0, 0, 7L, "A"), buffer, OFFSET);
		new ApplicationHeaderEncoder().wrap(buffer, OFFSET).templateId(2);

		assertThat(codec.decodeHeader(buffer, OFFSET).templateId()).isEqualTo(2);
		assertThatThrownBy(() -> codec.decode(buffer, OFFSET)).isInstanceOf(IllegalArgumentException.class)
				.hasMessage("not a Header: schemaId 1, templateId 2");
	}

	@Test
	void aNullHeaderIsRefused() {
		assertThatThrownBy(() -> new HeaderCodec().encode(ORDER, null, dirtyBuffer(), OFFSET))
				.isInstanceOf(IllegalArgumentException.class).hasMessage("header is required");
	}

	@Test
	void aSenderOutsideAsciiIsRefused() {
		assertThatThrownBy(
				() -> new HeaderCodec()
						.encode(ORDER, new ApplicationHeader(0, 0, 0, 0, 1L, "\u00e9"), dirtyBuffer(), OFFSET)
		).isInstanceOf(IllegalArgumentException.class);
	}

	/**
	 * Bytes that are none of the header's null values, so a member left unwritten
	 * shows.
	 */
	private static UnsafeBuffer dirtyBuffer() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		buffer.setMemory(0, buffer.capacity(), (byte) 0x5A);
		return buffer;
	}
}
