package corpus.arrays;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.tests.SchemaCase;

import corpus.arrays.Arrays.Swatch;
import corpus.arrays.sbe.MessageHeaderEncoder;

/**
 * Fixed-length arrays of a primitive other than char, which is a string, not an
 * array: bytes through the bulk accessors, the rest by index, in the message,
 * in a composite and in a group's entry. The one round trip carries every array
 * full at its bounds; the tests hold what a wrong length and a null array
 * refuse.
 */
final class ArraysTest implements SchemaCase {

	static final String ORACLE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.arrays" id="1" version="0">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			        <type name="Bounds" primitiveType="int16" length="2"/>
			        <composite name="Palette">
			            <type name="accent" primitiveType="uint8" length="3"/>
			            <type name="weight" primitiveType="int16"/>
			        </composite>
			        <composite name="groupSizeEncoding">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="numInGroup" primitiveType="uint16"/>
			        </composite>
			        <type name="Rgb" primitiveType="uint8" length="3"/>
			        <type name="Samples" primitiveType="int32" length="4" description="Four readings, oldest first"/>
			    </types>
			    <sbe:message name="Arrays" id="1">
			        <field name="colour" id="1" type="Rgb"/>
			        <field name="samples" id="2" type="Samples"/>
			        <field name="bounds" id="3" type="Bounds"/>
			        <field name="palette" id="4" type="Palette"/>
			        <group name="swatches" id="5">
			            <field name="colour" id="6" type="Rgb"/>
			        </group>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private static final int OFFSET = 8;

	private static final Arrays ARRAYS = new Arrays(
			new byte[]{0, (byte) 0xFF, Byte.MIN_VALUE},
			new int[]{Integer.MIN_VALUE, 0, Integer.MAX_VALUE, -1},
			new short[]{Short.MIN_VALUE, Short.MAX_VALUE},
			new Palette(new byte[]{(byte) 0xFF, 0, 1}, Short.MIN_VALUE),
			List.of(new Swatch(new byte[]{1, 2, 3}), new Swatch(new byte[]{Byte.MIN_VALUE, -1, Byte.MAX_VALUE}))
	);

	@Override
	public String description() {
		return "Arrays: fixed-length arrays of a primitive other than char, bytes through the bulk accessors, "
				+ "the rest by index, in a composite and a group's entry too";
	}

	@Override
	public String oracle() {
		return ORACLE;
	}

	@Override
	public List<RoundTrip<?>> roundTrips() {
		return List.of(new RoundTrip<>("every array full at its bounds", new ArraysCodec(), ARRAYS));
	}

	@Test
	void aColourOfTheWrongLengthIsRefused() {
		ArraysCodec codec = new ArraysCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		Arrays value = new Arrays(
				new byte[]{0, 1}, ARRAYS.samples(), ARRAYS.bounds(), ARRAYS.palette(), ARRAYS.swatches()
		);

		assertThatThrownBy(() -> codec.encode(value, buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("colour must be 3 long, not 2");
	}

	@Test
	void aSamplesOfTheWrongLengthIsRefused() {
		ArraysCodec codec = new ArraysCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		Arrays value = new Arrays(
				ARRAYS.colour(), new int[]{1, 2, 3}, ARRAYS.bounds(), ARRAYS.palette(), ARRAYS.swatches()
		);

		assertThatThrownBy(() -> codec.encode(value, buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("samples must be 4 long, not 3");
	}

	@Test
	void aBoundsOfTheWrongLengthIsRefused() {
		ArraysCodec codec = new ArraysCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		Arrays value = new Arrays(
				ARRAYS.colour(), ARRAYS.samples(), new short[]{1}, ARRAYS.palette(), ARRAYS.swatches()
		);

		assertThatThrownBy(() -> codec.encode(value, buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("bounds must be 2 long, not 1");
	}

	@Test
	void aNullColourIsRefused() {
		ArraysCodec codec = new ArraysCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		Arrays value = new Arrays(null, ARRAYS.samples(), ARRAYS.bounds(), ARRAYS.palette(), ARRAYS.swatches());

		assertThatThrownBy(() -> codec.encode(value, buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("colour is required");
	}

	@Test
	void aNullSamplesIsRefused() {
		ArraysCodec codec = new ArraysCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		Arrays value = new Arrays(ARRAYS.colour(), null, ARRAYS.bounds(), ARRAYS.palette(), ARRAYS.swatches());

		assertThatThrownBy(() -> codec.encode(value, buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("samples is required");
	}

	@Test
	void aNullBoundsIsRefused() {
		ArraysCodec codec = new ArraysCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		Arrays value = new Arrays(ARRAYS.colour(), ARRAYS.samples(), null, ARRAYS.palette(), ARRAYS.swatches());

		assertThatThrownBy(() -> codec.encode(value, buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("bounds is required");
	}

	@Test
	void anAccentOfTheWrongLengthIsRefused() {
		ArraysCodec codec = new ArraysCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		Arrays value = new Arrays(
				ARRAYS.colour(), ARRAYS.samples(), ARRAYS.bounds(), new Palette(new byte[4], (short) 0), List.of()
		);

		assertThatThrownBy(() -> codec.encode(value, buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("accent must be 3 long, not 4");
	}

	@Test
	void aSwatchColourOfTheWrongLengthIsRefused() {
		ArraysCodec codec = new ArraysCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		Arrays value = new Arrays(
				ARRAYS.colour(), ARRAYS.samples(), ARRAYS.bounds(), ARRAYS.palette(), List.of(new Swatch(new byte[1]))
		);

		assertThatThrownBy(() -> codec.encode(value, buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("colour must be 3 long, not 1");
	}

	@Test
	void anotherTemplateIsRefused() {
		ArraysCodec codec = new ArraysCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		codec.encode(ARRAYS, buffer, OFFSET);
		new MessageHeaderEncoder().wrap(buffer, OFFSET).templateId(2);

		assertThatThrownBy(() -> codec.decode(buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("not a Arrays: schemaId 1, templateId 2");
	}
}
