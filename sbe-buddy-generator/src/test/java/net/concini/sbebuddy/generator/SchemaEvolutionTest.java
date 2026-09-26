package net.concini.sbebuddy.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.generator.SchemaEquivalence.Difference;
import net.concini.sbebuddy.generator.SchemaEquivalence.Segment;

/**
 * A schema held against its baseline: what SBE's extension rules allow, what
 * breaks the baseline, and where each difference is named.
 */
final class SchemaEvolutionTest {

	private static final String BASELINE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="p" id="1" version="1">
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
			        <composite name="varStringEncoding">
			            <type name="length" primitiveType="uint16"/>
			            <type name="varData" primitiveType="char" length="0" characterEncoding="UTF-8"/>
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
			        <type name="Symbol" primitiveType="char" length="6"/>
			    </types>
			    <sbe:message name="Order" id="1">
			        <field name="orderId" id="1" type="int64"/>
			        <field name="qty" id="2" type="int32" sinceVersion="1"/>
			        <field name="side" id="3" type="Side"/>
			        <field name="flags" id="4" type="Flags"/>
			        <field name="price" id="5" type="Price"/>
			        <field name="symbol" id="6" type="Symbol"/>
			        <group name="legs" id="7">
			            <field name="legId" id="8" type="int32"/>
			        </group>
			        <data name="note" id="9" type="varStringEncoding"/>
			    </sbe:message>
			    <sbe:message name="Cancel" id="2">
			        <field name="orderId" id="1" type="int64"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	/** The baseline at the next version, changed by nothing. */
	private static final String SCHEMA = BASELINE.replace("version=\"1\">", "version=\"2\">");

	private static final Segment ORDER = new Segment("message", "Order");

	// ---- what the extension rules allow

	@Test
	void theBaselineAtALaterVersionBreaksNothing() {
		assertThat(SchemaEvolution.differences(SCHEMA, BASELINE)).isEmpty();
		assertThat(SchemaEvolution.differences(BASELINE, BASELINE)).isEmpty();
	}

	@Test
	void namesDescriptionsAndDeprecationAreFree() {
		String schema = SCHEMA.replace("name=\"Order\"", "name=\"NewOrder\" description=\"An order\"")
				.replace("name=\"orderId\" id=\"1\"", "name=\"clOrdId\" id=\"1\" deprecated=\"2\"")
				.replace("\"Price\"", "\"Decimal\"")
				.replace("name=\"BUY\"", "name=\"Buy\"")
				.replace("<type name=\"Symbol\"", "<type name=\"Ticker\"")
				.replace("type=\"Symbol\"", "type=\"Ticker\"")
				.replace("name=\"legs\"", "name=\"entries\"");

		assertThat(SchemaEvolution.differences(schema, BASELINE)).isEmpty();
	}

	@Test
	void aPrimitiveMayBecomeANamedTypeOfTheSameEncoding() {
		String schema = SCHEMA.replace("<types>", "<types>\n<type name=\"OrderId\" primitiveType=\"int64\"/>")
				.replace("name=\"orderId\" id=\"1\" type=\"int64\"", "name=\"orderId\" id=\"1\" type=\"OrderId\"");

		assertThat(SchemaEvolution.differences(schema, BASELINE)).isEmpty();
	}

	@Test
	void presenceMayChangeBetweenRequiredAndOptional() {
		String schema = SCHEMA.replace(
				"type=\"int32\" sinceVersion=\"1\"", "type=\"int32\" presence=\"optional\" sinceVersion=\"1\""
		);

		assertThat(SchemaEvolution.differences(schema, BASELINE)).isEmpty();
	}

	@Test
	void whatIsAppendedStatesAVersionAboveTheBaselines() {
		String schema = SCHEMA
				.replace(
						"<field name=\"symbol\" id=\"6\" type=\"Symbol\"/>",
						"<field name=\"symbol\" id=\"6\" type=\"Symbol\"/>\n<field name=\"account\" id=\"10\" type=\"int32\" sinceVersion=\"2\"/>"
				)
				.replace(
						"</group>",
						"</group>\n<group name=\"fills\" id=\"11\" sinceVersion=\"2\"><field name=\"qty\" id=\"12\" type=\"int32\"/></group>"
				)
				.replace(
						"<validValue name=\"SELL\">2</validValue>",
						"<validValue name=\"SELL\">2</validValue><validValue name=\"SHORT\" sinceVersion=\"2\">3</validValue>"
				)
				.replace(
						"</sbe:messageSchema>",
						"<sbe:message name=\"Fill\" id=\"3\" sinceVersion=\"2\"/></sbe:messageSchema>"
				);

		assertThat(SchemaEvolution.differences(schema, BASELINE)).isEmpty();
	}

