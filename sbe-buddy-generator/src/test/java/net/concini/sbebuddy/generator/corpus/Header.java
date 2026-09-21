package net.concini.sbebuddy.generator.corpus;

import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.LONG;
import static net.concini.sbebuddy.generator.Fixtures.annotatedComposite;
import static net.concini.sbebuddy.generator.Fixtures.annotatedField;
import static net.concini.sbebuddy.generator.Fixtures.annotatedMessage;
import static net.concini.sbebuddy.generator.Fixtures.annotatedSchema;
import static net.concini.sbebuddy.generator.Fixtures.annotatedType;
import static net.concini.sbebuddy.generator.Fixtures.composite;
import static net.concini.sbebuddy.generator.Fixtures.field;
import static net.concini.sbebuddy.generator.Fixtures.message;
import static net.concini.sbebuddy.generator.Fixtures.messageSchema;
import static net.concini.sbebuddy.generator.Fixtures.primitive;
import static net.concini.sbebuddy.generator.Fixtures.type;
import static uk.co.real_logic.sbe.PrimitiveType.UINT16;
import static uk.co.real_logic.sbe.PrimitiveType.UINT32;

import net.concini.sbebuddy.generator.Annotated;
import net.concini.sbebuddy.generator.Fixtures.AnnotatedCompositeBuilder;
import net.concini.sbebuddy.generator.Schema;

/**
 * A header of the schema's own naming and shape. sbe-tool requires the four
 * standard members and ignores anything else the header carries.
 */
final class Header {

	static final String PACKAGE_INFO = """
			@SbeSchema(id = 1, version = 0, headerType = ApplicationHeader.class)
			package corpus.header;

			import net.concini.sbebuddy.SbeSchema;
			""";

	static final String SOURCE = """
			package corpus.header;

			import static net.concini.sbebuddy.PrimitiveType.UINT16;
			import static net.concini.sbebuddy.PrimitiveType.UINT32;

			import net.concini.sbebuddy.SbeComposite;
			import net.concini.sbebuddy.SbeField;
			import net.concini.sbebuddy.SbeMessage;
			import net.concini.sbebuddy.SbeType;

			@SbeComposite(name = "applicationHeader", description = "The standard header and a sequence number")
			record ApplicationHeader(
					@SbeType(primitiveType = UINT16) int blockLength,
					@SbeType(primitiveType = UINT16) int templateId,
					@SbeType(primitiveType = UINT16) int schemaId,
					@SbeType(primitiveType = UINT16) int version,
					@SbeType(primitiveType = UINT32) long sequenceNumber
			) {
			}

			@SbeMessage(id = 1)
			record Header(
					@SbeField(id = 1) long orderId
			) {
			}
			""";

	static final String XML = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.header" id="1" version="0" headerType="applicationHeader">
			    <types>
			        <composite name="applicationHeader" description="The standard header and a sequence number">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			            <type name="sequenceNumber" primitiveType="uint32"/>
			        </composite>
			    </types>
			    <sbe:message name="Header" id="1">
			        <field name="orderId" id="1" type="int64"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private Header() {
	}

	static Schema schema() {
		return messageSchema("corpus.header", 1, 0)
				.headerType("applicationHeader")
				.types(
						composite("applicationHeader")
								.description("The standard header and a sequence number")
								.members(
										type("blockLength", UINT16),
										type("templateId", UINT16),
										type("schemaId", UINT16),
										type("version", UINT16),
										type("sequenceNumber", UINT32)
								)
				)
				.messages(
						message("Header", 1).fields(field("orderId", 1, "int64"))
				)
				.build();
	}

	static Annotated annotated() {
		// The package declares its header like any other composite, and names it as
		// the schema's; both reach the one instance, which is declared once.
		AnnotatedCompositeBuilder applicationHeader = annotatedComposite("ApplicationHeader")
				.name("applicationHeader")
				.description("The standard header and a sequence number")
				.members(
						annotatedType("blockLength", UINT16),
						annotatedType("templateId", UINT16),
						annotatedType("schemaId", UINT16),
						annotatedType("version", UINT16),
						annotatedType("sequenceNumber", UINT32)
				);
		return annotatedSchema("corpus.header", 1, 0)
				.headerType(applicationHeader)
				.types(applicationHeader)
				.messages(
						annotatedMessage("Header", 1).components(
								annotatedField("orderId", 1, primitive(LONG))
						)
				)
				.build();
	}
}
