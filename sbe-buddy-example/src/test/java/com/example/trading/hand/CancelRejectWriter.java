package com.example.trading.hand;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

import com.example.trading.sbe.CancelRejectEncoder;
import com.example.trading.sbe.CxlRejReason;
import com.example.trading.sbe.OrdStatus;
import com.example.trading.sbe.SessionHeaderEncoder;

/**
 * A {@code CancelReject} written as a typestate: the root block's required
 * fields, then the var-data {@code text}, which is the only way to
 * {@code length()}.
 */
public final class CancelRejectWriter {

	public interface RootBlockOrderId {
		RootBlockClOrdId orderId(String value);

		RootBlockClOrdId orderId(CharSequence value);

		RootBlockClOrdId putOrderId(byte[] src, int srcOffset);
	}

	public interface RootBlockClOrdId {
		RootBlockOrigClOrdId clOrdId(String value);

		RootBlockOrigClOrdId clOrdId(CharSequence value);

		RootBlockOrigClOrdId putClOrdId(byte[] src, int srcOffset);
	}

	public interface RootBlockOrigClOrdId {
		RootBlockOrdStatus origClOrdId(String value);

		RootBlockOrdStatus origClOrdId(CharSequence value);

		RootBlockOrdStatus putOrigClOrdId(byte[] src, int srcOffset);
	}

	public interface RootBlockOrdStatus {
		RootBlockCxlRejReason ordStatus(OrdStatus value);
	}

	public interface RootBlockCxlRejReason {
		RootBlock cxlRejReason(CxlRejReason value);
	}

	/** The root block complete: nothing optional, and the var-data that follows. */
	public interface RootBlock {
		AfterText text(String value);

		AfterText text(byte[] src, int srcOffset, int length);

		AfterText text(DirectBuffer src, int srcOffset, int length);
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
}