	// ---- the schema

	@Test
	void theSchemaKeepsItsIdAndByteOrderAndIsNoOlderThanItsBaseline() {
		String schema = BASELINE.replace("id=\"1\" version=\"1\"", "id=\"3\" version=\"0\" byteOrder=\"bigEndian\"");

		assertThat(SchemaEvolution.differences(schema, BASELINE)).containsExactly(
				new Difference(List.of(), "the baseline is schema 1, not 3; a schema keeps its id"),
				new Difference(List.of(), "the baseline is version 1, above the schema's 0"),
				new Difference(
						List.of(), "the baseline is littleEndian, not bigEndian; the byte order never changes"
				)
		);
	}

	@Test
	void theHeaderNeverChanges() {
		String schema = SCHEMA.replace(
				"<type name=\"version\" primitiveType=\"uint16\"/>", "<type name=\"version\" primitiveType=\"uint8\"/>"
		);

		assertThat(SchemaEvolution.differences(schema, BASELINE)).containsExactly(
				new Difference(
						List.of(new Segment("composite", "messageHeader")),
						"the header differs from the baseline's: composite \"messageHeader\" at \"version\": type \"version\" has primitiveType=\"uint16\", not \"uint8\"; the header never changes"
				)
		);
	}

	// ---- messages

	@Test
	void aMessageIsMatchedByIdAndStays() {
		String schema = SCHEMA.replace("name=\"Cancel\" id=\"2\"", "name=\"Cancel\" id=\"3\"");

		assertThat(SchemaEvolution.differences(schema, BASELINE)).containsExactly(
				new Difference(
						List.of(),
						"the baseline has a message \"Cancel\" (id 2) and the schema none with that id; a message stays, deprecated if it is retired"
				),
				new Difference(
						List.of(new Segment("message", "Cancel")),
						"a message the baseline lacks needs a sinceVersion above the baseline's version 1"
				)
		);
	}

	@Test
	void aMessageKeepsItsVersionAndMeaning() {
		String schema = SCHEMA.replace("name=\"Order\" id=\"1\"", "name=\"Order\" id=\"1\" semanticType=\"D\"");

		assertThat(SchemaEvolution.differences(schema, BASELINE)).containsExactly(
				new Difference(List.of(ORDER), "the baseline has no semanticType here, not \"D\"")
		);
	}

	@Test
	void aBlockLengthOnlyGrowsAndStaysStated() {
		String baseline = BASELINE.replace("name=\"Order\" id=\"1\"", "name=\"Order\" id=\"1\" blockLength=\"64\"");

		assertThat(SchemaEvolution.differences(baseline.replace("\"64\"", "\"80\""), baseline)).isEmpty();
		assertThat(SchemaEvolution.differences(baseline.replace("\"64\"", "\"48\""), baseline)).containsExactly(
				new Difference(List.of(ORDER), "the baseline has blockLength=\"64\", above \"48\"; a block only grows")
		);
		assertThat(SchemaEvolution.differences(SCHEMA, baseline)).containsExactly(
				new Difference(
						List.of(ORDER),
						"the baseline has blockLength=\"64\" here, not none; a block length stays as stated"
				)
		);
	}

	// ---- fields, groups and var-data

	@Test
	void aFieldStays() {
		String schema = SCHEMA.replace("<field name=\"symbol\" id=\"6\" type=\"Symbol\"/>", "");

		assertThat(SchemaEvolution.differences(schema, BASELINE)).containsExactly(
				new Difference(
						List.of(ORDER),
						"the baseline's Order has a field \"symbol\" (id 6) the schema lacks; a field stays, unmapped if the record retires it"
				)
		);
	}

