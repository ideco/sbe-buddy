package com.example.trading.hand;

import org.jspecify.annotations.Nullable;

import com.example.trading.sbe.QtyEncodingEncoder;

/**
 * The composite {@code QtyEncoding} as a chain of every member in wire order,
 * constants left out: {@code mantissa} alone, since {@code exponent} is a
 * constant, and it returns the stage {@code N} that follows.
 */
public final class QtyEncodingWriter<N> {

	private @Nullable QtyEncodingEncoder composite;
	private @Nullable N next;

	QtyEncodingWriter<N> wrap(QtyEncodingEncoder composite, N next) {
		this.composite = composite;
		this.next = next;
		return this;
	}

	public N mantissa(int value) {
		open().mantissa(value);
		return end();
	}

	private QtyEncodingEncoder open() {
		if (next == null) {
			throw new IllegalStateException("QtyEncodingWriter has ended");
		}
		return composite;
	}

	private N end() {
		N following = next;
		next = null;
		return following;
	}
}
