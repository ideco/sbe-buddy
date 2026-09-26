package com.example.trading.hand;

import org.agrona.MutableDirectBuffer;
import org.jspecify.annotations.Nullable;

import com.example.trading.sbe.NewOrderEncoder;
import com.example.trading.sbe.NewOrderEncoder.PartiesEncoder;
import com.example.trading.sbe.NewOrderEncoder.PartiesEncoder.PartySubIdsEncoder;
import com.example.trading.sbe.OrdType;
import com.example.trading.sbe.PartyRole;
import com.example.trading.sbe.PriceEncodingEncoder;
import com.example.trading.sbe.QtyEncodingEncoder;
import com.example.trading.sbe.SessionHeaderEncoder;
import com.example.trading.sbe.Side;
import com.example.trading.sbe.TimeInForce;

/**
 * A {@code NewOrder} written as a typestate over sbe-tool's encoder: every
 * required field, group and var-data is a stage whose only way on is the next
 * one, and {@code length()} exists only once the last of them is written. All
 * of a block's stages are one object, an inner class per block (a class may not
 * implement its own nested interfaces), so a setter returns {@code this} typed
 * as what comes next and nothing is allocated. A block is filled with its null
 * values when it opens, so what the chain does not set is null on the wire.
 */
public final class NewOrderWriter {

	// ---- the root block, one stage per required field in wire order, then the
	// block complete

	public interface RootBlockClOrdId {
		RootBlockAccount clOrdId(String value);

		RootBlockAccount clOrdId(CharSequence value);

		RootBlockAccount putClOrdId(byte[] src, int srcOffset);
	}

	public interface RootBlockAccount {
		RootBlockSymbol account(String value);

		RootBlockSymbol account(CharSequence value);

		RootBlockSymbol putAccount(byte[] src, int srcOffset);
	}

	public interface RootBlockSymbol {
		RootBlockSide symbol(String value);

		RootBlockSide symbol(CharSequence value);

		RootBlockSide putSymbol(byte[] src, int srcOffset);
	}

	public interface RootBlockSide {
		RootBlockOrdType side(Side value);
	}

	public interface RootBlockOrdType {
		RootBlockTimeInForce ordType(OrdType value);
	}

	public interface RootBlockTimeInForce {
		RootBlockExecInst timeInForce(TimeInForce value);
	}

	public interface RootBlockExecInst {
		/** The set's choices, then {@code end()}. */
		ExecInstWriter<RootBlockTransactTime> execInst();
	}

	public interface RootBlockTransactTime {
		RootBlockOrderQty transactTime(long value);
	}

	public interface RootBlockOrderQty {
		QtyEncodingWriter<RootBlock> orderQty();
	}

	/**
	 * The root block complete: its optional fields in any order, and the first
	 * group.
	 */
	public interface RootBlock {
		PriceEncodingWriter<RootBlock> price();

		PriceEncodingWriter<RootBlock> stopPx();

		Parties parties();
	}

	// ---- the parties group and its entries

	public interface Parties {
		PartiesEntryPartyId entry();

		/** Settles the count to the entries written. */
		AfterParties end();
	}

	public interface PartiesEntryPartyId {
		PartiesEntryPartyRole partyId(String value);

		PartiesEntryPartyRole partyId(CharSequence value);

		PartiesEntryPartyRole putPartyId(byte[] src, int srcOffset);
	}

	public interface PartiesEntryPartyRole {
		PartiesEntry partyRole(PartyRole value);
	}

	/**
	 * A parties entry complete: nothing optional, and its nested group, which must
	 * be opened.
	 */
	public interface PartiesEntry {
		PartySubIds partySubIds();
	}

	public interface PartySubIds {
		PartySubIdsEntryPartySubId entry();

		/** Back into the parties group, for its next entry or its end. */
		Parties end();
	}

	public interface PartySubIdsEntryPartySubId {
		PartySubIdsEntryPartySubIdType partySubId(String value);

		PartySubIdsEntryPartySubIdType partySubId(CharSequence value);

		PartySubIdsEntryPartySubIdType putPartySubId(byte[] src, int srcOffset);
	}

	public interface PartySubIdsEntryPartySubIdType {
		/** The entry's last field; the entry is complete and the group takes over. */
		PartySubIds partySubIdType(short value);
	}

	/** After the last group: the message is complete. */
	public interface AfterParties {
		/** The bytes written, header included. */
		int length();
	}

	/** Where the writer is; a stage's methods want it at one place. */
	private enum At {
		BEFORE_ROOT_BLOCK, ROOT_BLOCK, PARTIES, PARTIES_ENTRY, PARTY_SUB_IDS, PARTY_SUB_IDS_ENTRY, AFTER_PARTIES
	}