	@Test
	void aGroupAndVarDataStay() {
		String schema = SCHEMA.replaceAll("(?s)<group.*</group>", "")
				.replace("<data name=\"note\" id=\"9\" type=\"varStringEncoding\"/>", "");

		assertThat(SchemaEvolution.differences(schema, BASELINE)).containsExactly(
				new Difference(
						List.of(ORDER),
						"the baseline's Order has a group \"legs\" (id 7) the schema lacks; a group stays"
				),
				new Difference(
						List.of(ORDER),
						"the baseline's Order has var-data \"note\" (id 9) the schema lacks; var-data stays"
				)
		);
	}

	@Test
	void anAppendedMemberWithoutALaterVersionIsAProblemOnIt() {
		String schema = SCHEMA.replace(
				"<field name=\"symbol\" id=\"6\" type=\"Symbol\"/>",
				"<field name=\"symbol\" id=\"6\" type=\"Symbol\"/>\n<field name=\"account\" id=\"10\" type=\"int32\" sinceVersion=\"1\"/>"
		)
				.replace(
						"<field name=\"legId\" id=\"8\" type=\"int32\"/>",
						"<field name=\"legId\" id=\"8\" type=\"int32\"/><field name=\"ratio\" id=\"11\" type=\"int32\"/>"
				);

		assertThat(SchemaEvolution.differences(schema, BASELINE)).containsExactly(
				new Difference(
						List.of(ORDER, new Segment("field", "account")),
						"a field the baseline lacks needs a sinceVersion above the baseline's version 1"
				),
				new Difference(
						List.of(ORDER, new Segment("group", "legs"), new Segment("field", "ratio")),
						"a field the baseline lacks needs a sinceVersion above the baseline's version 1"
				)
		);
	}

	@Test
	void aFieldThatChangesItsIdIsAnotherField() {
		String schema = SCHEMA.replace(
				"name=\"orderId\" id=\"1\" type=\"int64\"/>\n        <field name=\"qty\"",
				"name=\"clOrdId\" id=\"11\" type=\"int64\"/>\n        <field name=\"qty\""
		);

		assertThat(SchemaEvolution.differences(schema, BASELINE)).containsExactly(
				new Difference(
						List.of(ORDER, new Segment("field", "clOrdId")),
						"the baseline has a field \"orderId\" (id 1) here, not id 11; the id is its identity, and a field that means something else is appended as a new one"
				)
		);
	}

	@Test
	void fieldsKeepTheirOrder() {
		String schema = SCHEMA.replace(
				"<field name=\"side\" id=\"3\" type=\"Side\"/>\n        <field name=\"flags\" id=\"4\" type=\"Flags\"/>",
				"<field name=\"flags\" id=\"4\" type=\"Flags\"/>\n        <field name=\"side\" id=\"3\" type=\"Side\"/>"
		);

		assertThat(SchemaEvolution.differences(schema, BASELINE)).contains(
				new Difference(
						List.of(ORDER, new Segment("field", "flags")),
						"the baseline has a field \"side\" (id 3) here, not id 4; the id is its identity, and a field that means something else is appended as a new one"
				),
				new Difference(
						List.of(ORDER, new Segment("field", "flags")),
						"the type differs from the baseline's: enum \"Side\", not set \"Flags\""
				)
		);
	}

	@Test
	void aFieldKeepsItsType() {
		String schema = SCHEMA
				.replace(
						"name=\"orderId\" id=\"1\" type=\"int64\"/>\n        <field name=\"qty\"",
						"name=\"orderId\" id=\"1\" type=\"int32\"/>\n        <field name=\"qty\""
				)
				.replace("type=\"Price\"", "type=\"int64\"")
				.replace(
						"<type name=\"Symbol\" primitiveType=\"char\" length=\"6\"/>",
						"<type name=\"Symbol\" primitiveType=\"char\" length=\"8\"/>"
				);

		assertThat(SchemaEvolution.differences(schema, BASELINE)).containsExactly(
				new Difference(
						List.of(ORDER, new Segment("field", "orderId")),
						"the type differs from the baseline's: int64, not int32"
				),
				new Difference(
						List.of(ORDER, new Segment("field", "price")),
						"the type differs from the baseline's: composite \"Price\", not int64"
				),
				new Difference(
						List.of(ORDER, new Segment("field", "symbol")),
						"the type differs from the baseline's: type \"Symbol\" has length=\"6\", not \"8\""
				)
		);
	}

