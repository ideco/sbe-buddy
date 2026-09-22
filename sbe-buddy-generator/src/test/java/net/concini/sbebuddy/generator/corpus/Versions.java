package net.concini.sbebuddy.generator.corpus;

import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.INT;
import static net.concini.sbebuddy.generator.Fixtures.annotatedChoice;
import static net.concini.sbebuddy.generator.Fixtures.annotatedComposite;
import static net.concini.sbebuddy.generator.Fixtures.annotatedData;
import static net.concini.sbebuddy.generator.Fixtures.annotatedEnum;
import static net.concini.sbebuddy.generator.Fixtures.annotatedEnumValue;
import static net.concini.sbebuddy.generator.Fixtures.annotatedField;
import static net.concini.sbebuddy.generator.Fixtures.annotatedGroup;
import static net.concini.sbebuddy.generator.Fixtures.annotatedMessage;
import static net.concini.sbebuddy.generator.Fixtures.annotatedRef;
import static net.concini.sbebuddy.generator.Fixtures.annotatedSchema;
import static net.concini.sbebuddy.generator.Fixtures.annotatedSet;
import static net.concini.sbebuddy.generator.Fixtures.annotatedType;
import static net.concini.sbebuddy.generator.Fixtures.boxed;
import static net.concini.sbebuddy.generator.Fixtures.choice;
import static net.concini.sbebuddy.generator.Fixtures.composite;
import static net.concini.sbebuddy.generator.Fixtures.data;
import static net.concini.sbebuddy.generator.Fixtures.declared;
import static net.concini.sbebuddy.generator.Fixtures.enumeration;
import static net.concini.sbebuddy.generator.Fixtures.field;
import static net.concini.sbebuddy.generator.Fixtures.group;
import static net.concini.sbebuddy.generator.Fixtures.groupSizeEncoding;
import static net.concini.sbebuddy.generator.Fixtures.message;
import static net.concini.sbebuddy.generator.Fixtures.messageHeader;
import static net.concini.sbebuddy.generator.Fixtures.messageSchema;
import static net.concini.sbebuddy.generator.Fixtures.primitive;
import static net.concini.sbebuddy.generator.Fixtures.ref;
import static net.concini.sbebuddy.generator.Fixtures.set;
import static net.concini.sbebuddy.generator.Fixtures.text;
import static net.concini.sbebuddy.generator.Fixtures.type;
import static net.concini.sbebuddy.generator.Fixtures.validValue;
import static uk.co.real_logic.sbe.PrimitiveType.CHAR;
import static uk.co.real_logic.sbe.PrimitiveType.INT32;
import static uk.co.real_logic.sbe.PrimitiveType.UINT16;
import static uk.co.real_logic.sbe.PrimitiveType.UINT8;

import net.concini.sbebuddy.generator.Annotated;
import net.concini.sbebuddy.generator.Fixtures.AnnotatedCompositeBuilder;
import net.concini.sbebuddy.generator.Fixtures.AnnotatedEnumBuilder;
import net.concini.sbebuddy.generator.Fixtures.AnnotatedSetBuilder;
import net.concini.sbebuddy.generator.Fixtures.AnnotatedTypeBuilder;
import net.concini.sbebuddy.generator.Schema;

/**
 * Every node kind carrying both version attributes, under a schema old enough
 * to declare them: sbe-tool rejects a sinceVersion above the schema's version.
 */
final class Versions {

	static final String PACKAGE_INFO = """
			@SbeSchema(id = 1, version = 3, semanticVersion = "FIX.5.0SP2", codecs = false)
			package corpus.versions;

			import net.concini.sbebuddy.SbeSchema;
			""";

