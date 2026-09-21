package net.concini.sbebuddy.generator.corpus;

import static java.nio.ByteOrder.BIG_ENDIAN;
import static net.concini.sbebuddy.generator.Annotated.JavaPrimitive.LONG;
import static net.concini.sbebuddy.generator.Fixtures.annotatedField;
import static net.concini.sbebuddy.generator.Fixtures.annotatedMessage;
import static net.concini.sbebuddy.generator.Fixtures.annotatedSchema;
import static net.concini.sbebuddy.generator.Fixtures.field;
import static net.concini.sbebuddy.generator.Fixtures.message;
import static net.concini.sbebuddy.generator.Fixtures.messageHeader;
import static net.concini.sbebuddy.generator.Fixtures.messageSchema;
import static net.concini.sbebuddy.generator.Fixtures.primitive;

import net.concini.sbebuddy.generator.Annotated;
import net.concini.sbebuddy.generator.Schema;

/** A schema on the wire the other way round. */
final class BigEndian {

	static final String XML = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.bigendian" id="1" version="0" byteOrder="bigEndian">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			    </types>
			    <sbe:message name="BigEndian" id="1">
			        <field name="orderId" id="1" type="int64"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private BigEndian() {
	}

	static Schema schema() {
		return messageSchema("corpus.bigendian", 1, 0)
				.byteOrder(BIG_ENDIAN)
				.types(messageHeader())
				.messages(
						message("BigEndian", 1).fields(field("orderId", 1, "int64"))
				)
				.build();
	}

	static Annotated annotated() {
		return annotatedSchema("corpus.bigendian", 1, 0)
				.byteOrder(BIG_ENDIAN)
				.messages(
						annotatedMessage("BigEndian", 1).components(
								annotatedField("orderId", 1, primitive(LONG))
						)
				)
				.build();
	}
}
