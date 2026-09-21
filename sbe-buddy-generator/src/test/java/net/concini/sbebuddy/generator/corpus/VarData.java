package net.concini.sbebuddy.generator.corpus;

import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.INT;
import static net.concini.sbebuddy.generator.Fixtures.annotatedComposite;
import static net.concini.sbebuddy.generator.Fixtures.annotatedData;
import static net.concini.sbebuddy.generator.Fixtures.annotatedField;
import static net.concini.sbebuddy.generator.Fixtures.annotatedMessage;
import static net.concini.sbebuddy.generator.Fixtures.annotatedSchema;
import static net.concini.sbebuddy.generator.Fixtures.annotatedType;
import static net.concini.sbebuddy.generator.Fixtures.bytes;
import static net.concini.sbebuddy.generator.Fixtures.composite;
import static net.concini.sbebuddy.generator.Fixtures.data;
import static net.concini.sbebuddy.generator.Fixtures.field;
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
 * one, and a length type wide enough to need its own maxValue.
 */
final class VarData {

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
			        <composite name="varStringEncoding">
			            <type name="length" primitiveType="uint16"/>
			            <type name="varData" primitiveType="char" length="0" characterEncoding="UTF-8"/>
			        </composite>
			        <composite name="varBlobEncoding">
			            <type name="length" primitiveType="uint32" maxValue="1073741824"/>
			            <type name="varData" primitiveType="uint8" length="0"/>
			        </composite>
			        <composite name="varByteEncoding">
			            <type name="length" primitiveType="uint8"/>
			            <type name="varData" primitiveType="uint8" length="0"/>
			        </composite>
			    </types>
			    <sbe:message name="VarData" id="1">
			        <field name="orderId" id="1" type="int32"/>
			        <data name="note" id="2" type="varStringEncoding" offset="4" semanticType="String" description="A free-text note"/>
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
						composite("varStringEncoding").members(
								type("length", UINT16),
								type("varData", CHAR).length(0).characterEncoding("UTF-8")
						),
						composite("varBlobEncoding").members(
								type("length", UINT32).maxValue("1073741824"),
								type("varData", UINT8).length(0)
						),
						composite("varByteEncoding").members(
								type("length", UINT8),
								type("varData", UINT8).length(0)
						)
				)
				.messages(
						message("VarData", 1)
								.fields(field("orderId", 1, "int32"))
								.data(
										data("note", 2, "varStringEncoding")
												.offset(4)
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
				.name("varStringEncoding")
				.members(
						annotatedType("length", UINT16),
						annotatedType("varData", CHAR).length(0).characterEncoding("UTF-8")
				);
		AnnotatedCompositeBuilder varBlobEncoding = annotatedComposite("VarBlobEncoding")
				.name("varBlobEncoding")
				.members(
						annotatedType("length", UINT32).maxValue("1073741824"),
						annotatedType("varData", UINT8).length(0)
				);
		AnnotatedCompositeBuilder varByteEncoding = annotatedComposite("VarByteEncoding")
				.name("varByteEncoding")
				.members(
						annotatedType("length", UINT8),
						annotatedType("varData", UINT8).length(0)
				);
		return annotatedSchema("corpus.vardata", 1, 0)
				.types(varStringEncoding, varBlobEncoding, varByteEncoding)
				.messages(
						annotatedMessage("VarData", 1).components(
								annotatedField("orderId", 1, primitive(INT)),
								annotatedData("note", 2, text(), varStringEncoding)
										.offset(4)
										.semanticType("String")
										.description("A free-text note"),
								annotatedData("payload", 3, bytes(), varBlobEncoding),
								annotatedData("signature", 4, bytes(), varByteEncoding)
						)
				)
				.build();
	}
}
