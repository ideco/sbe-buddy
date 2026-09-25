package corpus.boundpaths;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.tests.SchemaCase;

import corpus.boundpaths.BoundPaths.Fill;

/**
 * Members whose paths spell alike: a field {@code fillsPrice} and a group
 * {@code fills} holding a {@code price}, a field {@code fillsLevels} and the
 * group's {@code levels} of another length, a field {@code quoteBid} and a
 * composite {@code Quote} holding a {@code bid}. The codec compiles, each array
 * goes through its own pair, and each binding is handed its own component's
 * context, which its refusals name.
 */
final class BoundPathsTest implements SchemaCase {

	static final String ORACLE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.boundpaths" id="1" version="0">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			        <type name="Levels" primitiveType="int32" length="2"/>
			        <composite name="Quote">
			            <type name="bid" primitiveType="int64"/>
			        </composite>
			        <composite name="groupSizeEncoding">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="numInGroup" primitiveType="uint16"/>
			        </composite>
			        <type name="Depths" primitiveType="int32" length="3"/>
			    </types>
			    <sbe:message name="BoundPaths" id="1">
			        <field name="fillsPrice" id="1" type="int64" presence="optional"/>
			        <field name="fillsLevels" id="2" type="Levels"/>
			        <field name="quoteBid" id="3" type="int64"/>
			        <field name="quote" id="4" type="Quote"/>
			        <group name="fills" id="5">
			            <field name="price" id="6" type="int64"/>
			            <field name="levels" id="7" type="Depths"/>
			        </group>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private static final int OFFSET = 8;

	@Override
	public String description() {
		return "BoundPaths: members whose paths spell alike, each with its own helpers and context";
	}

	@Override
	public String oracle() {
		return ORACLE;
	}

	@Override
	public List<RoundTrip<?>> roundTrips() {
		return List.of(
				new RoundTrip<>(
						"every member present, the arrays of two lengths", new BoundPathsCodec(),
						message(new BigDecimal("1.25"), new BigDecimal("3.50"), new BigDecimal("2.75"))
				),
				new RoundTrip<>(
						"the optional price null, the fills' own present", new BoundPathsCodec(),
						message(null, new BigDecimal("3.50"), new BigDecimal("2.75"))
				)
		);
	}

	@Test
	void theGroupsPriceIsHandedItsOwnContext() {
		assertRefused(
				message(new BigDecimal("1.25"), new BigDecimal("3.50"), new BigDecimal("2.755")),
				"price (REQUIRED) refuses 2.755"
		);
	}

	@Test
	void theCompositesBidIsHandedItsOwnContext() {
		assertRefused(
				new BoundPaths(
						null, new int[]{1, 2}, new BigDecimal("3.50"), new Quote(new BigDecimal("3.505")), List.of()
				),
				"bid (REQUIRED) refuses 3.505"
		);
	}

	private static BoundPaths message(BigDecimal fillsPrice, BigDecimal bid, BigDecimal price) {
		return new BoundPaths(
				fillsPrice, new int[]{1, 2}, bid, new Quote(bid), List.of(new Fill(price, new int[]{4, 5, 6}))
		);
	}

	private static void assertRefused(BoundPaths value, String message) {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[128]);

		assertThatThrownBy(() -> new BoundPathsCodec().encode(value, buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage(message);
	}
}
