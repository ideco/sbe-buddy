package corpus.varencodings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.tests.SchemaCase;

import corpus.varencodings.sbe.MessageHeaderEncoder;

/**
 * The api's built-in var-data encodings: UTF-8 text, ASCII text and bytes, and
 * UTF-8 text appended in version 1. The round trips carry every data empty and
 * every data full of what its encoding holds; the tests hold a version 0 header
 * decoding the appended comment null, and the ASCII text's refusals.
 */
final class VarEncodingsTest implements SchemaCase {

	static final String ORACLE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.varencodings" id="1" version="1">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			        <composite name="varStringEncoding">
			            <type name="length" primitiveType="uint16"/>
			            <type name="varData" primitiveType="char" length="0" characterEncoding="UTF-8"/>
			        </composite>
			        <composite name="varAsciiEncoding">
			            <type name="length" primitiveType="uint16"/>
			            <type name="varData" primitiveType="char" length="0" characterEncoding="US-ASCII"/>
			        </composite>
			        <composite name="varDataEncoding">
			            <type name="length" primitiveType="uint16"/>
			            <type name="varData" primitiveType="uint8" length="0"/>
			        </composite>
			    </types>
			    <sbe:message name="VarEncodings" id="1">
			        <field name="id" id="1" type="int32"/>
			        <data name="text" id="2" type="varStringEncoding"/>
			        <data name="symbol" id="3" type="varAsciiEncoding"/>
			        <data name="blob" id="4" type="varDataEncoding"/>
			        <data name="comment" id="5" type="varStringEncoding" sinceVersion="1"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private static final int OFFSET = 8;

	@Override
	public String description() {
		return "VarEncodings: the api's UTF-8 text, ASCII text and bytes, UTF-8 text appended in version 1";
	}

	@Override
	public String oracle() {
		return ORACLE;
	}

	@Override
	public List<RoundTrip<?>> roundTrips() {
		return List.of(
				new RoundTrip<>(
						"every data empty", new VarEncodingsCodec(), new VarEncodings(1, "", "", new byte[0], "")
				),
				new RoundTrip<>(
						"text beyond the Basic Multilingual Plane, ASCII's every character, bytes, a comment",
						new VarEncodingsCodec(),
						new VarEncodings(2, "𝄞 clef, ß, 中文", everyAscii(), new byte[]{0, -128, 127, -1}, "appended")
				)
		);
	}

	@Test
	void aHeaderAtVersion0DecodesWithTheAppendedCommentNull() {
		VarEncodingsCodec codec = new VarEncodingsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		codec.encode(new VarEncodings(1, "text", "SYM", new byte[]{1}, "comment"), buffer, OFFSET);
		new MessageHeaderEncoder().wrap(buffer, OFFSET).version(0);

		VarEncodings decoded = codec.decode(buffer, OFFSET);

		assertThat(decoded).isEqualTo(new VarEncodings(1, "text", "SYM", decoded.blob(), null));
		assertThat(decoded.blob()).containsExactly(1);
		assertThat(codec.lastDecodedLength()).isEqualTo(codec.decodedLength(buffer, OFFSET));
	}

	@Test
	void aNullAppendedCommentIsRefused() {
		// Encoding writes the current version, which carries the comment.
		VarEncodingsCodec codec = new VarEncodingsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		VarEncodings value = new VarEncodings(1, "", "", new byte[0], null);

		assertThatThrownBy(() -> codec.encodedLength(value))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("comment is required");
		assertThatThrownBy(() -> codec.encode(value, buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("comment is required");
	}

	@Test
	void aNonAsciiSymbolIsRefused() {
		VarEncodingsCodec codec = new VarEncodingsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		VarEncodings value = new VarEncodings(1, "", "café", new byte[0], "");

		assertThatThrownBy(() -> codec.encodedLength(value))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("symbol is not ASCII: café");
		assertThatThrownBy(() -> codec.encode(value, buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("symbol is not ASCII: café");
	}

	@Test
	void aSymbolLongerThanItsLengthTypeHoldsIsRefused() {
		VarEncodingsCodec codec = new VarEncodingsCodec();
		String symbol = "S".repeat(65_535);
		VarEncodings value = new VarEncodings(1, "", symbol, new byte[0], "");

		assertThatThrownBy(() -> codec.encodedLength(value))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("symbol is longer than 65534: " + symbol);
	}

	private static String everyAscii() {
		StringBuilder ascii = new StringBuilder();
		for (char c = 0; c < 128; c++) {
			ascii.append(c);
		}
		return ascii.toString();
	}
}
