package com.example.trading.hand;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.jspecify.annotations.Nullable;

import com.example.trading.sbe.CancelRejectEncoder;
import com.example.trading.sbe.CxlRejReason;
import com.example.trading.sbe.OrdStatus;
import com.example.trading.sbe.SessionHeaderEncoder;
import com.example.trading.sbe.VarLatin1EncodingEncoder;

/**
 * A {@code CancelReject} written as a typestate: the root block's required
 * fields, then the var-data {@code text}, which is the only way to
 * {@code length()}. The bound twins take the record's types; its enums share
 * their names with sbe-tool's, so they are qualified.
 */
public final class CancelRejectWriter {

	public interface RootBlockOrderId {
		RootBlockOrderIdBound bound();

		RootBlockClOrdId orderId(String value);

		RootBlockClOrdId orderId(CharSequence value);

		RootBlockClOrdId putOrderId(byte[] src, int srcOffset);
	}

	public interface RootBlockClOrdId {
		RootBlockClOrdIdBound bound();

		RootBlockOrigClOrdId clOrdId(String value);

		RootBlockOrigClOrdId clOrdId(CharSequence value);

		RootBlockOrigClOrdId putClOrdId(byte[] src, int srcOffset);
	}

	public interface RootBlockOrigClOrdId {
		RootBlockOrigClOrdIdBound bound();

		RootBlockOrdStatus origClOrdId(String value);

		RootBlockOrdStatus origClOrdId(CharSequence value);

		RootBlockOrdStatus putOrigClOrdId(byte[] src, int srcOffset);
	}

	public interface RootBlockOrdStatus {
		RootBlockOrdStatusBound bound();

		RootBlockCxlRejReason ordStatus(OrdStatus value);
	}

	public interface RootBlockCxlRejReason {
		RootBlockCxlRejReasonBound bound();

		RootBlock cxlRejReason(CxlRejReason value);
	}

	/** The root block complete: nothing optional, and the var-data that follows. */
	public interface RootBlock {
		RootBlockBound bound();

		AfterText text(String value);

		AfterText text(byte[] src, int srcOffset, int length);

		AfterText text(DirectBuffer src, int srcOffset, int length);
	}

	public interface RootBlockOrderIdBound {
		RootBlockClOrdIdBound orderId(String value);

		RootBlockOrderId wire();
	}

	public interface RootBlockClOrdIdBound {
		RootBlockOrigClOrdIdBound clOrdId(String value);

		RootBlockClOrdId wire();
	}

	public interface RootBlockOrigClOrdIdBound {
		RootBlockOrdStatusBound origClOrdId(String value);

		RootBlockOrigClOrdId wire();
	}

	public interface RootBlockOrdStatusBound {
		RootBlockCxlRejReasonBound ordStatus(com.example.trading.OrdStatus value);

		RootBlockOrdStatus wire();
	}

	public interface RootBlockCxlRejReasonBound {
		RootBlockBound cxlRejReason(com.example.trading.CxlRejReason value);

		RootBlockCxlRejReason wire();
	}

	/**
	 * The root block complete, bound: the text as the record holds it, in the
	 * schema's encoding.
	 */
	public interface RootBlockBound {
		AfterText text(String value);

		RootBlock wire();
	}

	public interface AfterText {
		int length();
	}

	private enum At {
		BEFORE_ROOT_BLOCK, ROOT_BLOCK, AFTER_TEXT
	}

	private final SessionHeaderEncoder header = new SessionHeaderEncoder();
	private final CancelRejectEncoder encoder = new CancelRejectEncoder();
	private final RootBlockStage rootBlock = new RootBlockStage();
	private final RootBlockBoundStage rootBlockBound = new RootBlockBoundStage();
	private At at = At.BEFORE_ROOT_BLOCK;

