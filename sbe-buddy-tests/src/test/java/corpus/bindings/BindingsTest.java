package corpus.bindings;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.tests.SchemaCase;

import corpus.bindings.sbe.MessageHeaderEncoder;

/**
 * Bindings between a record's own types and the wire's faces: a specialization
 * over a primitive face, shared by a named type and a bare primitive and over
 * an optional field, and the generic interface over a string, to a wrapper
 * record, and over a byte array, to a record. The round trips carry the
 * optional bound field absent and present; the tests hold every required
 * binding's field required, a binding's own exception passing through, and the
 * ASCII string a binding hands the flyweight.
 */
final class BindingsTest implements SchemaCase {

	static final String ORACLE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.bindings" id="1" version="0">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			        <type name="Cents" primitiveType="int64"/>
			        <type name="Rgb" primitiveType="uint8" length="3"/>
			        <type name="Symbol" primitiveType="char" length="6"/>
			    </types>
			    <sbe:message name="Bindings" id="1">
			        <field name="price" id="1" type="Cents"/>
			        <field name="fee" id="2" type="int64"/>
			        <field name="rebate" id="3" type="int64" presence="optional"/>
			        <field name="symbol" id="4" type="Symbol"/>
			        <field name="colour" id="5" type="Rgb"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private static final int OFFSET = 8;

	private static final BigDecimal PRICE = new BigDecimal("101.25");

	private static final BigDecimal FEE = new BigDecimal("0.50");

	private static final Ticker SYMBOL = new Ticker("AAPL");

	private static final Colour COLOUR = new Colour(255, 128, 0);

	@Override
	public String description() {
		return "Bindings: specializations over primitive faces, the generic interface over a string and a byte array";
	}

	@Override
	public String oracle() {
		return ORACLE;
	}

	@Override
	public List<RoundTrip<?>> roundTrips() {
		return List.of(
				new RoundTrip<>(
						"the rebate absent", new BindingsCodec(), new Bindings(PRICE, FEE, null, SYMBOL, COLOUR)
				),
				new RoundTrip<>(
						"the rebate present", new BindingsCodec(),
						new Bindings(PRICE, FEE, new BigDecimal("1.05"), SYMBOL, COLOUR)
				)
		);
	}

	@Test
	void theCentsBindingsOwnExceptionPassesThroughUnwrapped() {
		BindingsCodec codec = new BindingsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		Bindings tooManyDecimals = new Bindings(new BigDecimal("1.005"), FEE, null, SYMBOL, COLOUR);

		assertThatThrownBy(() -> codec.encode(tooManyDecimals, buffer, OFFSET))
				.isInstanceOf(ArithmeticException.class);
	}

	@Test
	void aNullPriceIsRefused() {
		BindingsCodec codec = new BindingsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);

		assertThatThrownBy(() -> codec.encode(new Bindings(null, FEE, null, SYMBOL, COLOUR), buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("price is required");
	}

	@Test
	void aNullFeeIsRefused() {
		BindingsCodec codec = new BindingsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);

		assertThatThrownBy(() -> codec.encode(new Bindings(PRICE, null, null, SYMBOL, COLOUR), buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("fee is required");
	}

	@Test
	void aNullSymbolIsRefused() {
		BindingsCodec codec = new BindingsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);

		assertThatThrownBy(() -> codec.encode(new Bindings(PRICE, FEE, null, null, COLOUR), buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("symbol is required");
	}

	@Test
	void aNullColourIsRefused() {
		BindingsCodec codec = new BindingsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);

		assertThatThrownBy(() -> codec.encode(new Bindings(PRICE, FEE, null, SYMBOL, null), buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("colour is required");
	}

	@Test
	void aSymbolLongerThanItsLengthIsRefused() {
		BindingsCodec codec = new BindingsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		Bindings value = new Bindings(PRICE, FEE, null, new Ticker("TOOLONG"), COLOUR);

		assertThatThrownBy(() -> codec.encode(value, buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("symbol is longer than 6: TOOLONG");
	}

	@Test
	void aNonAsciiSymbolIsRefused() {
		BindingsCodec codec = new BindingsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		Bindings value = new Bindings(PRICE, FEE, null, new Ticker("café"), COLOUR);

		assertThatThrownBy(() -> codec.encode(value, buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("symbol is not ASCII: café");
	}

	@Test
	void anotherTemplateIsRefused() {
		BindingsCodec codec = new BindingsCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		codec.encode(new Bindings(PRICE, FEE, null, SYMBOL, COLOUR), buffer, OFFSET);
		new MessageHeaderEncoder().wrap(buffer, OFFSET).templateId(2);

		assertThatThrownBy(() -> codec.decode(buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("not a Bindings: schemaId 1, templateId 2");
	}
}
