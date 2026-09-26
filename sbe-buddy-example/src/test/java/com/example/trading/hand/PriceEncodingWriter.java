package com.example.trading.hand;

import org.jspecify.annotations.Nullable;

import com.example.trading.sbe.PriceEncodingEncoder;

/**
 * The composite {@code PriceEncoding} as a chain of every member in wire order:
 * {@code mantissa}, optional, so it is set or set null; {@code exponent} is a
 * constant. Either way on to the stage {@code N} that follows.
 */
public final class PriceEncodingWriter<N> {

	private @Nullable PriceEncodingEncoder composite;
	private @Nullable N next;

	PriceEncodingWriter<N> wrap(PriceEncodingEncoder composite, N next) {
		this.composite = composite;
		this.next = next;
		return this;
	}

	public N mantissa(long value) {
		open().mantissa(value);
		return end();
	}

	public N mantissaNull() {
		open().mantissa(PriceEncodingEncoder.mantissaNullValue());
		return end();
	}

	private PriceEncodingEncoder open() {
		if (next == null) {
			throw new IllegalStateException("PriceEncodingWriter has ended");
		}
		return composite;
	}

	private N end() {
		N following = next;
		next = null;
		return following;
	}
}