	static final String SOURCE = """
			package corpus.versions;

			import static net.concini.sbebuddy.PrimitiveType.CHAR;
			import static net.concini.sbebuddy.PrimitiveType.INT32;
			import static net.concini.sbebuddy.PrimitiveType.UINT16;
			import static net.concini.sbebuddy.PrimitiveType.UINT8;

			import java.util.List;

			import net.concini.sbebuddy.SbeChoice;
			import net.concini.sbebuddy.SbeComposite;
			import net.concini.sbebuddy.SbeData;
			import net.concini.sbebuddy.SbeEnum;
			import net.concini.sbebuddy.SbeEnumValue;
			import net.concini.sbebuddy.SbeField;
			import net.concini.sbebuddy.SbeGroup;
			import net.concini.sbebuddy.SbeMessage;
			import net.concini.sbebuddy.SbeRef;
			import net.concini.sbebuddy.SbeSet;
			import net.concini.sbebuddy.SbeType;

			@SbeComposite(name = "varStringEncoding")
			record VarStringEncoding(
					@SbeType(primitiveType = UINT16) int length,
					@SbeType(primitiveType = CHAR, length = 0, characterEncoding = "UTF-8") String varData
			) {
			}

			@SbeType(primitiveType = INT32, sinceVersion = 1, deprecated = 3)
			final class Added {
			}

			@SbeEnum(primitiveType = UINT8, sinceVersion = 1, deprecated = 3)
			enum Status {

				@SbeEnumValue(value = "1", sinceVersion = 1, deprecated = 3)
				New
			}

			@SbeSet(primitiveType = UINT8, sinceVersion = 1, deprecated = 3)
			enum Flags {

				@SbeChoice(value = 0, sinceVersion = 1, deprecated = 3)
				urgent
			}

			@SbeComposite(sinceVersion = 1, deprecated = 3)
			record Pair(
					@SbeType(primitiveType = INT32) int first,
					@SbeRef(value = Added.class, offset = 4, sinceVersion = 1, deprecated = 3) int second
			) {
			}

			@SbeMessage(id = 1, sinceVersion = 1, deprecated = 3)
			record Versions(
					@SbeField(id = 1, type = Added.class, sinceVersion = 1, deprecated = 3) Integer added,
					@SbeGroup(id = 2, sinceVersion = 2, deprecated = 3) List<Extra> extra,
					@SbeData(id = 4, type = VarStringEncoding.class, sinceVersion = 2, deprecated = 3) String note
			) {

				record Extra(
						@SbeField(id = 3, sinceVersion = 2, deprecated = 3) Pair pair
				) {
				}
			}
			""";

	static final String XML = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.versions" id="1" version="3" semanticVersion="FIX.5.0SP2">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			        <type name="Added" primitiveType="int32" sinceVersion="1" deprecated="3"/>
			        <set name="Flags" encodingType="uint8" sinceVersion="1" deprecated="3">
			            <choice name="urgent" sinceVersion="1" deprecated="3">0</choice>
			        </set>
			        <composite name="Pair" sinceVersion="1" deprecated="3">
			            <type name="first" primitiveType="int32"/>
			            <ref name="second" type="Added" offset="4" sinceVersion="1" deprecated="3"/>
			        </composite>
			        <enum name="Status" encodingType="uint8" sinceVersion="1" deprecated="3">
			            <validValue name="New" sinceVersion="1" deprecated="3">1</validValue>
			        </enum>
			        <composite name="varStringEncoding">
			            <type name="length" primitiveType="uint16"/>
			            <type name="varData" primitiveType="char" length="0" characterEncoding="UTF-8"/>
			        </composite>
			        <composite name="groupSizeEncoding">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="numInGroup" primitiveType="uint16"/>
			        </composite>
			    </types>
			    <sbe:message name="Versions" id="1" sinceVersion="1" deprecated="3">
			        <field name="added" id="1" type="Added" sinceVersion="1" deprecated="3"/>
			        <group name="extra" id="2" sinceVersion="2" deprecated="3">
			            <field name="pair" id="3" type="Pair" sinceVersion="2" deprecated="3"/>
			        </group>
			        <data name="note" id="4" type="varStringEncoding" sinceVersion="2" deprecated="3"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private Versions() {
	}

