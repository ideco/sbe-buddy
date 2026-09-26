package com.example.trading.hand;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import org.agrona.MutableDirectBuffer;
import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.BindingContext;
import net.concini.sbebuddy.Presence;
import net.concini.sbebuddy.PrimitiveType;

import com.example.trading.ExecInst;
import com.example.trading.PriceBinding;
import com.example.trading.PriceEncoding;
import com.example.trading.QtyBinding;
import com.example.trading.QtyEncoding;
import com.example.trading.UtcTimestampBinding;
import com.example.trading.sbe.ExecInstEncoder;
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
 * Every stage that takes a value has a bound twin taking the record's type
 * through its binding, reached by {@code bound()} and left by {@code wire()};
 * the twin is a second object per block, since wire and bound setters of a
 * {@code String} field share a signature and differ in what they return. The
 * record's enums share their names with sbe-tool's, imported for the wire
 * stages, so they are qualified.
 */
public final class NewOrderWriter {

	// ---- the root block, one stage per required field in wire order, then the
	// block complete

	public interface RootBlockClOrdId {
		RootBlockClOrdIdBound bound();

		RootBlockAccount clOrdId(String value);

		RootBlockAccount clOrdId(CharSequence value);

		RootBlockAccount putClOrdId(byte[] src, int srcOffset);
	}

	public interface RootBlockAccount {
		RootBlockAccountBound bound();

		RootBlockSymbol account(String value);

		RootBlockSymbol account(CharSequence value);

		RootBlockSymbol putAccount(byte[] src, int srcOffset);
	}

	public interface RootBlockSymbol {
		RootBlockSymbolBound bound();

		RootBlockSide symbol(String value);

		RootBlockSide symbol(CharSequence value);

		RootBlockSide putSymbol(byte[] src, int srcOffset);
	}

	public interface RootBlockSide {
		RootBlockSideBound bound();

		RootBlockOrdType side(Side value);
	}

	public interface RootBlockOrdType {
		RootBlockOrdTypeBound bound();

		RootBlockTimeInForce ordType(OrdType value);
	}

	public interface RootBlockTimeInForce {
		RootBlockTimeInForceBound bound();

		RootBlockExecInst timeInForce(TimeInForce value);
	}

	public interface RootBlockExecInst {
		RootBlockExecInstBound bound();

		/** The set's choices, then {@code end()}. */
		ExecInstWriter<RootBlockTransactTime> execInst();
	}

	public interface RootBlockTransactTime {
		RootBlockTransactTimeBound bound();

		RootBlockOrderQty transactTime(long value);
	}

	public interface RootBlockOrderQty {
		RootBlockOrderQtyBound bound();

		QtyEncodingWriter<RootBlock> orderQty();
	}

	/**
	 * The root block complete: its optional fields in any order, and the first
	 * group.
	 */
	public interface RootBlock {
		RootBlockBound bound();

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
		PartiesEntryPartyIdBound bound();

		PartiesEntryPartyRole partyId(String value);

		PartiesEntryPartyRole partyId(CharSequence value);

		PartiesEntryPartyRole putPartyId(byte[] src, int srcOffset);
	}

	public interface PartiesEntryPartyRole {
		PartiesEntryPartyRoleBound bound();

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
		PartySubIdsEntryPartySubIdBound bound();

		PartySubIdsEntryPartySubIdType partySubId(String value);

		PartySubIdsEntryPartySubIdType partySubId(CharSequence value);

		PartySubIdsEntryPartySubIdType putPartySubId(byte[] src, int srcOffset);
	}

	public interface PartySubIdsEntryPartySubIdType {
		PartySubIdsEntryPartySubIdTypeBound bound();

		/** The entry's last field; the entry is complete and the group takes over. */
		PartySubIds partySubIdType(short value);
	}

	// ---- the bound twins: the record's types through the bindings, one per stage
	// that takes a value

	public interface RootBlockClOrdIdBound {
		RootBlockAccountBound clOrdId(String value);

		RootBlockClOrdId wire();
	}

