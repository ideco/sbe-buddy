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
import static net.concini.sbebuddy.generator.Fixtures.bytes;
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
import static uk.co.real_logic.sbe.PrimitiveType.UINT32;
import static uk.co.real_logic.sbe.PrimitiveType.UINT8;

import net.concini.sbebuddy.generator.Annotated;
import net.concini.sbebuddy.generator.Fixtures.AnnotatedCompositeBuilder;
import net.concini.sbebuddy.generator.Schema;

/**
 * Variable-length data: text with a character encoding, opaque bytes without
 * one, a length type wide enough to need its own maxValue, and var-data inside
 * a group's entry.
 */
final class VarData {

	static final String PACKAGE_INFO = """
			@SbeSchema(id = 1, version = 0, codecs = false)
			package corpus.vardata;

			import net.concini.sbebuddy.SbeSchema;
			""";

	static final String SOURCE = """
			package corpus.vardata;

			import static net.concini.sbebuddy.PrimitiveType.CHAR;
			import static net.concini.sbebuddy.PrimitiveType.UINT16;
			import static net.concini.sbebuddy.PrimitiveType.UINT32;
			import static net.concini.sbebuddy.PrimitiveType.UINT8;

			import java.util.List;

			import net.concini.sbebuddy.SbeComposite;
			import net.concini.sbebuddy.SbeData;
			import net.concini.sbebuddy.SbeField;
			import net.concini.sbebuddy.SbeGroup;
			import net.concini.sbebuddy.SbeMessage;
			import net.concini.sbebuddy.SbeType;

			@SbeComposite(name = "varStringEncoding")
			record VarStringEncoding(
					@SbeType(primitiveType = UINT16) int length,
					@SbeType(primitiveType = CHAR, length = 0, characterEncoding = "UTF-8") String varData
			) {
			}

			@SbeComposite(name = "varBlobEncoding")
			record VarBlobEncoding(
					@SbeType(primitiveType = UINT32, maxValue = "1073741824") long length,
					@SbeType(primitiveType = UINT8, length = 0) byte[] varData
			) {
			}

			@SbeComposite(name = "varByteEncoding")
			record VarByteEncoding(
					@SbeType(primitiveType = UINT8) short length,
					@SbeType(primitiveType = UINT8, length = 0) byte[] varData
			) {
			}

			@SbeMessage(id = 1)
			record VarData(
					@SbeField(id = 1) int orderId,
					@SbeGroup(id = 5) List<Attachment> attachments,
					@SbeData(
							id = 2, type = VarStringEncoding.class, semanticType = "String",
							description = "A free-text note"
					) String note,
					@SbeData(id = 3, type = VarBlobEncoding.class) byte[] payload,
					@SbeData(id = 4, type = VarByteEncoding.class) byte[] signature
			) {

				record Attachment(
						@SbeField(id = 6) int kind,
						@SbeData(id = 7, type = VarByteEncoding.class, offset = 4) byte[] content
				) {
				}
			}
			""";

	static final String XML = """
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

	private VarData() {
	}

	static Schema schema() {
		return messageSchema("corpus.vardata", 1, 0)
				.types(
						messageHeader(),
						composite("varBlobEncoding").members(
								type("length", UINT32).maxValue("1073741824"),
								type("varData", UINT8).length(0)
						),
						composite("varByteEncoding").members(
								type("length", UINT8),
								type("varData", UINT8).length(0)
						),
						composite("varStringEncoding").members(
								type("length", UINT16),
								type("varData", CHAR).length(0).characterEncoding("UTF-8")
						),
						groupSizeEncoding()
				)
				.messages(
						message("VarData", 1)
								.fields(field("orderId", 1, "int32"))
								.groups(
										group("attachments", 5)
												.fields(field("kind", 6, "int32"))
												.data(data("content", 7, "varByteEncoding").offset(4))
								)
								.data(
										data("note", 2, "varStringEncoding")
												.semanticType("String")
												.description("A free-text note"),
										data("payload", 3, "varBlobEncoding"),
										data("signature", 4, "varByteEncoding")
								)
				)
				.build();
	}

	static Annotated annotated() {
		AnnotatedCompositeBuilder varStringEncoding = annotatedComposite("VarStringEncoding")
				.qualifiedName("corpus.vardata.VarStringEncoding")
				.name("varStringEncoding")
				.members(
						annotatedType("length", UINT16).javaType(primitive(INT)),
						annotatedType("varData", CHAR).length(0).characterEncoding("UTF-8").javaType(text())
				);
		AnnotatedCompositeBuilder varBlobEncoding = annotatedComposite("VarBlobEncoding")
				.qualifiedName("corpus.vardata.VarBlobEncoding")
				.name("varBlobEncoding")
				.members(
						annotatedType("length", UINT32).maxValue("1073741824").javaType(primitive(LONG)),
						annotatedType("varData", UINT8).length(0).javaType(bytes())
				);
		AnnotatedCompositeBuilder varByteEncoding = annotatedComposite("VarByteEncoding")
				.qualifiedName("corpus.vardata.VarByteEncoding")
				.name("varByteEncoding")
				.members(
						annotatedType("length", UINT8).javaType(primitive(SHORT)),
						annotatedType("varData", UINT8).length(0).javaType(bytes())
				);
		return annotatedSchema("corpus.vardata", 1, 0).codecs(false)
				.types(varBlobEncoding, varByteEncoding, varStringEncoding)
				.messages(
						annotatedMessage("VarData", 1).components(
								annotatedField("orderId", 1, primitive(INT)),
								annotatedGroup("attachments", 5)
										.javaType(listOfRecord("corpus.vardata.VarData.Attachment"))
										.components(
												annotatedField("kind", 6, primitive(INT)),
												annotatedData("content", 7, bytes(), varByteEncoding).offset(4)
										),
								annotatedData("note", 2, text(), varStringEncoding)
										.semanticType("String")
										.description("A free-text note"),
								annotatedData("payload", 3, bytes(), varBlobEncoding),
								annotatedData("signature", 4, bytes(), varByteEncoding)
						)
				)
				.build();
	}
}