	private final SessionHeaderEncoder header = new SessionHeaderEncoder();
	private final NewOrderEncoder encoder = new NewOrderEncoder();
	private final RootBlockStage rootBlock = new RootBlockStage();
	private final ExecInstWriter<RootBlockTransactTime> execInst = new ExecInstWriter<>();
	private final QtyEncodingWriter<RootBlock> orderQty = new QtyEncodingWriter<>();
	private final PriceEncodingWriter<RootBlock> price = new PriceEncodingWriter<>();
	private final PriceEncodingWriter<RootBlock> stopPx = new PriceEncodingWriter<>();
	private final PartiesStage parties = new PartiesStage();
	private final PartiesEntryStage partiesEntry = new PartiesEntryStage();
	private final PartySubIdsStage partySubIds = new PartySubIdsStage();
	private final PartySubIdsEntryStage partySubIdsEntry = new PartySubIdsEntryStage();
	private @Nullable PartiesEncoder partiesEncoder; // encoder.partiesCount(...), from the moment the group opens
	private @Nullable PartySubIdsEncoder partySubIdsEncoder;
	private At at = At.BEFORE_ROOT_BLOCK;

	/**
	 * Writes the header, the standard four members and the rest as their null
	 * values, fills the root block with null values and stands at its first
	 * required field.
	 */
	public RootBlockClOrdId wrap(MutableDirectBuffer buffer, int offset) {
		encoder.wrapAndApplyHeader(buffer, offset, header);
		header.sequenceNumber(SessionHeaderEncoder.sequenceNumberNullValue());
		nullRootBlock();
		at = At.ROOT_BLOCK;
		return rootBlock;
	}

	/** The header as written, for its own members. */
	public SessionHeaderEncoder header() {
		return header;
	}

	private void nullRootBlock() {
		for (int i = 0; i < NewOrderEncoder.clOrdIdLength(); i++) {
			encoder.clOrdId(i, NewOrderEncoder.clOrdIdNullValue());
		}
		for (int i = 0; i < NewOrderEncoder.accountLength(); i++) {
			encoder.account(i, NewOrderEncoder.accountNullValue());
		}
		for (int i = 0; i < NewOrderEncoder.symbolLength(); i++) {
			encoder.symbol(i, NewOrderEncoder.symbolNullValue());
		}
		encoder.side(Side.NULL_VAL);
		encoder.ordType(OrdType.NULL_VAL);
		encoder.timeInForce(TimeInForce.NULL_VAL);
		encoder.execInst().clear();
		encoder.transactTime(NewOrderEncoder.transactTimeNullValue());
		encoder.orderQty().mantissa(QtyEncodingEncoder.mantissaNullValue());
		encoder.price().mantissa(PriceEncodingEncoder.mantissaNullValue());
		encoder.stopPx().mantissa(PriceEncodingEncoder.mantissaNullValue());
	}

	private void at(At expected, String stage) {
		if (at != expected) {
			throw new IllegalStateException(stage + " is not the current stage");
		}
	}

	// ---- the root block's stages, one object

	private final class RootBlockStage
			implements
				RootBlockClOrdId,
				RootBlockAccount,
				RootBlockSymbol,
				RootBlockSide,
				RootBlockOrdType,
				RootBlockTimeInForce,
				RootBlockExecInst,
				RootBlockTransactTime,
				RootBlockOrderQty,
				RootBlock,
				AfterParties {

		@Override
		public RootBlockAccount clOrdId(String value) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.clOrdId(value);
			return this;
		}