	public interface RootBlockAccountBound {
		RootBlockSymbolBound account(String value);

		RootBlockAccount wire();
	}

	public interface RootBlockSymbolBound {
		RootBlockSideBound symbol(String value);

		RootBlockSymbol wire();
	}

	public interface RootBlockSideBound {
		RootBlockOrdTypeBound side(com.example.trading.Side value);

		RootBlockSide wire();
	}

	public interface RootBlockOrdTypeBound {
		RootBlockTimeInForceBound ordType(com.example.trading.OrdType value);

		RootBlockOrdType wire();
	}

	public interface RootBlockTimeInForceBound {
		RootBlockExecInstBound timeInForce(com.example.trading.TimeInForce value);

		RootBlockTimeInForce wire();
	}

	public interface RootBlockExecInstBound {
		RootBlockTransactTimeBound execInst(Set<ExecInst> value);

		RootBlockExecInst wire();
	}

	public interface RootBlockTransactTimeBound {
		RootBlockOrderQtyBound transactTime(Instant value);

		RootBlockTransactTime wire();
	}

	public interface RootBlockOrderQtyBound {
		RootBlockBound orderQty(long value);

		RootBlockOrderQty wire();
	}

	/** The root block complete, bound: {@code null} is the optional's absence. */
	public interface RootBlockBound {
		RootBlockBound price(@Nullable BigDecimal value);

		RootBlockBound stopPx(@Nullable BigDecimal value);

		Parties parties();

		RootBlock wire();
	}

	public interface PartiesEntryPartyIdBound {
		PartiesEntryPartyRoleBound partyId(String value);

		PartiesEntryPartyId wire();
	}

	public interface PartiesEntryPartyRoleBound {
		PartiesEntry partyRole(com.example.trading.PartyRole value);

		PartiesEntryPartyRole wire();
	}

	public interface PartySubIdsEntryPartySubIdBound {
		PartySubIdsEntryPartySubIdTypeBound partySubId(String value);

		PartySubIdsEntryPartySubId wire();
	}

	public interface PartySubIdsEntryPartySubIdTypeBound {
		PartySubIds partySubIdType(short value);

		PartySubIdsEntryPartySubIdType wire();
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

	private static final BindingContext transactTimeContext = new BindingContext(
			"transactTime", PrimitiveType.UINT64,
			null, null, null, Presence.REQUIRED
	);
	private static final BindingContext orderQtyContext = new BindingContext(
			"orderQty", null, null, null, null,
			Presence.REQUIRED
	);
	private static final BindingContext priceContext = new BindingContext(
			"price", null, null, null, null,
			Presence.OPTIONAL
	);
	private static final BindingContext stopPxContext = new BindingContext(
			"stopPx", null, null, null, null,
			Presence.OPTIONAL
	);
	private final SessionHeaderEncoder header = new SessionHeaderEncoder();
	private final NewOrderEncoder encoder = new NewOrderEncoder();
	private final UtcTimestampBinding utcTimestampBinding = new UtcTimestampBinding();
	private final QtyBinding qtyBinding = new QtyBinding();
	private final PriceBinding priceBinding = new PriceBinding();
	private final RootBlockStage rootBlock = new RootBlockStage();
	private final RootBlockBoundStage rootBlockBound = new RootBlockBoundStage();
	private final ExecInstWriter<RootBlockTransactTime> execInst = new ExecInstWriter<>();
	private final QtyEncodingWriter<RootBlock> orderQty = new QtyEncodingWriter<>();
	private final PriceEncodingWriter<RootBlock> price = new PriceEncodingWriter<>();
	private final PriceEncodingWriter<RootBlock> stopPx = new PriceEncodingWriter<>();
	private final PartiesStage parties = new PartiesStage();
	private final PartiesEntryStage partiesEntry = new PartiesEntryStage();
	private final PartiesEntryBoundStage partiesEntryBound = new PartiesEntryBoundStage();
	private final PartySubIdsStage partySubIds = new PartySubIdsStage();
	private final PartySubIdsEntryStage partySubIdsEntry = new PartySubIdsEntryStage();
	private final PartySubIdsEntryBoundStage partySubIdsEntryBound = new PartySubIdsEntryBoundStage();
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
		public RootBlockBoundStage bound() {
			at(At.ROOT_BLOCK, "RootBlock");
			return rootBlockBound;
		}

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
		public PartiesEntryBoundStage bound() {
			at(At.PARTIES_ENTRY, "PartiesEntry");
			return partiesEntryBound;
		}

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
		public PartySubIdsEntryBoundStage bound() {
			at(At.PARTY_SUB_IDS_ENTRY, "PartySubIdsEntry");
			return partySubIdsEntryBound;
		}

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

