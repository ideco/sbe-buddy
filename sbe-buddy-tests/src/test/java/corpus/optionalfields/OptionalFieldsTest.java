package corpus.optionalfields;

import static net.concini.sbebuddy.tests.WriterAssert.assertWritesTheCodecsBytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.tests.SchemaCase;

import corpus.optionalfields.sbe.MessageHeaderEncoder;

/**
 * Presence declared on the field itself rather than on a named type; the
 * primitive's null value applies. The round trips carry both optional fields
 * absent and then present; the case's own test shows the float's null value
 * swallowing an ordinary NaN.
 */
final class OptionalFieldsTest implements SchemaCase {

	static final String ORACLE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.optionalfields" id="1" version="0">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			    </types>
			    <sbe:message name="OptionalFields" id="1">
			        <field name="orderId" id="1" type="int64"/>
			        <field name="quantity" id="2" type="int32" presence="optional"/>
			        <field name="price" id="3" type="double" presence="optional" description="Absent for a market order"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private static final int OFFSET = 8;

	@Override
	public String description() {
		return "OptionalFields: presence declared on the field itself, the primitive's null value marking absence";
	}

	@Override
	public String oracle() {
		return ORACLE;
	}

	@Override
	public List<RoundTrip<?>> roundTrips() {
		return List.of(
				new RoundTrip<>(
						"the quantity and the price absent", new OptionalFieldsCodec(),
						new OptionalFields(42L, null, null)
				),
				new RoundTrip<>(
						"the quantity and the price present", new OptionalFieldsCodec(),
						new OptionalFields(42L, 100, 101.25)
				)
		);
	}

	@Test
	void aFloatFieldPresentAtNaNIsTheNullValueAndDecodesToNull() {
		OptionalFieldsCodec codec = new OptionalFieldsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		OptionalFields value = new OptionalFields(42L, 100, Double.NaN);

		codec.encode(value, buffer, OFFSET);
		OptionalFields decoded = codec.decode(buffer, OFFSET);

		assertThat(decoded.price()).isNull();
	}

	@Test
	void anotherTemplateIsRefused() {
		OptionalFieldsCodec codec = new OptionalFieldsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		codec.encode(new OptionalFields(42L, null, null), buffer, OFFSET);
		new MessageHeaderEncoder().wrap(buffer, OFFSET).templateId(2);

		assertThatThrownBy(() -> codec.decode(buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("not a OptionalFields: schemaId 1, templateId 2");
	}

	@Test
	void anOptionalFieldTheWriterLeavesUnsetIsNull() {
		assertWritesTheCodecsBytes(
				new OptionalFieldsCodec(), new OptionalFields(42L, null, null),
				(buffer, offset) -> new OptionalFieldsWriter().wrap(buffer, offset).orderId(42L).length()
		);
	}

	@Test
	void theOptionalFieldsAreSetInAnyOrderOnceTheRequiredAreSet() {
		assertWritesTheCodecsBytes(
				new OptionalFieldsCodec(), new OptionalFields(42L, 100, 101.25),
				(buffer, offset) -> new OptionalFieldsWriter().wrap(buffer, offset)
						.orderId(42L)
						.price(101.25)
						.quantity(100)
						.length()
		);
	}
}
