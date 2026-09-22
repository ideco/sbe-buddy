package net.concini.sbebuddy.generator.corpus;

import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.INT;
import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.LONG;
import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.SHORT;
import static net.concini.sbebuddy.generator.Fixtures.annotatedComposite;
import static net.concini.sbebuddy.generator.Fixtures.annotatedData;
import static net.concini.sbebuddy.generator.Fixtures.annotatedField;
import static net.concini.sbebuddy.generator.Fixtures.annotatedGroup;
import static net.concini.sbebuddy.generator.Fixtures.annotatedMessage;
import static net.concini.sbebuddy.generator.Fixtures.annotatedSchema;
import static net.concini.sbebuddy.generator.Fixtures.annotatedType;
import static net.concini.sbebuddy.generator.Fixtures.composite;
import static net.concini.sbebuddy.generator.Fixtures.data;
import static net.concini.sbebuddy.generator.Fixtures.field;
import static net.concini.sbebuddy.generator.Fixtures.group;
import static net.concini.sbebuddy.generator.Fixtures.groupSizeEncoding;
import static net.concini.sbebuddy.generator.Fixtures.listOfRecord;
import static net.concini.sbebuddy.generator.Fixtures.message;
import static net.concini.sbebuddy.generator.Fixtures.messageHeader;
import static net.concini.sbebuddy.generator.Fixtures.messageSchema;
import static net.concini.sbebuddy.generator.Fixtures.primitive;
import static net.concini.sbebuddy.generator.Fixtures.text;
import static net.concini.sbebuddy.generator.Fixtures.type;
import static uk.co.real_logic.sbe.PrimitiveType.CHAR;
import static uk.co.real_logic.sbe.PrimitiveType.UINT16;
import static uk.co.real_logic.sbe.PrimitiveType.UINT8;

import net.concini.sbebuddy.generator.Annotated;
import net.concini.sbebuddy.generator.Fixtures.AnnotatedCompositeBuilder;
import net.concini.sbebuddy.generator.Schema;

/**
 * A group inside a group, a group holding var-data, and a group whose
 * dimensions are a composite of its own rather than the default.
 */
final class Groups {

	static final String PACKAGE_INFO = """
			@SbeSchema(id = 1, version = 0, codecs = false)
			package corpus.groups;

			import net.concini.sbebuddy.SbeSchema;
			""";

	static final String SOURCE = """
			package corpus.groups;

			import static net.concini.sbebuddy.PrimitiveType.CHAR;
			import static net.concini.sbebuddy.PrimitiveType.UINT16;
			import static net.concini.sbebuddy.PrimitiveType.UINT8;

			import java.util.List;

			import net.concini.sbebuddy.SbeComposite;
			import net.concini.sbebuddy.SbeData;
			import net.concini.sbebuddy.SbeField;
			import net.concini.sbebuddy.SbeGroup;
			import net.concini.sbebuddy.SbeMessage;
			import net.concini.sbebuddy.SbeType;

			@SbeComposite(name = "smallGroupSizeEncoding")
			record SmallGroupSizeEncoding(
					@SbeType(primitiveType = UINT8) short blockLength,
					@SbeType(primitiveType = UINT8) short numInGroup
			) {
			}

			@SbeComposite(name = "varStringEncoding")
			record VarStringEncoding(
					@SbeType(primitiveType = UINT16) int length,
					@SbeType(primitiveType = CHAR, length = 0, characterEncoding = "UTF-8") String varData
			) {
			}

			@SbeMessage(id = 1)
			record Groups(
					@SbeField(id = 1) long orderId,
					@SbeGroup(
							id = 10, blockLength = 8, semanticType = "NoLegs",
							description = "The legs of a multi-leg order",
							layout = {"legId", "allocations", "legNote"}
					) List<Leg> legs
			) {

				record Leg(
						@SbeData(id = 14, type = VarStringEncoding.class) String legNote,
						@SbeGroup(id = 12, dimensionType = SmallGroupSizeEncoding.class) List<Allocation> allocations,
						@SbeField(id = 11) int legId
				) {

					record Allocation(
							@SbeField(id = 13) int account
					) {
					}
				}
			}
			""";

