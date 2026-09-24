package net.concini.sbebuddy.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.generator.SchemaEquivalence.Difference;
import net.concini.sbebuddy.generator.SchemaEquivalence.Segment;

/**
 * The equivalence between a package's annotations and its resource, and between
 * a written schema and its oracle: what counts as one schema, and how each
 * difference is named.
 */
final class SchemaEquivalenceTest {

	private static final String SCHEMA = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="p" id="1" version="2">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			        <composite name="groupSizeEncoding">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="numInGroup" primitiveType="uint16"/>
			        </composite>
			        <composite name="Price">
			            <type name="mantissa" primitiveType="int64"/>
			            <type name="exponent" primitiveType="int8" presence="constant">-2</type>
			        </composite>
			        <enum name="Side" encodingType="char">
			            <validValue name="BUY">1</validValue>
			            <validValue name="SELL">2</validValue>
			        </enum>
			        <set name="Flags" encodingType="uint8">
			            <choice name="urgent">0</choice>
			        </set>
			    </types>
			    <sbe:message name="Order" id="1">
			        <field name="orderId" id="1" type="int64"/>
			        <field name="qty" id="2" type="int32" presence="optional" sinceVersion="1"/>
			        <field name="side" id="3" type="Side"/>
			        <field name="price" id="4" type="Price"/>
			        <group name="legs" id="5">
			            <field name="legId" id="6" type="int32"/>
			        </group>
			    </sbe:message>
			    <sbe:message name="Cancel" id="2">
			        <field name="orderId" id="1" type="int64"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	@Test
	void theXsdsDefaultsAndTheOrderOfDeclarationsAndMessagesDoNotCount() {
		String withDefaults = SCHEMA
				.replace(
						"<field name=\"orderId\" id=\"1\" type=\"int64\"/>",
						"<field name=\"orderId\" id=\"1\" type=\"int64\" presence=\"required\" sinceVersion=\"0\"/>"
				)
				.replace("version=\"2\">", "version=\"2\" byteOrder=\"littleEndian\" headerType=\"messageHeader\">");
		String reordered = swap(
				swap(
						SCHEMA, "    <sbe:message name=\"Order\"", "    <sbe:message name=\"Cancel\"",
						"</sbe:messageSchema>"
				), "        <composite name=\"Price\">", "        <enum name=\"Side\"", "        <set name=\"Flags\""
		);

		assertThat(SchemaEquivalence.differences(SCHEMA, withDefaults)).isEmpty();
		assertThat(SchemaEquivalence.differences(SCHEMA, reordered)).isEmpty();
	}

	@Test
	void anAttributeIsNamedOnItsNode() {
		String schema = SCHEMA.replace("id=\"1\" version=\"2\"", "id=\"1\" version=\"3\"")
				.replace(
						"<field name=\"qty\" id=\"2\" type=\"int32\" presence=\"optional\"",
						"<field name=\"qty\" id=\"2\" type=\"int32\""
				)
				.replace("<field name=\"legId\" id=\"6\"", "<field name=\"legId\" id=\"7\"");

		assertThat(SchemaEquivalence.differences(SCHEMA, schema)).containsExactlyInAnyOrder(
				new Difference(List.of(), "the schema has version=\"3\", not \"2\""),
				new Difference(
						List.of(new Segment("message", "Order"), new Segment("field", "qty")),
						"the schema has presence=\"required\", not \"optional\""
				),
				new Difference(
						List.of(
								new Segment("message", "Order"), new Segment("group", "legs"),
								new Segment("field", "legId")
						),
						"the schema has id=\"7\", not \"6\""
				)
		);
	}

