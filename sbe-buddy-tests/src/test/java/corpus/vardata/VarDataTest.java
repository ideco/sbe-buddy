package corpus.vardata;

import static net.concini.sbebuddy.tests.WriterAssert.assertWritesTheCodecsBytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.tests.SchemaCase;

import corpus.vardata.VarData.Attachment;
import corpus.vardata.sbe.MessageHeaderDecoder;
import corpus.vardata.sbe.VarDataDecoder;
import corpus.vardata.sbe.VarDataWriter;

/**
 * Variable-length data: text with a character encoding, opaque bytes without
 * one, a length type wide enough to need its own maxValue, and var-data inside
 * a group's entry. The round trips carry every data empty, UTF-8 text of every
 * width a character takes with bytes of every value, and attachments empty and
 * full beside a signature at its length type's maximum; the tests hold a null
 * refused in the message and in an entry, the maxima of bytes and of UTF-8
 * text, a lone surrogate, and the note's UTF-8 as the flyweight reads it.
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

	private static final int OFFSET = 8;

	@Override
	public List<RoundTrip<?>> roundTrips() {
		return List.of(
				new RoundTrip<>(
						"every data empty, no attachments", new VarDataCodec(),
						new VarData(1, List.of(), "", new byte[0], new byte[0])
				),
				new RoundTrip<>(
						"text of one, two, three and four bytes a character, bytes of every value", new VarDataCodec(),
						new VarData(2, List.of(), "a é € 😀", everyByte(), new byte[]{-1, 0, 1})
				),
				new RoundTrip<>(
						"attachments empty and full, the signature at the uint8 length's maximum", new VarDataCodec(),
						new VarData(
								3, List.of(new Attachment(1, new byte[0]), new Attachment(2, filled(254))), "note",
								new byte[]{42}, filled(254)
						)
				)
		);
	}

	@Test
	void aNullNoteIsRefused() {
		VarDataCodec codec = new VarDataCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		VarData value = new VarData(1, List.of(), null, new byte[0], new byte[0]);

		assertThatThrownBy(() -> codec.encodedLength(value))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("note is required");
		assertThatThrownBy(() -> codec.encode(value, buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("note is required");
	}

	@Test
	void aNullContentInAnAttachmentIsRefused() {
		VarDataCodec codec = new VarDataCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		VarData value = new VarData(1, List.of(new Attachment(1, null)), "", new byte[0], new byte[0]);

		assertThatThrownBy(() -> codec.encodedLength(value))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("content is required");
		assertThatThrownBy(() -> codec.encode(value, buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("content is required");
	}

	@Test
	void aSignatureLongerThanItsLengthTypeHoldsIsRefused() {
		VarDataCodec codec = new VarDataCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[512]);
		VarData value = new VarData(1, List.of(), "", new byte[0], filled(255));

		assertThatThrownBy(() -> codec.encodedLength(value))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("signature is longer than 254 bytes: 255");
		assertThatThrownBy(() -> codec.encode(value, buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("signature is longer than 254 bytes: 255");
	}

	@Test
	void aNoteLongerInUtf8ThanItsLengthTypeHoldsIsRefused() {
		// 32,767 two-byte characters fit in 65,534 bytes; one more does not.
		VarDataCodec codec = new VarDataCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[70_000]);
		VarData fits = new VarData(1, List.of(), "é".repeat(32_767), new byte[0], new byte[0]);
		VarData over = new VarData(1, List.of(), "é".repeat(32_768), new byte[0], new byte[0]);

		assertThat(codec.encode(fits, buffer, OFFSET)).isEqualTo(codec.encodedLength(fits));
		assertThatThrownBy(() -> codec.encodedLength(over))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("note is longer than 65534 bytes in UTF-8: 65536");
		assertThatThrownBy(() -> codec.encode(over, buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("note is longer than 65534 bytes in UTF-8: 65536");
	}

	@Test
	void aLoneSurrogateIsRefused() {
		// String.getBytes would write each as '?', a silent loss.
		VarDataCodec codec = new VarDataCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);

		assertThatThrownBy(
				() -> codec.encode(new VarData(1, List.of(), "a\uD83D", new byte[0], new byte[0]), buffer, OFFSET)
		)
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("note has a lone surrogate at index 1");
		assertThatThrownBy(() -> codec.encodedLength(new VarData(1, List.of(), "\uDE00a", new byte[0], new byte[0])))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("note has a lone surrogate at index 0");
	}

	@Test
	void theNoteIsUtf8OnTheWire() {
		VarDataCodec codec = new VarDataCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		codec.encode(new VarData(1, List.of(), "é😀", new byte[0], new byte[0]), buffer, OFFSET);
		MessageHeaderDecoder header = new MessageHeaderDecoder().wrap(buffer, OFFSET);
		VarDataDecoder decoder = new VarDataDecoder().wrap(
				buffer, OFFSET + MessageHeaderDecoder.ENCODED_LENGTH, header.blockLength(), header.version()
		);
		decoder.attachments();

		assertThat(decoder.noteLength()).isEqualTo(6);
		assertThat(decoder.note()).isEqualTo("é😀");
	}

	/**
	 * An entry's var-data hands back its group; the root's follow the group in wire
	 * order.
	 */
	@Test
	void theWriterWritesTheCodecsBytesForAttachmentsAndEveryData() {
		assertWritesTheCodecsBytes(
				new VarDataCodec(),
				new VarData(
						3, List.of(new Attachment(1, new byte[0]), new Attachment(2, filled(254))), "note",
						new byte[]{42}, filled(254)
				),
				(buffer, offset) -> new VarDataWriter().wrap(buffer, offset)
						.orderId(3)
						.attachments()
						.entry().kind(1).putContent(new byte[0], 0, 0)
						.entry().kind(2).putContent(filled(254), 0, 254)
						.end()
						.note("note")
						.putPayload(new byte[]{42}, 0, 1)
						.putSignature(filled(254), 0, 254)
						.length()
		);
	}

	private static byte[] everyByte() {
		byte[] bytes = new byte[256];
		for (int i = 0; i < bytes.length; i++) {
			bytes[i] = (byte) i;
		}
		return bytes;
	}

	private static byte[] filled(int length) {
		byte[] bytes = new byte[length];
		Arrays.fill(bytes, (byte) 7);
		return bytes;
	}
}