	static final String XML = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.groups" id="1" version="0">
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
			        <composite name="smallGroupSizeEncoding">
			            <type name="blockLength" primitiveType="uint8"/>
			            <type name="numInGroup" primitiveType="uint8"/>
			        </composite>
			        <composite name="varStringEncoding">
			            <type name="length" primitiveType="uint16"/>
			            <type name="varData" primitiveType="char" length="0" characterEncoding="UTF-8"/>
			        </composite>
			    </types>
			    <sbe:message name="Groups" id="1">
			        <field name="orderId" id="1" type="int64"/>
			        <group name="legs" id="10" blockLength="8" semanticType="NoLegs" description="The legs of a multi-leg order">
			            <field name="legId" id="11" type="int32"/>
			            <group name="allocations" id="12" dimensionType="smallGroupSizeEncoding">
			                <field name="account" id="13" type="int32"/>
			            </group>
			            <data name="legNote" id="14" type="varStringEncoding"/>
			        </group>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private Groups() {
	}

	static Schema schema() {
		return messageSchema("corpus.groups", 1, 0)
				.types(
						messageHeader(),
						composite("smallGroupSizeEncoding").members(
								type("blockLength", UINT8),
								type("numInGroup", UINT8)
						),
						composite("varStringEncoding").members(
								type("length", UINT16),
								type("varData", CHAR).length(0).characterEncoding("UTF-8")
						),
						groupSizeEncoding()
				)
				.messages(
						message("Groups", 1)
								.fields(field("orderId", 1, "int64"))
								.groups(
										group("legs", 10)
												.blockLength(8)
												.semanticType("NoLegs")
												.description("The legs of a multi-leg order")
												.fields(field("legId", 11, "int32"))
												.groups(
														group("allocations", 12)
																.dimensionType("smallGroupSizeEncoding")
																.fields(field("account", 13, "int32"))
												)
												.data(data("legNote", 14, "varStringEncoding"))
								)
				)
				.build();
	}

	static Annotated annotated() {
		AnnotatedCompositeBuilder smallGroupSizeEncoding = annotatedComposite("SmallGroupSizeEncoding")
				.qualifiedName("corpus.groups.SmallGroupSizeEncoding")
				.name("smallGroupSizeEncoding")
				.members(
						annotatedType("blockLength", UINT8).javaType(primitive(SHORT)),
						annotatedType("numInGroup", UINT8).javaType(primitive(SHORT))
				);
		AnnotatedCompositeBuilder varStringEncoding = annotatedComposite("VarStringEncoding")
				.qualifiedName("corpus.groups.VarStringEncoding")
				.name("varStringEncoding")
				.members(
						annotatedType("length", UINT16).javaType(primitive(INT)),
						annotatedType("varData", CHAR).length(0).characterEncoding("UTF-8").javaType(text())
				);
		return annotatedSchema("corpus.groups", 1, 0).codecs(false)
				.types(smallGroupSizeEncoding, varStringEncoding)
				.messages(
						annotatedMessage("Groups", 1).components(
								annotatedField("orderId", 1, primitive(LONG)),
								annotatedGroup("legs", 10)
										.javaType(listOfRecord("corpus.groups.Groups.Leg"))
										.blockLength(8)
										.semanticType("NoLegs")
										.description("The legs of a multi-leg order")
										.layout("legId", "allocations", "legNote")
										.components(
												annotatedData("legNote", 14, text(), varStringEncoding),
												annotatedGroup("allocations", 12)
														.javaType(listOfRecord("corpus.groups.Groups.Leg.Allocation"))
														.dimensionType(smallGroupSizeEncoding)
														.components(
																annotatedField("account", 13, primitive(INT))
														),
												annotatedField("legId", 11, primitive(INT))
										)
						)
				)
				.build();
	}
}