		@Override
		public RootBlockAccount clOrdId(CharSequence value) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.clOrdId(value);
			return this;
		}

		@Override
		public RootBlockAccount putClOrdId(byte[] src, int srcOffset) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.putClOrdId(src, srcOffset);
			return this;
		}

		@Override
		public RootBlockSymbol account(String value) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.account(value);
			return this;
		}

		@Override
		public RootBlockSymbol account(CharSequence value) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.account(value);
			return this;
		}

		@Override
		public RootBlockSymbol putAccount(byte[] src, int srcOffset) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.putAccount(src, srcOffset);
			return this;
		}

		@Override
		public RootBlockSide symbol(String value) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.symbol(value);
			return this;
		}

		@Override
		public RootBlockSide symbol(CharSequence value) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.symbol(value);
			return this;
		}

		@Override
		public RootBlockSide putSymbol(byte[] src, int srcOffset) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.putSymbol(src, srcOffset);
			return this;
		}

		@Override
		public RootBlockOrdType side(Side value) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.side(value);
			return this;
		}

		@Override
		public RootBlockTimeInForce ordType(OrdType value) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.ordType(value);
			return this;
		}

		@Override
		public RootBlockExecInst timeInForce(TimeInForce value) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.timeInForce(value);
			return this;
		}

		@Override
		public ExecInstWriter<RootBlockTransactTime> execInst() {
			at(At.ROOT_BLOCK, "RootBlock");
			return execInst.wrap(encoder.execInst(), this);
		}

		@Override
		public RootBlockOrderQty transactTime(long value) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.transactTime(value);
			return this;
		}

		@Override
		public QtyEncodingWriter<RootBlock> orderQty() {
			at(At.ROOT_BLOCK, "RootBlock");
			return orderQty.wrap(encoder.orderQty(), this);
		}

		@Override
		public PriceEncodingWriter<RootBlock> price() {
			at(At.ROOT_BLOCK, "RootBlock");
			return price.wrap(encoder.price(), this);
		}

		@Override
		public PriceEncodingWriter<RootBlock> stopPx() {
			at(At.ROOT_BLOCK, "RootBlock");
			return stopPx.wrap(encoder.stopPx(), this);
		}

		/**
		 * Opens the group with room for its maximum; {@code end()} settles the count.
		 */
		@Override
		public Parties parties() {
			at(At.ROOT_BLOCK, "RootBlock");
			partiesEncoder = encoder.partiesCount(PartiesEncoder.countMaxValue());
			at = At.PARTIES;
			return parties;
		}

		@Override
		public int length() {
			at(At.AFTER_PARTIES, "AfterParties");
			return SessionHeaderEncoder.ENCODED_LENGTH + encoder.encodedLength();
		}
	}

	// ---- the groups' stages, one object per block

	private final class PartiesStage implements Parties {

		@Override
		public PartiesEntryPartyId entry() {
			at(At.PARTIES, "Parties");
			partiesEncoder.next();
			for (int i = 0; i < PartiesEncoder.partyIdLength(); i++) {
				partiesEncoder.partyId(i, PartiesEncoder.partyIdNullValue());
			}
			partiesEncoder.partyRole(PartyRole.NULL_VAL);
			at = At.PARTIES_ENTRY;
			return partiesEntry;
		}

		@Override
		public AfterParties end() {
			at(At.PARTIES, "Parties");
			partiesEncoder.resetCountToIndex();
			at = At.AFTER_PARTIES;
			return rootBlock;
		}
	}

	private final class PartiesEntryStage implements PartiesEntryPartyId, PartiesEntryPartyRole, PartiesEntry {

		@Override
		public PartiesEntryPartyRole partyId(String value) {
			at(At.PARTIES_ENTRY, "PartiesEntry");
			partiesEncoder.partyId(value);
			return this;
		}

		@Override
		public PartiesEntryPartyRole partyId(CharSequence value) {
			at(At.PARTIES_ENTRY, "PartiesEntry");
			partiesEncoder.partyId(value);
			return this;
		}

		@Override
		public PartiesEntryPartyRole putPartyId(byte[] src, int srcOffset) {
			at(At.PARTIES_ENTRY, "PartiesEntry");
			partiesEncoder.putPartyId(src, srcOffset);
			return this;
		}

		@Override
		public PartiesEntry partyRole(PartyRole value) {
			at(At.PARTIES_ENTRY, "PartiesEntry");
			partiesEncoder.partyRole(value);
			return this;
		}

		@Override
		public PartySubIds partySubIds() {
			at(At.PARTIES_ENTRY, "PartiesEntry");
			partySubIdsEncoder = partiesEncoder.partySubIdsCount(PartySubIdsEncoder.countMaxValue());
			at = At.PARTY_SUB_IDS;
			return partySubIds;
		}
	}

	private final class PartySubIdsStage implements PartySubIds {

		@Override
		public PartySubIdsEntryPartySubId entry() {
			at(At.PARTY_SUB_IDS, "PartySubIds");
			partySubIdsEncoder.next();
			for (int i = 0; i < PartySubIdsEncoder.partySubIdLength(); i++) {
				partySubIdsEncoder.partySubId(i, PartySubIdsEncoder.partySubIdNullValue());
			}
			partySubIdsEncoder.partySubIdType(PartySubIdsEncoder.partySubIdTypeNullValue());
			at = At.PARTY_SUB_IDS_ENTRY;
			return partySubIdsEntry;
		}

		@Override
		public Parties end() {
			at(At.PARTY_SUB_IDS, "PartySubIds");
			partySubIdsEncoder.resetCountToIndex();
			at = At.PARTIES;
			return parties;
		}
	}

	private final class PartySubIdsEntryStage implements PartySubIdsEntryPartySubId, PartySubIdsEntryPartySubIdType {

		@Override
		public PartySubIdsEntryPartySubIdType partySubId(String value) {
			at(At.PARTY_SUB_IDS_ENTRY, "PartySubIdsEntry");
			partySubIdsEncoder.partySubId(value);
			return this;
		}

		@Override
		public PartySubIdsEntryPartySubIdType partySubId(CharSequence value) {
			at(At.PARTY_SUB_IDS_ENTRY, "PartySubIdsEntry");
			partySubIdsEncoder.partySubId(value);
			return this;
		}

		@Override
		public PartySubIdsEntryPartySubIdType putPartySubId(byte[] src, int srcOffset) {
			at(At.PARTY_SUB_IDS_ENTRY, "PartySubIdsEntry");
			partySubIdsEncoder.putPartySubId(src, srcOffset);
			return this;
		}

		@Override
		public PartySubIds partySubIdType(short value) {
			at(At.PARTY_SUB_IDS_ENTRY, "PartySubIdsEntry");
			partySubIdsEncoder.partySubIdType(value);
			at = At.PARTY_SUB_IDS;
			return partySubIds;
		}
	}
}