	public RootBlockOrderId wrap(MutableDirectBuffer buffer, int offset) {
		encoder.wrapAndApplyHeader(buffer, offset, header);
		header.sequenceNumber(SessionHeaderEncoder.sequenceNumberNullValue());
		nullRootBlock();
		at = At.ROOT_BLOCK;
		return rootBlock;
	}

	public SessionHeaderEncoder header() {
		return header;
	}

	private void nullRootBlock() {
		for (int i = 0; i < CancelRejectEncoder.orderIdLength(); i++) {
			encoder.orderId(i, CancelRejectEncoder.orderIdNullValue());
		}
		for (int i = 0; i < CancelRejectEncoder.clOrdIdLength(); i++) {
			encoder.clOrdId(i, CancelRejectEncoder.clOrdIdNullValue());
		}
		for (int i = 0; i < CancelRejectEncoder.origClOrdIdLength(); i++) {
			encoder.origClOrdId(i, CancelRejectEncoder.origClOrdIdNullValue());
		}
		encoder.ordStatus(OrdStatus.NULL_VAL);
		encoder.cxlRejReason(CxlRejReason.NULL_VAL);
	}

	private void at(At expected, String stage) {
		if (at != expected) {
			throw new IllegalStateException(stage + " is not the current stage");
		}
	}

	private final class RootBlockStage
			implements
				RootBlockOrderId,
				RootBlockClOrdId,
				RootBlockOrigClOrdId,
				RootBlockOrdStatus,
				RootBlockCxlRejReason,
				RootBlock,
				AfterText {

		@Override
		public RootBlockBoundStage bound() {
			at(At.ROOT_BLOCK, "RootBlock");
			return rootBlockBound;
		}

		@Override
		public RootBlockClOrdId orderId(String value) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.orderId(value);
			return this;
		}