	@Test
	void aFieldKeepsItsVersionOffsetAndMeaning() {
		String schema = SCHEMA.replace("type=\"int32\" sinceVersion=\"1\"", "type=\"int32\" sinceVersion=\"2\"")
				.replace(
						"name=\"orderId\" id=\"1\" type=\"int64\"/>\n        <field name=\"qty\"",
						"name=\"orderId\" id=\"1\" type=\"int64\" timeUnit=\"millisecond\"/>\n        <field name=\"qty\""
				)
				.replace("type=\"Symbol\"/>", "type=\"Symbol\" offset=\"40\"/>");

		assertThat(SchemaEvolution.differences(schema, BASELINE)).containsExactly(
				new Difference(
						List.of(ORDER, new Segment("field", "orderId")),
						"the baseline has timeUnit=\"nanosecond\" here, not \"millisecond\""
				),
				new Difference(
						List.of(ORDER, new Segment("field", "qty")),
						"the baseline has sinceVersion=\"1\" here, not \"2\""
				),
				new Difference(
						List.of(ORDER, new Segment("field", "symbol")), "the baseline has no offset here, not \"40\""
				)
		);
	}

	@Test
	void aFieldNeverBecomesAConstant() {
		String schema = SCHEMA
				.replace("type=\"Side\"/>", "type=\"Side\" presence=\"constant\" valueRef=\"Side.BUY\"/>");

		assertThat(SchemaEvolution.differences(schema, BASELINE)).containsExactly(
				new Difference(
						List.of(ORDER, new Segment("field", "side")),
						"the baseline has presence=\"required\" here, not \"constant\"; a constant takes no space in the block"
				),
				new Difference(
						List.of(ORDER, new Segment("field", "side")),
						"the baseline's constant here is none, not \"1\"; a constant keeps its value"
				)
		);
	}

	@Test
	void aGroupKeepsItsIdAndDimensions() {
		String schema = SCHEMA
				.replace(
						"<group name=\"legs\" id=\"7\">", "<group name=\"legs\" id=\"17\" dimensionType=\"smallGroup\">"
				)
				.replace(
						"<type name=\"Symbol\"",
						"<composite name=\"smallGroup\"><type name=\"blockLength\" primitiveType=\"uint16\"/><type name=\"numInGroup\" primitiveType=\"uint8\"/></composite>\n<type name=\"Symbol\""
				);

		assertThat(SchemaEvolution.differences(schema, BASELINE)).containsExactly(
				new Difference(
						List.of(ORDER, new Segment("group", "legs")),
						"the baseline has a group \"legs\" (id 7) here, not id 17; the id is its identity, and a group that means something else is appended as a new one"
				),
				new Difference(
						List.of(ORDER, new Segment("group", "legs")),
						"the dimensions differ from the baseline's: composite \"groupSizeEncoding\" at \"numInGroup\": type \"numInGroup\" has primitiveType=\"uint16\", not \"uint8\"; a group keeps its dimensions"
				)
		);
	}

	// ---- types

	@Test
	void aCompositeKeepsItsMembers() {
		String grown = SCHEMA.replace(
				"<type name=\"exponent\" primitiveType=\"int8\" presence=\"constant\">-2</type>",
				"<type name=\"exponent\" primitiveType=\"int8\" presence=\"constant\">-2</type><type name=\"scale\" primitiveType=\"int8\"/>"
		);
		String changed = SCHEMA.replace(
				"<type name=\"exponent\" primitiveType=\"int8\" presence=\"constant\">-2</type>",
				"<type name=\"exponent\" primitiveType=\"int8\" presence=\"constant\">-4</type>"
		);
		String notConstant = SCHEMA.replace(
				"<type name=\"exponent\" primitiveType=\"int8\" presence=\"constant\">-2</type>",
				"<type name=\"exponent\" primitiveType=\"int8\"/>"
		);
		List<Segment> price = List.of(ORDER, new Segment("field", "price"));

		assertThat(SchemaEvolution.differences(grown, BASELINE)).containsExactly(
				new Difference(
						price,
						"the type differs from the baseline's: composite \"Price\" has 2 members, not 3; a composite keeps its members"
				)
		);
		assertThat(SchemaEvolution.differences(changed, BASELINE)).containsExactly(
				new Difference(
						price,
						"the type differs from the baseline's: composite \"Price\" at \"exponent\": type \"exponent\" has the constant \"-2\", not \"-4\""
				)
		);
		assertThat(SchemaEvolution.differences(notConstant, BASELINE)).containsExactly(
				new Difference(
						price,
						"the type differs from the baseline's: composite \"Price\" at \"exponent\": type \"exponent\" has presence=\"constant\", not \"required\""
				)
		);
	}

