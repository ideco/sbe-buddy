package com.example.trading.hand;

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.jspecify.annotations.Nullable;

import com.example.trading.sbe.CancelRejectDecoder;
import com.example.trading.sbe.CxlRejReason;
import com.example.trading.sbe.OrdStatus;
import com.example.trading.sbe.SessionHeaderDecoder;

/**
 * A {@code CancelReject} as the sequence of its stages: the root block, then
 * the var-data {@code text}. A var-data is passed over on arrival, its start
 * and length kept, so it reads again and again and the limit is always past
 * what the reader has visited. The bound stages carry the record's components;
 * its enums share their names with sbe-tool's, so they are qualified.
 */
public final class CancelRejectReader
		implements
			Iterable<CancelRejectReader.Stage>,
			Iterator<CancelRejectReader.Stage> {

	/** The stages of a {@code CancelReject}, in the order they come. */
	public sealed interface Stage extends com.example.trading.hand.Stage permits RootBlock, Text {
	}

	private enum At {
		BEFORE_ROOT_BLOCK, ROOT_BLOCK, TEXT, END
	}

	private final SessionHeaderDecoder header = new SessionHeaderDecoder();
	private final CancelRejectDecoder decoder = new CancelRejectDecoder();
	private final CancelRejectDecoder measure = new CancelRejectDecoder();
	private final RootBlock rootBlock = new RootBlock();
	private final RootBlockBound rootBlockBound = new RootBlockBound();
	private final Text text = new Text();
	private final TextBound textBound = new TextBound();
	private @Nullable DirectBuffer buffer;
	private int textStart; // the limit on arrival at the text, where its prefix lies
	private int textLength;
	private At at = At.BEFORE_ROOT_BLOCK;

	public CancelRejectReader wrap(DirectBuffer buffer, int offset) {
		this.buffer = buffer;
		decoder.wrapAndApplyHeader(buffer, offset, header);
		at = At.BEFORE_ROOT_BLOCK;
		return this;
	}

	public CancelRejectReader rewind() {
		decoder.sbeRewind();
		at = At.BEFORE_ROOT_BLOCK;
		return this;
	}

	public SessionHeaderDecoder header() {
		return header;
	}

	public int actingVersion() {
		return decoder.actingVersion();
	}

	public int decodedLength() {
		if (!hasNext()) {
			return SessionHeaderDecoder.ENCODED_LENGTH + decoder.encodedLength();
		}
		return SessionHeaderDecoder.ENCODED_LENGTH
				+ measure.wrap(buffer, decoder.offset(), header.blockLength(), header.version()).sbeDecodedLength();
	}

	@Override
	public Iterator<Stage> iterator() {
		return this;
	}

	@Override
	public boolean hasNext() {
		return switch (at) {
			case BEFORE_ROOT_BLOCK -> true;
			case ROOT_BLOCK -> textPresent();
			case TEXT, END -> false;
		};
	}

	@Override
	public Stage next() {
		return switch (at) {
			case BEFORE_ROOT_BLOCK -> {
				at = At.ROOT_BLOCK;
				yield rootBlock;
			}
			case ROOT_BLOCK -> textPresent() ? arriveAtText() : end();
			case TEXT, END -> end();
		};
	}

	private boolean textPresent() {
		return decoder.actingVersion() >= CancelRejectDecoder.textSinceVersion();
	}

	/** Keeps where the text lies and moves the limit past it. */
	private Stage arriveAtText() {
		textStart = decoder.limit();
		textLength = decoder.skipText();
		at = At.TEXT;
		return text;
	}

	private Stage end() {
		at = At.END;
		throw new NoSuchElementException();
	}

	private void open(At first, At last, String stage) {
		if (at.compareTo(first) < 0 || at.compareTo(last) > 0) {
			throw new IllegalStateException(stage + " is not open");
		}
	}

	private void current(At expected, String stage) {
		if (at != expected) {
			throw new IllegalStateException(stage + " is not the current stage");
		}
	}

	/** The root block. */
	public final class RootBlock implements Stage {

		private RootBlock() {
		}

		private void open() {
			CancelRejectReader.this.open(At.ROOT_BLOCK, At.END, "RootBlock");
		}

		public String orderId() {
			open();
			return decoder.orderId();
		}

		public byte orderId(int index) {
			open();
			return decoder.orderId(index);
		}

		public int getOrderId(byte[] dst, int dstOffset) {
			open();
			return decoder.getOrderId(dst, dstOffset);
		}

		public int getOrderId(Appendable value) {
			open();
			return decoder.getOrderId(value);
		}

		public String clOrdId() {
			open();
			return decoder.clOrdId();
		}

		public byte clOrdId(int index) {
			open();
			return decoder.clOrdId(index);
		}

		public int getClOrdId(byte[] dst, int dstOffset) {
			open();
			return decoder.getClOrdId(dst, dstOffset);
		}

		public int getClOrdId(Appendable value) {
			open();
			return decoder.getClOrdId(value);
		}

		public String origClOrdId() {
			open();
			return decoder.origClOrdId();
		}

		public byte origClOrdId(int index) {
			open();
			return decoder.origClOrdId(index);
		}

		public int getOrigClOrdId(byte[] dst, int dstOffset) {
			open();
			return decoder.getOrigClOrdId(dst, dstOffset);
		}

		public int getOrigClOrdId(Appendable value) {
			open();
			return decoder.getOrigClOrdId(value);
		}

		public OrdStatus ordStatus() {
			open();
			return decoder.ordStatus();
		}

		public byte ordStatusRaw() {
			open();
			return decoder.ordStatusRaw();
		}

		public CxlRejReason cxlRejReason() {
			open();
			return decoder.cxlRejReason();
		}

		public short cxlRejReasonRaw() {
			open();
			return decoder.cxlRejReasonRaw();
		}

		public RootBlockBound bound() {
			open();
			return rootBlockBound;
		}

		@Override
		public void skip() {
			current(At.ROOT_BLOCK, "RootBlock");
			decoder.sbeSkip();
			at = At.END;
		}
	}

	/**
	 * The var-data {@code text}: read where it lies, as often as wanted, through
	 * sbe-tool's own accessors.
	 */
	public final class Text implements Stage {

		private Text() {
		}

		private void open() {
			CancelRejectReader.this.open(At.TEXT, At.TEXT, "Text");
		}

		public int length() {
			open();
			return textLength;
		}

		/** The whole text into {@code dst}; the bytes copied. */
		public int copyTo(MutableDirectBuffer dst, int dstOffset) {
			open();
			int limit = decoder.limit();
			decoder.limit(textStart);
			int copied = decoder.getText(dst, dstOffset, textLength);
			decoder.limit(limit);
			return copied;
		}

		public int copyTo(byte[] dst, int dstOffset) {
			open();
			int limit = decoder.limit();
			decoder.limit(textStart);
			int copied = decoder.getText(dst, dstOffset, textLength);
			decoder.limit(limit);
			return copied;
		}

		/** {@code window} over the text's bytes, nothing copied. */
		public void wrap(DirectBuffer window) {
			open();
			int limit = decoder.limit();
			decoder.limit(textStart);
			decoder.wrapText(window);
			decoder.limit(limit);
		}

		public TextBound bound() {
			open();
			return textBound;
		}

		/** Nothing to prune. */
		@Override
		public void skip() {
			current(At.TEXT, "Text");
		}
	}

	/** {@code CancelReject}'s components of the root block. */
	public final class RootBlockBound {

		private RootBlockBound() {
		}

		public String orderId() {
			rootBlock.open();
			return decoder.orderId();
		}

		public String clOrdId() {
			rootBlock.open();
			return decoder.clOrdId();
		}

		public String origClOrdId() {
			rootBlock.open();
			return decoder.origClOrdId();
		}

		/** The record's unknown value for a wire value the schema does not name. */
		public com.example.trading.OrdStatus ordStatus() {
			rootBlock.open();
			return decodeOrdStatus(decoder.ordStatusRaw());
		}

		public com.example.trading.CxlRejReason cxlRejReason() {
			rootBlock.open();
			return decodeCxlRejReason(decoder.cxlRejReasonRaw());
		}

		public RootBlock wire() {
			rootBlock.open();
			return rootBlock;
		}
	}

	/**
	 * The record's {@code text}: the {@code String} sbe-tool decodes, built on each
	 * call.
	 */
	public final class TextBound {

		private TextBound() {
		}

		public String value() {
			text.open();
			int limit = decoder.limit();
			decoder.limit(textStart);
			String value = decoder.text();
			decoder.limit(limit);
			return value;
		}

		public Text wire() {
			text.open();
			return text;
		}
	}

	private static com.example.trading.OrdStatus decodeOrdStatus(byte raw) {
		return switch (raw) {
			case (byte) 48 -> com.example.trading.OrdStatus.NEW;
			case (byte) 49 -> com.example.trading.OrdStatus.PARTIALLY_FILLED;
			case (byte) 50 -> com.example.trading.OrdStatus.FILLED;
			case (byte) 52 -> com.example.trading.OrdStatus.CANCELED;
			case (byte) 56 -> com.example.trading.OrdStatus.REJECTED;
			default -> com.example.trading.OrdStatus.UNKNOWN;
		};
	}

	private static com.example.trading.CxlRejReason decodeCxlRejReason(short raw) {
		return switch (raw) {
			case (short) 0 -> com.example.trading.CxlRejReason.TOO_LATE;
			case (short) 1 -> com.example.trading.CxlRejReason.UNKNOWN_ORDER;
			case (short) 99 -> com.example.trading.CxlRejReason.OTHER;
			default -> throw new IllegalArgumentException("CxlRejReason has no value " + raw);
		};
	}
}
