package net.concini.sbebuddy.tests;

import static org.assertj.core.api.Assertions.assertThat;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

import net.concini.sbebuddy.Codec;

/**
 * A value written by hand through a message's writer, held against its codec:
 * the chain writes the length and the bytes the codec writes for the value,
 * leaving the bytes around the message as they were, and the codec reads the
 * value back from what the chain wrote.
 */
public final class WriterAssert {

	private static final int OFFSET = 8;

	private WriterAssert() {
	}

	/** A writer's chain from {@code wrap} to {@code length()}. */
	@FunctionalInterface
	public interface Chain {

		int write(MutableDirectBuffer buffer, int offset);
	}

	public static <T> void assertWritesTheCodecsBytes(Codec<T, ?> codec, T value, Chain chain) {
		int capacity = OFFSET + codec.encodedLength(value) + OFFSET;
		UnsafeBuffer expected = new UnsafeBuffer(new byte[capacity]);
		UnsafeBuffer written = new UnsafeBuffer(new byte[capacity]);

		int length = codec.encode(value, expected, OFFSET);

		assertThat(chain.write(written, OFFSET)).isEqualTo(length);
		assertThat(written.byteArray()).isEqualTo(expected.byteArray());
		assertThat(codec.decode(written, OFFSET)).usingRecursiveComparison().isEqualTo(value);
	}
}