	@Test
	void aRefAndAnInlineMemberOfTheSameTypeAreOne() {
		String baseline = BASELINE.replace(
				"<type name=\"mantissa\" primitiveType=\"int64\"/>", "<ref name=\"mantissa\" type=\"Mantissa\"/>"
		).replace("<types>", "<types>\n<type name=\"Mantissa\" primitiveType=\"int64\"/>");

		assertThat(SchemaEvolution.differences(SCHEMA, baseline)).isEmpty();
	}

	@Test
	void anEnumValueStaysAndOneAddedStatesItsVersion() {
		String removed = SCHEMA.replace("<validValue name=\"SELL\">2</validValue>", "");
		String added = SCHEMA.replace(
				"<validValue name=\"SELL\">2</validValue>",
				"<validValue name=\"SELL\">2</validValue><validValue name=\"SHORT\">3</validValue>"
		);
		String moved = SCHEMA.replace(
				"<validValue name=\"SELL\">2</validValue>",
				"<validValue name=\"SELL\" sinceVersion=\"1\">2</validValue>"
		);
		List<Segment> side = List.of(ORDER, new Segment("field", "side"));

		assertThat(SchemaEvolution.differences(removed, BASELINE)).containsExactly(
				new Difference(
						side,
						"the type differs from the baseline's: enum \"Side\" has the value \"2\" (SELL), which the schema's lacks; values stay, deprecated if they are retired"
				)
		);
		assertThat(SchemaEvolution.differences(added, BASELINE)).containsExactly(
				new Difference(
						side,
						"the type differs from the baseline's: enum \"Side\" lacks the value \"3\" (SHORT), which needs a sinceVersion above the baseline's version 1"
				)
		);
		assertThat(SchemaEvolution.differences(moved, BASELINE)).containsExactly(
				new Difference(
						side,
						"the type differs from the baseline's: enum \"Side\" has the value \"2\" (SELL) since version 0, not 1"
				)
		);
	}

	@Test
	void anEnumKeepsItsEncoding() {
		String schema = SCHEMA
				.replace("<enum name=\"Side\" encodingType=\"char\">", "<enum name=\"Side\" encodingType=\"uint8\">");

		assertThat(SchemaEvolution.differences(schema, BASELINE)).containsExactly(
				new Difference(
						List.of(ORDER, new Segment("field", "side")),
						"the type differs from the baseline's: enum \"Side\" is encoded as char, not uint8"
				)
		);
	}

	@Test
	void aSetsChoicesAreMatchedByBit() {
		String renamed = SCHEMA.replace("<choice name=\"urgent\">0</choice>", "<choice name=\"rush\">0</choice>");
		String moved = SCHEMA.replace("<choice name=\"urgent\">0</choice>", "<choice name=\"urgent\">1</choice>");

		assertThat(SchemaEvolution.differences(renamed, BASELINE)).isEmpty();
		assertThat(SchemaEvolution.differences(moved, BASELINE)).containsExactly(
				new Difference(
						List.of(ORDER, new Segment("field", "flags")),
						"the type differs from the baseline's: set \"Flags\" has the choice \"0\" (urgent), which the schema's lacks; choices stay, deprecated if they are retired"
				)
		);
	}

	// ---- the documents

	@Test
	void theBaselinesVersionIsItsDocuments() {
		assertThat(SchemaEvolution.version(BASELINE)).isEqualTo(1);
	}

	@Test
	void aBaselineSbeXsdRefusesIsAnIllegalArgument() {
		assertThatThrownBy(() -> SchemaEvolution.differences(SCHEMA, "<messageSchema/>"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageStartingWith("not a valid SBE schema");
	}
}