	static Schema schema() {
		return messageSchema("corpus.versions", 1, 3)
				.semanticVersion("FIX.5.0SP2")
				.types(
						messageHeader(),
						type("Added", INT32).sinceVersion(1).deprecated(3),
						set("Flags", "uint8")
								.sinceVersion(1)
								.deprecated(3)
								.choices(choice("urgent", 0).sinceVersion(1).deprecated(3)),
						composite("Pair")
								.sinceVersion(1)
								.deprecated(3)
								.members(
										type("first", INT32),
										ref("second", "Added").offset(4).sinceVersion(1).deprecated(3)
								),
						enumeration("Status", "uint8")
								.sinceVersion(1)
								.deprecated(3)
								.validValues(validValue("New", "1").sinceVersion(1).deprecated(3)),
						composite("varStringEncoding").members(
								type("length", UINT16),
								type("varData", CHAR).length(0).characterEncoding("UTF-8")
						),
						groupSizeEncoding()
				)
				.messages(
						message("Versions", 1)
								.sinceVersion(1)
								.deprecated(3)
								.fields(field("added", 1, "Added").sinceVersion(1).deprecated(3))
								.groups(
										group("extra", 2)
												.sinceVersion(2)
												.deprecated(3)
												.fields(field("pair", 3, "Pair").sinceVersion(2).deprecated(3))
								)
								.data(data("note", 4, "varStringEncoding").sinceVersion(2).deprecated(3))
				)
				.build();
	}

	static Annotated annotated() {
		AnnotatedCompositeBuilder varStringEncoding = annotatedComposite("VarStringEncoding")
				.qualifiedName("corpus.versions.VarStringEncoding")
				.name("varStringEncoding")
				.members(
						annotatedType("length", UINT16).javaType(primitive(INT)),
						annotatedType("varData", CHAR).length(0).characterEncoding("UTF-8").javaType(text())
				);
		AnnotatedTypeBuilder added = annotatedType("Added", INT32).sinceVersion(1).deprecated(3);
		AnnotatedEnumBuilder status = annotatedEnum("Status")
				.qualifiedName("corpus.versions.Status")
				.primitiveType(UINT8)
				.sinceVersion(1)
				.deprecated(3)
				.values(annotatedEnumValue("New", "1").sinceVersion(1).deprecated(3));
		AnnotatedSetBuilder flags = annotatedSet("Flags")
				.qualifiedName("corpus.versions.Flags")
				.primitiveType(UINT8)
				.sinceVersion(1)
				.deprecated(3)
				.choices(annotatedChoice("urgent", 0).sinceVersion(1).deprecated(3));
		AnnotatedCompositeBuilder pair = annotatedComposite("Pair")
				.qualifiedName("corpus.versions.Pair")
				.sinceVersion(1)
				.deprecated(3)
				.members(
						annotatedType("first", INT32).javaType(primitive(INT)),
						annotatedRef("second", primitive(INT)).value(added).offset(4).sinceVersion(1).deprecated(3)
				);
		return annotatedSchema("corpus.versions", 1, 3).codecs(false)
				.semanticVersion("FIX.5.0SP2")
				.types(added, flags, pair, status, varStringEncoding)
				.messages(
						annotatedMessage("Versions", 1)
								.sinceVersion(1)
								.deprecated(3)
								.components(
										annotatedField("added", 1, boxed(INT))
												.type(added)
												.sinceVersion(1)
												.deprecated(3),
										annotatedGroup("extra", 2)
												.sinceVersion(2)
												.deprecated(3)
												.components(
														annotatedField("pair", 3, declared(pair))
																.sinceVersion(2)
																.deprecated(3)
												),
										annotatedData("note", 4, text(), varStringEncoding)
												.sinceVersion(2)
												.deprecated(3)
								)
				)
				.build();
	}
}