	@Test
	void aMessageOrAMemberOneSideLacksIsNamedFromTheAnnotationsSide() {
		String schema = SCHEMA
				.replace("        <field name=\"side\" id=\"3\" type=\"Side\"/>\n", "")
				.replace(
						"    <sbe:message name=\"Cancel\" id=\"2\">", "    <sbe:message name=\"Replace\" id=\"3\">\n"
								+ "        <field name=\"orderId\" id=\"1\" type=\"int64\"/>\n"
								+ "        <group name=\"legs\" id=\"5\">\n"
								+ "            <field name=\"legId\" id=\"6\" type=\"int32\"/>\n"
								+ "        </group>\n"
								+ "    </sbe:message>\n"
								+ "    <sbe:message name=\"Cancel\" id=\"2\">"
				)
				.replace(
						"<sbe:message name=\"Order\" id=\"1\">",
						"<sbe:message name=\"Order\" id=\"1\">\n        <field name=\"account\" id=\"9\" type=\"int32\"/>"
				);

		assertThat(SchemaEquivalence.differences(SCHEMA, schema)).containsExactlyInAnyOrder(
				new Difference(
						List.of(new Segment("message", "Order"), new Segment("field", "side")),
						"the schema's Order has no field named \"side\""
				),
				new Difference(
						List.of(new Segment("message", "Order"), new Segment("field", "account")),
						"the schema's Order has a field \"account\" no component carries; add it, or declare it unmapped"
				),
				new Difference(
						List.of(new Segment("message", "Replace")),
						"the schema has a message \"Replace\" (id 3) and no record maps it"
				)
		);
		assertThat(SchemaEquivalence.differences(schema, SCHEMA)).extracting(Difference::message).contains(
				"the schema has no message named \"Replace\"", "the schema's Order has no field named \"account\"",
				"the schema's Order has a field \"side\" no component carries; add it, or declare it unmapped"
		);
	}

	@Test
	void aDeclarationOneSideLacksAndAValueOneSideLacksAreNamed() {
		String schema = SCHEMA.replace("<set name=\"Flags\"", "<set name=\"Options\"")
				.replace("<validValue name=\"SELL\">2</validValue>", "<validValue name=\"SHORT\">5</validValue>")
				.replace(
						"<type name=\"exponent\" primitiveType=\"int8\" presence=\"constant\">-2</type>",
						"<type name=\"exponent\" primitiveType=\"int8\" presence=\"constant\">-4</type>"
				);

		assertThat(SchemaEquivalence.differences(SCHEMA, schema)).containsExactlyInAnyOrder(
				new Difference(List.of(new Segment("set", "Flags")), "the schema has no set named \"Flags\""),
				new Difference(
						List.of(new Segment("set", "Options")),
						"the schema has a set \"Options\" and no declaration maps it"
				),
				new Difference(
						List.of(new Segment("enum", "Side"), new Segment("validValue", "SELL")),
						"the schema's Side has no value named \"SELL\""
				),
				new Difference(
						List.of(new Segment("enum", "Side"), new Segment("validValue", "SHORT")),
						"the schema's Side has a value named \"SHORT\" and no constant maps it"
				),
				new Difference(
						List.of(new Segment("composite", "Price"), new Segment("type", "exponent")),
						"the schema has the value \"-4\", not \"-2\""
				)
		);
	}

	@Test
	void theOrderOfABlocksMembersCounts() {
		String schema = SCHEMA.replace(
				"        <field name=\"orderId\" id=\"1\" type=\"int64\"/>\n        <field name=\"qty\" id=\"2\" type=\"int32\" presence=\"optional\" sinceVersion=\"1\"/>\n",
				"        <field name=\"qty\" id=\"2\" type=\"int32\" presence=\"optional\" sinceVersion=\"1\"/>\n        <field name=\"orderId\" id=\"1\" type=\"int64\"/>\n"
		);

		assertThat(SchemaEquivalence.differences(SCHEMA, schema)).containsExactly(
				new Difference(
						List.of(new Segment("message", "Order")),
						"the schema's Order has its members in the order [qty, orderId, side, price, legs], not [orderId, qty, side, price, legs]"
				)
		);
	}

	@Test
	void aDocumentTheXsdRejectsIsRefused() {
		assertThatThrownBy(() -> SchemaEquivalence.differences(SCHEMA, SCHEMA.replace("id=\"2\">", "id=\"70000\">")))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("70000");
	}

	/**
	 * The block from {@code first} to {@code second} swapped with the one from
	 * there to {@code end}.
	 */
	private static String swap(String schema, String first, String second, String end) {
		int a = schema.indexOf(first);
		int b = schema.indexOf(second);
		int c = schema.indexOf(end);
		return schema.substring(0, a) + schema.substring(b, c) + schema.substring(a, b) + schema.substring(c);
	}
}
