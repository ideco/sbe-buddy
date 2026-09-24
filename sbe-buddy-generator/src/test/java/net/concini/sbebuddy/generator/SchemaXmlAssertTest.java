package net.concini.sbebuddy.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** The comparison's own guarantees, which every corpus case relies on. */
final class SchemaXmlAssertTest {

	private static final String HEADER = """
			<composite name="messageHeader">
				<type name="blockLength" primitiveType="uint16"/>
				<type name="templateId" primitiveType="uint16"/>
				<type name="schemaId" primitiveType="uint16"/>
				<type name="version" primitiveType="uint16"/>
			</composite>
			""";

	@Test
	void parsingFillsTheXsdDefaults() {
		Document document = SchemaXmlAssert.parse("""
				<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="p" id="1" version="0">
					<types>%s</types>
					<sbe:message name="M" id="1">
						<field name="f" id="1" type="int32"/>
					</sbe:message>
				</sbe:messageSchema>
				""".formatted(HEADER));
		Element field = (Element) document.getElementsByTagName("field").item(0);
		Element root = document.getDocumentElement();

		assertThat(field.getAttribute("presence")).isEqualTo("required");
		assertThat(field.getAttribute("sinceVersion")).isEqualTo("0");
		assertThat(root.getAttribute("byteOrder")).isEqualTo("littleEndian");
		assertThat(root.getAttribute("headerType")).isEqualTo("messageHeader");
	}

	@Test
	void parsingRejectsWhatTheXsdRejects() {
		String oracle = """
				<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="p" id="1" version="0">
					<types>%s</types>
					<sbe:message name="M" id="70000"/>
				</sbe:messageSchema>
				""".formatted(HEADER);

		assertThatThrownBy(() -> SchemaXmlAssert.parse(oracle))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("70000");
	}
}
