package corpus.schemafirst;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.tests.SchemaCase;

import corpus.schemafirst.BigEndian.Fill;

/**
 * The big-endian schema read from its XML: the records of
 * {@code corpus.bigendian} over the checked-in schema, and the proof that the
 * switch changed nothing, the generated sources of the two packages being equal
 * but for the package name.
 */
final class SchemaFirstTest implements SchemaCase {

	static final String ORACLE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.schemafirst" id="1" version="0" byteOrder="bigEndian">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			        <type name="Bounds" primitiveType="int16" length="2"/>
			        <composite name="Price">
			            <type name="mantissa" primitiveType="int64"/>
			            <type name="exponent" primitiveType="int8"/>
			        </composite>
			        <set name="Rights" encodingType="uint32">
			            <choice name="trade">1</choice>
			            <choice name="quote">30</choice>
			        </set>
			        <enum name="Venue" encodingType="uint16">
			            <validValue name="XLON">1000</validValue>
			            <validValue name="XNYS">2000</validValue>
			        </enum>
			        <composite name="groupSizeEncoding">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="numInGroup" primitiveType="uint16"/>
			        </composite>
			        <composite name="varStringEncoding">
			            <type name="length" primitiveType="uint16"/>
			            <type name="varData" primitiveType="char" length="0" characterEncoding="UTF-8"/>
			        </composite>
			    </types>
			    <sbe:message name="BigEndian" id="1">
			        <field name="orderId" id="1" type="int64"/>
			        <field name="level" id="2" type="int16"/>
			        <field name="port" id="3" type="uint16"/>
			        <field name="count" id="4" type="int32"/>
			        <field name="sequence" id="5" type="uint32"/>
			        <field name="timestamp" id="6" type="uint64"/>
			        <field name="ratio" id="7" type="float"/>
			        <field name="rate" id="8" type="double"/>
			        <field name="bounds" id="9" type="Bounds"/>
			        <field name="venue" id="10" type="Venue"/>
			        <field name="rights" id="11" type="Rights"/>
			        <field name="bid" id="12" type="Price"/>
			        <group name="fills" id="13">
			            <field name="quantity" id="14" type="int32"/>
			        </group>
			        <data name="note" id="15" type="varStringEncoding"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	/** The twin's value: every value's bytes differ from their reverse. */
	private static final BigEndian VALUE = new BigEndian(
			0x0102030405060708L, (short) 0x0102, 0xABCD, 0x01020304, 0xF1F2F3F4L, 0x1112131415161718L, 1.5f, Math.PI,
			new short[]{0x0102, 0x0304}, Venue.XNYS, EnumSet.of(Rights.trade, Rights.quote),
			new Price(0x0A0B0C0D0E0F1011L, (byte) -2), List.of(new Fill(0x01020304), new Fill(5)), "big"
	);

	@Override
	public String description() {
		return "SchemaFirst: the big-endian schema read from its XML, over the twin's records";
	}

	@Override
	public String oracle() {
		return ORACLE;
	}

	@Override
	public List<RoundTrip<?>> roundTrips() {
		return List.of(
				new RoundTrip<>("every construct whose bytes depend on the order", new BigEndianCodec(), VALUE),
				new RoundTrip<>(
						"the low bounds, no fills and an empty note", new BigEndianCodec(),
						new BigEndian(
								Long.MIN_VALUE, Short.MIN_VALUE, 0, Integer.MIN_VALUE, 0L, 0L, -Float.MAX_VALUE,
								-Double.MAX_VALUE, new short[]{Short.MIN_VALUE, Short.MAX_VALUE}, Venue.XLON,
								EnumSet.noneOf(Rights.class), new Price(Long.MIN_VALUE, Byte.MIN_VALUE), List.of(), ""
						)
				)
		);
	}

	/**
	 * Every source the processor generated for the twin, the flyweights and the
	 * codec, it generated for this package too, equal but for the package name.
	 */
	@Test
	void theGeneratedSourcesAreTheCodeFirstTwins() throws IOException {
		Path generated = Path.of("target", "generated-sources", "annotations", "corpus");
		Path twin = generated.resolve("bigendian");
		Path own = generated.resolve("schemafirst");
		try (Stream<Path> files = Files.walk(twin)) {
			List<Path> sources = files.filter(Files::isRegularFile).toList();
			assertThat(sources).as("the twin's generated sources").isNotEmpty();
			for (Path source : sources) {
				Path counterpart = own.resolve(twin.relativize(source));
				assertThat(counterpart).exists();
				assertThat(Files.readString(counterpart)).as(counterpart.toString())
						.isEqualTo(Files.readString(source).replace("corpus.bigendian", "corpus.schemafirst"));
			}
		}
	}
}
