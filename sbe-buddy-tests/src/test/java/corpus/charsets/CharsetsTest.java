package corpus.charsets;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.tests.SchemaCase;

/**
 * Text in encodings other than ASCII, each encoded by the codec, which refuses
 * what the encoding cannot hold where the flyweight would write '?': char
 * arrays in ISO-8859-1 and UTF-8, padded with zeros, and var-data in
 * windows-1252 and UTF-16. The round trips carry everything empty and every
 * char array full; the tests hold the refusals.
 */
final class CharsetsTest implements SchemaCase {

	static final String ORACLE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.charsets" id="1" version="0">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			        <type name="Latin1Name" primitiveType="char" length="8" characterEncoding="ISO-8859-1"/>
			        <type name="Utf8Name" primitiveType="char" length="8" characterEncoding="UTF-8"/>
			        <composite name="varWesternEncoding">
			            <type name="length" primitiveType="uint16"/>
			            <type name="varData" primitiveType="char" length="0" characterEncoding="windows-1252"/>
			        </composite>
			        <composite name="varUtf16Encoding">
			            <type name="length" primitiveType="uint16"/>
			            <type name="varData" primitiveType="char" length="0" characterEncoding="UTF-16"/>
			        </composite>
			    </types>
			    <sbe:message name="Charsets" id="1">
			        <field name="latin" id="1" type="Latin1Name"/>
			        <field name="unicode" id="2" type="Utf8Name"/>
			        <data name="western" id="3" type="varWesternEncoding"/>
			        <data name="wide" id="4" type="varUtf16Encoding"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private static final int OFFSET = 8;

	@Override
	public String description() {
		return "Charsets: char arrays in ISO-8859-1 and UTF-8, var-data in windows-1252 and UTF-16";
	}

	@Override
	public String oracle() {
		return ORACLE;
	}

	@Override
	public List<RoundTrip<?>> roundTrips() {
		return List.of(
				new RoundTrip<>("everything empty", new CharsetsCodec(), new Charsets("", "", "", "")),
				new RoundTrip<>(
						"char arrays full, eight bytes in each encoding, text beyond the Basic Multilingual Plane",
						new CharsetsCodec(), new Charsets("Grüße!!!", "héllo!!", "€ café", "𝄞 clef, 中文")
				)
		);
	}

	@Test
	void aCharacterTheEncodingCannotHoldIsRefused() {
		assertRefused(new Charsets("€", "", "", ""), "latin cannot be written in ISO-8859-1: €");
		assertRefused(new Charsets("", "", "中文", ""), "western cannot be written in windows-1252: 中文");
	}

	@Test
	void aLoneSurrogateIsRefused() {
		assertRefused(new Charsets("", "", "", "a\uD834"), "wide cannot be written in UTF-16: a\uD834");
		assertRefused(new Charsets("", "\uD834", "", ""), "unicode cannot be written in UTF-8: \uD834");
	}

	@Test
	void aCharArrayLongerInBytesThanItsLengthIsRefused() {
		assertRefused(new Charsets("", "日本語", "", ""), "unicode is longer than 8 bytes in UTF-8: 9");
	}

	private static void assertRefused(Charsets value, String message) {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);

		assertThatThrownBy(() -> new CharsetsCodec().encode(value, buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage(message);
	}
}