		@Override
		public RootBlockClOrdId orderId(CharSequence value) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.orderId(value);
			return this;
		}

		@Override
		public RootBlockClOrdId putOrderId(byte[] src, int srcOffset) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.putOrderId(src, srcOffset);
			return this;
		}

		@Override
		public RootBlockOrigClOrdId clOrdId(String value) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.clOrdId(value);
			return this;
		}

		@Override
		public RootBlockOrigClOrdId clOrdId(CharSequence value) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.clOrdId(value);
			return this;
		}

		@Override
		public RootBlockOrigClOrdId putClOrdId(byte[] src, int srcOffset) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.putClOrdId(src, srcOffset);
			return this;
		}

		@Override
		public RootBlockOrdStatus origClOrdId(String value) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.origClOrdId(value);
			return this;
		}

		@Override
		public RootBlockOrdStatus origClOrdId(CharSequence value) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.origClOrdId(value);
			return this;
		}

		@Override
		public RootBlockOrdStatus putOrigClOrdId(byte[] src, int srcOffset) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.putOrigClOrdId(src, srcOffset);
			return this;
		}

		@Override
		public RootBlockCxlRejReason ordStatus(OrdStatus value) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.ordStatus(value);
			return this;
		}

		@Override
		public RootBlock cxlRejReason(CxlRejReason value) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.cxlRejReason(value);
			return this;
		}

		@Override
		public AfterText text(String value) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.text(value);
			at = At.AFTER_TEXT;
			return this;
		}

		@Override
		public AfterText text(byte[] src, int srcOffset, int length) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.putText(src, srcOffset, length);
			at = At.AFTER_TEXT;
			return this;
		}

		@Override
		public AfterText text(DirectBuffer src, int srcOffset, int length) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.putText(src, srcOffset, length);
			at = At.AFTER_TEXT;
			return this;
		}

		@Override
		public int length() {
			at(At.AFTER_TEXT, "AfterText");
			return SessionHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength();
		}
	}

	private final class RootBlockBoundStage
			implements
				RootBlockOrderIdBound,
				RootBlockClOrdIdBound,
				RootBlockOrigClOrdIdBound,
				RootBlockOrdStatusBound,
				RootBlockCxlRejReasonBound,
				RootBlockBound {

		@Override
		public RootBlockClOrdIdBound orderId(String value) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.orderId(ascii(required(value, "orderId"), CancelRejectEncoder.orderIdLength(), "orderId"));
			return this;
		}

		@Override
		public RootBlockOrigClOrdIdBound clOrdId(String value) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.clOrdId(ascii(required(value, "clOrdId"), CancelRejectEncoder.clOrdIdLength(), "clOrdId"));
			return this;
		}

		@Override
		public RootBlockOrdStatusBound origClOrdId(String value) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.origClOrdId(
					ascii(required(value, "origClOrdId"), CancelRejectEncoder.origClOrdIdLength(), "origClOrdId")
			);
			return this;
		}

		@Override
		public RootBlockCxlRejReasonBound ordStatus(com.example.trading.OrdStatus value) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.ordStatus(encodeOrdStatus(required(value, "ordStatus")));
			return this;
		}

		@Override
		public RootBlockBound cxlRejReason(com.example.trading.CxlRejReason value) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.cxlRejReason(encodeCxlRejReason(required(value, "cxlRejReason")));
			return this;
		}

		@Override
		public AfterText text(String value) {
			at(At.ROOT_BLOCK, "RootBlock");
			byte[] bytes = encoded(
					required(value, "text"), StandardCharsets.ISO_8859_1,
					VarLatin1EncodingEncoder.lengthMaxValue(), "text"
			);
			return rootBlock.text(bytes, 0, bytes.length);
		}

		@Override
		public RootBlockStage wire() {
			at(At.ROOT_BLOCK, "RootBlock");
			return rootBlock;
		}
	}

	private static <T> T required(@Nullable T value, String field) {
		if (value == null) {
			throw new IllegalArgumentException(field + " is required");
		}
		return value;
	}

	private static String ascii(String value, int length, String field) {
		if (value.length() > length) {
			throw new IllegalArgumentException(field + " is longer than " + length + ": " + value);
		}
		for (int i = 0; i < value.length(); i++) {
			if (value.charAt(i) > 127) {
				throw new IllegalArgumentException(field + " is not ASCII: " + value);
			}
		}
		return value;
	}

	/**
	 * Through a reporting encoder, never {@code String.getBytes}, so a character
	 * the charset lacks is refused.
	 */
	private static byte[] encoded(String value, Charset charset, long maxLength, String field) {
		ByteBuffer bytes;
		try {
			bytes = charset.newEncoder().encode(CharBuffer.wrap(value));
		} catch (CharacterCodingException e) {
			throw new IllegalArgumentException(field + " cannot be written in " + charset.name() + ": " + value, e);
		}
		if (bytes.remaining() > maxLength) {
			throw new IllegalArgumentException(
					field + " is longer than " + maxLength + " bytes in " + charset.name() + ": " + bytes.remaining()
			);
		}
		byte[] encoded = new byte[bytes.remaining()];
		bytes.get(encoded);
		return encoded;
	}

	private static OrdStatus encodeOrdStatus(com.example.trading.OrdStatus value) {
		return switch (value) {
			case NEW -> OrdStatus.NEW;
			case PARTIALLY_FILLED -> OrdStatus.PARTIALLY_FILLED;
			case FILLED -> OrdStatus.FILLED;
			case CANCELED -> OrdStatus.CANCELED;
			case REJECTED -> OrdStatus.REJECTED;
			case UNKNOWN -> throw new IllegalArgumentException("OrdStatus.UNKNOWN has no wire form");
		};
	}

	private static CxlRejReason encodeCxlRejReason(com.example.trading.CxlRejReason value) {
		return switch (value) {
			case TOO_LATE -> CxlRejReason.TOO_LATE;
			case UNKNOWN_ORDER -> CxlRejReason.UNKNOWN_ORDER;
			case OTHER -> CxlRejReason.OTHER;
		};
	}
}
