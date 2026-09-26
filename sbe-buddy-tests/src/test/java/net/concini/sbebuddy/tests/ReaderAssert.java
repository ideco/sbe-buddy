package net.concini.sbebuddy.tests;

import static org.assertj.core.api.Assertions.assertThat;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import net.concini.sbebuddy.Codec;

/**
 * A value read back by hand through a message's reader, its bound stages giving
 * the record's components, held against the value its codec wrote.
 */
public final class ReaderAssert {

	private static final int OFFSET = 8;

	private ReaderAssert() {
	}

	/** A reading from {@code wrap} to the value, the record rebuilt. */
	@FunctionalInterface
	public interface Reading<T> {

		T read(DirectBuffer buffer, int offset);
	}

	public static <T> void assertReadsTheValue(Codec<T, ?> codec, T value, Reading<T> reading) {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[OFFSET + codec.encodedLength(value) + OFFSET]);
		codec.encode(value, buffer, OFFSET);

		assertThat(reading.read(buffer, OFFSET)).usingRecursiveComparison().isEqualTo(value);
	}
}