	// ---- the bound stages' objects, one per block, over the same encoders

	private final class RootBlockBoundStage
			implements
				RootBlockClOrdIdBound,
				RootBlockAccountBound,
				RootBlockSymbolBound,
				RootBlockSideBound,
				RootBlockOrdTypeBound,
				RootBlockTimeInForceBound,
				RootBlockExecInstBound,
				RootBlockTransactTimeBound,
				RootBlockOrderQtyBound,
				RootBlockBound {

		@Override
		public RootBlockAccountBound clOrdId(String value) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.clOrdId(ascii(required(value, "clOrdId"), NewOrderEncoder.clOrdIdLength(), "clOrdId"));
			return this;
		}

		@Override
		public RootBlockSymbolBound account(String value) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.account(ascii(required(value, "account"), NewOrderEncoder.accountLength(), "account"));
			return this;
		}

		@Override
		public RootBlockSideBound symbol(String value) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.symbol(ascii(required(value, "symbol"), NewOrderEncoder.symbolLength(), "symbol"));
			return this;
		}

		@Override
		public RootBlockOrdTypeBound side(com.example.trading.Side value) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.side(encodeSide(required(value, "side")));
			return this;
		}

		@Override
		public RootBlockTimeInForceBound ordType(com.example.trading.OrdType value) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.ordType(encodeOrdType(required(value, "ordType")));
			return this;
		}

		@Override
		public RootBlockExecInstBound timeInForce(com.example.trading.TimeInForce value) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.timeInForce(encodeTimeInForce(required(value, "timeInForce")));
			return this;
		}

		@Override
		public RootBlockTransactTimeBound execInst(Set<ExecInst> value) {
			at(At.ROOT_BLOCK, "RootBlock");
			encodeExecInst(required(value, "execInst"), encoder.execInst());
			return this;
		}

		@Override
		public RootBlockOrderQtyBound transactTime(Instant value) {
			at(At.ROOT_BLOCK, "RootBlock");
			encoder.transactTime(utcTimestampBinding.toWire(required(value, "transactTime"), transactTimeContext));
			return this;
		}

		@Override
		public RootBlockBound orderQty(long value) {
			at(At.ROOT_BLOCK, "RootBlock");
			writeQtyEncoding(qtyBinding.toWire(value, orderQtyContext), encoder.orderQty());
			return this;
		}

		@Override
		public RootBlockBound price(@Nullable BigDecimal value) {
			at(At.ROOT_BLOCK, "RootBlock");
			writePriceEncoding(priceBinding.toWire(value, priceContext), encoder.price());
			return this;
		}

		@Override
		public RootBlockBound stopPx(@Nullable BigDecimal value) {
			at(At.ROOT_BLOCK, "RootBlock");
			writePriceEncoding(priceBinding.toWire(value, stopPxContext), encoder.stopPx());
			return this;
		}

		@Override
		public Parties parties() {
			return rootBlock.parties();
		}

		@Override
		public RootBlockStage wire() {
			at(At.ROOT_BLOCK, "RootBlock");
			return rootBlock;
		}
	}

	private final class PartiesEntryBoundStage implements PartiesEntryPartyIdBound, PartiesEntryPartyRoleBound {

		@Override
		public PartiesEntryPartyRoleBound partyId(String value) {
			at(At.PARTIES_ENTRY, "PartiesEntry");
			partiesEncoder.partyId(ascii(required(value, "partyId"), PartiesEncoder.partyIdLength(), "partyId"));
			return this;
		}

		@Override
		public PartiesEntry partyRole(com.example.trading.PartyRole value) {
			at(At.PARTIES_ENTRY, "PartiesEntry");
			partiesEncoder.partyRole(encodePartyRole(required(value, "partyRole")));
			return partiesEntry;
		}

		@Override
		public PartiesEntryStage wire() {
			at(At.PARTIES_ENTRY, "PartiesEntry");
			return partiesEntry;
		}
	}

	private final class PartySubIdsEntryBoundStage
			implements
				PartySubIdsEntryPartySubIdBound,
				PartySubIdsEntryPartySubIdTypeBound {

		@Override
		public PartySubIdsEntryPartySubIdTypeBound partySubId(String value) {
			at(At.PARTY_SUB_IDS_ENTRY, "PartySubIdsEntry");
			partySubIdsEncoder.partySubId(
					ascii(required(value, "partySubId"), PartySubIdsEncoder.partySubIdLength(), "partySubId")
			);
			return this;
		}

		@Override
		public PartySubIds partySubIdType(short value) {
			return partySubIdsEntry.partySubIdType(value);
		}

		@Override
		public PartySubIdsEntryStage wire() {
			at(At.PARTY_SUB_IDS_ENTRY, "PartySubIdsEntry");
			return partySubIdsEntry;
		}
	}

	// ---- the leaf conversions, as the codec has them

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

	private static Side encodeSide(com.example.trading.Side value) {
		return switch (value) {
			case BUY -> Side.BUY;
			case SELL -> Side.SELL;
			case SELL_SHORT -> Side.SELL_SHORT;
		};
	}

	private static OrdType encodeOrdType(com.example.trading.OrdType value) {
		return switch (value) {
			case MARKET -> OrdType.MARKET;
			case LIMIT -> OrdType.LIMIT;
			case STOP -> OrdType.STOP;
			case STOP_LIMIT -> OrdType.STOP_LIMIT;
		};
	}

	private static TimeInForce encodeTimeInForce(com.example.trading.TimeInForce value) {
		return switch (value) {
			case DAY -> TimeInForce.DAY;
			case GOOD_TILL_CANCEL -> TimeInForce.GOOD_TILL_CANCEL;
			case IMMEDIATE_OR_CANCEL -> TimeInForce.IMMEDIATE_OR_CANCEL;
			case FILL_OR_KILL -> TimeInForce.FILL_OR_KILL;
		};
	}

	private static void encodeExecInst(Set<ExecInst> value, ExecInstEncoder wire) {
		wire.clear();
		wire.postOnly(value.contains(ExecInst.POST_ONLY));
		wire.reduceOnly(value.contains(ExecInst.REDUCE_ONLY));
		wire.allOrNone(value.contains(ExecInst.ALL_OR_NONE));
	}

	private static PartyRole encodePartyRole(com.example.trading.PartyRole value) {
		return switch (value) {
			case EXECUTING_FIRM -> PartyRole.EXECUTING_FIRM;
			case CLIENT_ID -> PartyRole.CLIENT_ID;
			case ENTERING_TRADER -> PartyRole.ENTERING_TRADER;
		};
	}

	private static void writeQtyEncoding(QtyEncoding value, QtyEncodingEncoder encoder) {
		encoder.mantissa(value.mantissa());
		if (value.exponent() != encoder.exponent()) {
			throw new IllegalArgumentException("exponent is the constant " + encoder.exponent());
		}
	}

	private static void writePriceEncoding(PriceEncoding value, PriceEncodingEncoder encoder) {
		encoder.mantissa(value.mantissa() == null ? PriceEncodingEncoder.mantissaNullValue() : value.mantissa());
		if (value.exponent() != encoder.exponent()) {
			throw new IllegalArgumentException("exponent is the constant " + encoder.exponent());
		}
	}
}
