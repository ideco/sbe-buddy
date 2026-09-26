package com.example.trading.hand;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

import org.agrona.DirectBuffer;
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
import com.example.trading.sbe.ExecInstDecoder;
import com.example.trading.sbe.NewOrderDecoder;
import com.example.trading.sbe.NewOrderDecoder.PartiesDecoder;
import com.example.trading.sbe.NewOrderDecoder.PartiesDecoder.PartySubIdsDecoder;
import com.example.trading.sbe.OrdType;
import com.example.trading.sbe.PartyRole;
import com.example.trading.sbe.PriceEncodingDecoder;
import com.example.trading.sbe.QtyEncodingDecoder;
import com.example.trading.sbe.SecurityIdSource;
import com.example.trading.sbe.SessionHeaderDecoder;
import com.example.trading.sbe.Side;
import com.example.trading.sbe.TimeInForce;

/**
 * A {@code NewOrder} as the sequence of its stages, over sbe-tool's decoder:
 * the root block, then {@code parties}, each entry followed by its
 * {@code partySubIds} and their entries. Hand-written as the shape the
 * generator will emit for every message; the stages are what
 * {@code flyweights.md} settles, one line per delegated field. Each stage of a
 * block the record maps has a {@code bound()} view: the record's components
 * through their bindings, applied on each call, as the codec applies them. The
 * record's enums share their names with sbe-tool's, imported here for the wire
 * stages, so the bound stages qualify them.
 */
public final class NewOrderReader implements Iterable<NewOrderReader.Stage>, Iterator<NewOrderReader.Stage> {

	/** The stages of a {@code NewOrder}, in the order they come. */
	public sealed interface Stage extends com.example.trading.hand.Stage
			permits RootBlock, Parties, PartiesEntry, PartySubIds, PartySubIdsEntry {
	}

	/**
	 * Where the reader is, in wire order. A stage answers while the reader is
	 * inside it, which is a contiguous range of these; {@code AFTER_PARTY_SUB_IDS}
	 * is inside a parties entry once its sub ids were passed.
	 */
	private enum At {
		BEFORE_ROOT_BLOCK, ROOT_BLOCK, PARTIES, PARTIES_ENTRY, PARTY_SUB_IDS, PARTY_SUB_IDS_ENTRY, AFTER_PARTY_SUB_IDS, END
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
	private final SessionHeaderDecoder header = new SessionHeaderDecoder();
	private final NewOrderDecoder decoder = new NewOrderDecoder();
	private final NewOrderDecoder measure = new NewOrderDecoder(); // decodedLength() mid-message, disturbing nothing
	private final UtcTimestampBinding utcTimestampBinding = new UtcTimestampBinding();
	private final QtyBinding qtyBinding = new QtyBinding();
	private final PriceBinding priceBinding = new PriceBinding();
	private final RootBlock rootBlock = new RootBlock();
	private final RootBlockBound rootBlockBound = new RootBlockBound();
	private final Parties parties = new Parties();
	private final PartiesEntry partiesEntry = new PartiesEntry();
	private final PartiesEntryBound partiesEntryBound = new PartiesEntryBound();
	private final PartySubIds partySubIds = new PartySubIds();
	private final PartySubIdsEntry partySubIdsEntry = new PartySubIdsEntry();
	private final PartySubIdsEntryBound partySubIdsEntryBound = new PartySubIdsEntryBound();
	private @Nullable DirectBuffer buffer;
	private @Nullable PartiesDecoder partiesDecoder; // decoder.parties(), from the moment the group opens
	private @Nullable PartySubIdsDecoder partySubIdsDecoder;
	private int partiesIndex;
	private int partySubIdsIndex;
	private At at = At.BEFORE_ROOT_BLOCK;

	/** Reads the header at the offset and stands before the root block. */
	public NewOrderReader wrap(DirectBuffer buffer, int offset) {
		this.buffer = buffer;
		decoder.wrapAndApplyHeader(buffer, offset, header);
		at = At.BEFORE_ROOT_BLOCK;
		return this;
	}

	/** Back before the root block of the message wrapped. */
	public NewOrderReader rewind() {
		decoder.sbeRewind();
		at = At.BEFORE_ROOT_BLOCK;
		return this;
	}

	/** The header as read, for its own members. */
	public SessionHeaderDecoder header() {
		return header;
	}

	public int actingVersion() {
		return decoder.actingVersion();
	}

	/**
	 * The bytes of the whole message, header included, at any point. Once no stage
	 * is left the limit is at the end; before that a second decoder walks the
	 * message, so the group decoders in use are not disturbed.
	 */
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

	/** Whether a stage follows, without reading anything. */
	@Override
	public boolean hasNext() {
		return switch (at) {
			case BEFORE_ROOT_BLOCK, PARTIES_ENTRY -> true;
			case ROOT_BLOCK -> partiesPresent();
			case PARTIES, AFTER_PARTY_SUB_IDS -> partiesDecoder.hasNext();
			case PARTY_SUB_IDS, PARTY_SUB_IDS_ENTRY -> partySubIdsDecoder.hasNext() || partiesDecoder.hasNext();
			case END -> false;
		};
	}

	/** The next stage, whatever the current one left unread passed over. */
	@Override
	public Stage next() {
		return switch (at) {
			case BEFORE_ROOT_BLOCK -> {
				at = At.ROOT_BLOCK;
				yield rootBlock;
			}
			case ROOT_BLOCK -> partiesPresent() ? openParties() : end();
			case PARTIES -> partiesDecoder.hasNext() ? nextPartiesEntry() : end();
			case PARTIES_ENTRY -> openPartySubIds();
			case PARTY_SUB_IDS, PARTY_SUB_IDS_ENTRY -> partySubIdsDecoder.hasNext()
					? nextPartySubIdsEntry()
					: afterPartySubIds();
			case AFTER_PARTY_SUB_IDS -> afterPartySubIds();
			case END -> throw new NoSuchElementException();
		};
	}

	private boolean partiesPresent() {
		return decoder.actingVersion() >= NewOrderDecoder.partiesDecoderSinceVersion();
	}

	private Stage openParties() {
		partiesDecoder = decoder.parties();
		partiesIndex = -1;
		at = At.PARTIES;
		return parties;
	}

	private Stage nextPartiesEntry() {
		partiesDecoder.next();
		partiesIndex++;
		at = At.PARTIES_ENTRY;
		return partiesEntry;
	}

	private Stage openPartySubIds() {
		partySubIdsDecoder = partiesDecoder.partySubIds();
		partySubIdsIndex = -1;
		at = At.PARTY_SUB_IDS;
		return partySubIds;
	}

	private Stage nextPartySubIdsEntry() {
		partySubIdsDecoder.next();
		partySubIdsIndex++;
		at = At.PARTY_SUB_IDS_ENTRY;
		return partySubIdsEntry;
	}

	/**
	 * Past a parties entry's sub ids: the next entry, or the end of the message.
	 */
	private Stage afterPartySubIds() {
		return partiesDecoder.hasNext() ? nextPartiesEntry() : end();
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

	/**
	 * The root block: sbe-tool's fixed fields, delegated one to one; answers for
	 * the whole message.
	 */
	public final class RootBlock implements Stage {

		private RootBlock() {
		}

		private void open() {
			NewOrderReader.this.open(At.ROOT_BLOCK, At.END, "RootBlock");
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

		public String account() {
			open();
			return decoder.account();
		}

		public byte account(int index) {
			open();
			return decoder.account(index);
		}

		public int getAccount(byte[] dst, int dstOffset) {
			open();
			return decoder.getAccount(dst, dstOffset);
		}

		public int getAccount(Appendable value) {
			open();
			return decoder.getAccount(value);
		}

		public String symbol() {
			open();
			return decoder.symbol();
		}

		public byte symbol(int index) {
			open();
			return decoder.symbol(index);
		}

		public int getSymbol(byte[] dst, int dstOffset) {
			open();
			return decoder.getSymbol(dst, dstOffset);
		}

		public int getSymbol(Appendable value) {
			open();
			return decoder.getSymbol(value);
		}

		public Side side() {
			open();
			return decoder.side();
		}

		public byte sideRaw() {
			open();
			return decoder.sideRaw();
		}

		public OrdType ordType() {
			open();
			return decoder.ordType();
		}

		public byte ordTypeRaw() {
			open();
			return decoder.ordTypeRaw();
		}

		public TimeInForce timeInForce() {
			open();
			return decoder.timeInForce();
		}

		public byte timeInForceRaw() {
			open();
			return decoder.timeInForceRaw();
		}

		public ExecInstDecoder execInst() {
			open();
			return decoder.execInst();
		}

		public SecurityIdSource securityIdSource() {
			open();
			return decoder.securityIdSource();
		}

		public byte securityIdSourceRaw() {
			open();
			return decoder.securityIdSourceRaw();
		}

		public long transactTime() {
			open();
			return decoder.transactTime();
		}

		public QtyEncodingDecoder orderQty() {
			open();
			return decoder.orderQty();
		}

		public PriceEncodingDecoder price() {
			open();
			return decoder.price();
		}

		public PriceEncodingDecoder stopPx() {
			open();
			return decoder.stopPx();
		}

		/** The record's view of the block. */
		public RootBlockBound bound() {
			open();
			return rootBlockBound;
		}

		/** The rest of the message: iteration ends. */
		@Override
		public void skip() {
			current(At.ROOT_BLOCK, "RootBlock");
			decoder.sbeSkip();
			at = At.END;
		}
	}

	/** The {@code parties} header, around sbe-tool's group decoder. */
	public final class Parties implements Stage {

		private Parties() {
		}

		private void open() {
			NewOrderReader.this.open(At.PARTIES, At.AFTER_PARTY_SUB_IDS, "Parties");
		}

		public int count() {
			open();
			return partiesDecoder.count();
		}

		/** Every entry, with all it holds: no {@code PartiesEntry} comes. */
		@Override
		public void skip() {
			current(At.PARTIES, "Parties");
			while (partiesDecoder.hasNext()) {
				partiesDecoder.next();
				partiesDecoder.sbeSkip();
			}
			at = At.END;
		}
	}

	/**
	 * One entry of {@code parties}: the same group decoder, after its
	 * {@code next()}.
	 */
	public final class PartiesEntry implements Stage {

		private PartiesEntry() {
		}

		private void open() {
			NewOrderReader.this.open(At.PARTIES_ENTRY, At.AFTER_PARTY_SUB_IDS, "PartiesEntry");
		}

		public int index() {
			open();
			return partiesIndex;
		}

		public String partyId() {
			open();
			return partiesDecoder.partyId();
		}

		public byte partyId(int index) {
			open();
			return partiesDecoder.partyId(index);
		}

		public int getPartyId(byte[] dst, int dstOffset) {
			open();
			return partiesDecoder.getPartyId(dst, dstOffset);
		}

		public int getPartyId(Appendable value) {
			open();
			return partiesDecoder.getPartyId(value);
		}

		public PartyRole partyRole() {
			open();
			return partiesDecoder.partyRole();
		}

		public short partyRoleRaw() {
			open();
			return partiesDecoder.partyRoleRaw();
		}

		public PartiesEntryBound bound() {
			open();
			return partiesEntryBound;
		}

		/** The entry's sub ids: no {@code PartySubIds} comes for it. */
		@Override
		public void skip() {
			current(At.PARTIES_ENTRY, "PartiesEntry");
			partiesDecoder.sbeSkip();
			at = At.AFTER_PARTY_SUB_IDS;
		}
	}

	/** The {@code partySubIds} header of the current parties entry. */
	public final class PartySubIds implements Stage {

		private PartySubIds() {
		}

		private void open() {
			NewOrderReader.this.open(At.PARTY_SUB_IDS, At.PARTY_SUB_IDS_ENTRY, "PartySubIds");
		}

		public int count() {
			open();
			return partySubIdsDecoder.count();
		}

		@Override
		public void skip() {
			current(At.PARTY_SUB_IDS, "PartySubIds");
			while (partySubIdsDecoder.hasNext()) {
				partySubIdsDecoder.next();
				partySubIdsDecoder.sbeSkip();
			}
			at = At.AFTER_PARTY_SUB_IDS;
		}
	}

	/** One entry of {@code partySubIds}. */
	public final class PartySubIdsEntry implements Stage {

		private PartySubIdsEntry() {
		}

		private void open() {
			NewOrderReader.this.open(At.PARTY_SUB_IDS_ENTRY, At.PARTY_SUB_IDS_ENTRY, "PartySubIdsEntry");
		}

		public int index() {
			open();
			return partySubIdsIndex;
		}

		public String partySubId() {
			open();
			return partySubIdsDecoder.partySubId();
		}

		public byte partySubId(int index) {
			open();
			return partySubIdsDecoder.partySubId(index);
		}

		public int getPartySubId(byte[] dst, int dstOffset) {
			open();
			return partySubIdsDecoder.getPartySubId(dst, dstOffset);
		}

		public int getPartySubId(Appendable value) {
			open();
			return partySubIdsDecoder.getPartySubId(value);
		}

		public short partySubIdType() {
			open();
			return partySubIdsDecoder.partySubIdType();
		}

		public PartySubIdsEntryBound bound() {
			open();
			return partySubIdsEntryBound;
		}

		/** Nothing nested: nothing to prune. */
		@Override
		public void skip() {
			current(At.PARTY_SUB_IDS_ENTRY, "PartySubIdsEntry");
			partySubIdsDecoder.sbeSkip();
		}
	}

	// ---- the bound stages: the record's components, bindings run on each call,
	// never cached

	/**
	 * {@code NewOrder}'s components of the root block, absent ones {@code null}.
	 */
	public final class RootBlockBound {

		private RootBlockBound() {
		}

		public String clOrdId() {
			rootBlock.open();
			return decoder.clOrdId();
		}

		public String account() {
			rootBlock.open();
			return decoder.account();
		}

		public String symbol() {
			rootBlock.open();
			return decoder.symbol();
		}

		public com.example.trading.Side side() {
			rootBlock.open();
			return decodeSide(decoder.sideRaw());
		}

		public com.example.trading.OrdType ordType() {
			rootBlock.open();
			return decodeOrdType(decoder.ordTypeRaw());
		}

		public com.example.trading.TimeInForce timeInForce() {
			rootBlock.open();
			return decodeTimeInForce(decoder.timeInForceRaw());
		}

		/** A set the record holds; built on each call. */
		public Set<ExecInst> execInst() {
			rootBlock.open();
			return decodeExecInst(decoder.execInst());
		}

		public com.example.trading.SecurityIdSource securityIdSource() {
			rootBlock.open();
			return decodeSecurityIdSource(decoder.securityIdSourceRaw());
		}

		public Instant transactTime() {
			rootBlock.open();
			return utcTimestampBinding.fromWire(decoder.transactTime(), transactTimeContext);
		}

		/** The binding's, over the face record the reader builds for it. */
		public long orderQty() {
			rootBlock.open();
			return qtyBinding.fromWire(readQtyEncoding(decoder.orderQty()), orderQtyContext);
		}

		/**
		 * The binding decides absence from the face, as on an optional composite it
		 * must.
		 */
		public @Nullable BigDecimal price() {
			rootBlock.open();
			return priceBinding.fromWire(readPriceEncoding(decoder.price()), priceContext);
		}

		public @Nullable BigDecimal stopPx() {
			rootBlock.open();
			return priceBinding.fromWire(readPriceEncoding(decoder.stopPx()), stopPxContext);
		}

		public RootBlock wire() {
			rootBlock.open();
			return rootBlock;
		}
	}

	/** {@code NewOrder.Party}'s components of the entry's block. */
	public final class PartiesEntryBound {

		private PartiesEntryBound() {
		}

		public int index() {
			partiesEntry.open();
			return partiesIndex;
		}

		public String partyId() {
			partiesEntry.open();
			return partiesDecoder.partyId();
		}

		public com.example.trading.PartyRole partyRole() {
			partiesEntry.open();
			return decodePartyRole(partiesDecoder.partyRoleRaw());
		}

		public PartiesEntry wire() {
			partiesEntry.open();
			return partiesEntry;
		}
	}

	/** {@code NewOrder.PartySubId}'s components. */
	public final class PartySubIdsEntryBound {

		private PartySubIdsEntryBound() {
		}

		public int index() {
			partySubIdsEntry.open();
			return partySubIdsIndex;
		}

		public String partySubId() {
			partySubIdsEntry.open();
			return partySubIdsDecoder.partySubId();
		}

		public short partySubIdType() {
			partySubIdsEntry.open();
			return partySubIdsDecoder.partySubIdType();
		}

		public PartySubIdsEntry wire() {
			partySubIdsEntry.open();
			return partySubIdsEntry;
		}
	}

	// ---- the leaf conversions, as the codec has them

	private static com.example.trading.Side decodeSide(byte raw) {
		return switch (raw) {
			case (byte) 49 -> com.example.trading.Side.BUY;
			case (byte) 50 -> com.example.trading.Side.SELL;
			case (byte) 53 -> com.example.trading.Side.SELL_SHORT;
			default -> throw new IllegalArgumentException("Side has no value " + raw);
		};
	}

	private static com.example.trading.OrdType decodeOrdType(byte raw) {
		return switch (raw) {
			case (byte) 49 -> com.example.trading.OrdType.MARKET;
			case (byte) 50 -> com.example.trading.OrdType.LIMIT;
			case (byte) 51 -> com.example.trading.OrdType.STOP;
			case (byte) 52 -> com.example.trading.OrdType.STOP_LIMIT;
			default -> throw new IllegalArgumentException("OrdType has no value " + raw);
		};
	}

	private static com.example.trading.TimeInForce decodeTimeInForce(byte raw) {
		return switch (raw) {
			case (byte) 48 -> com.example.trading.TimeInForce.DAY;
			case (byte) 49 -> com.example.trading.TimeInForce.GOOD_TILL_CANCEL;
			case (byte) 51 -> com.example.trading.TimeInForce.IMMEDIATE_OR_CANCEL;
			case (byte) 52 -> com.example.trading.TimeInForce.FILL_OR_KILL;
			default -> throw new IllegalArgumentException("TimeInForce has no value " + raw);
		};
	}

	private static Set<ExecInst> decodeExecInst(ExecInstDecoder wire) {
		if ((wire.getRaw() & ~(1L << 0 | 1L << 1 | 1L << 2)) != 0) {
			throw new IllegalArgumentException("ExecInst has a bit no choice names: " + wire.getRaw());
		}
		Set<ExecInst> value = EnumSet.noneOf(ExecInst.class);
		if (wire.postOnly()) {
			value.add(ExecInst.POST_ONLY);
		}
		if (wire.reduceOnly()) {
			value.add(ExecInst.REDUCE_ONLY);
		}
		if (wire.allOrNone()) {
			value.add(ExecInst.ALL_OR_NONE);
		}
		return value;
	}

	private static com.example.trading.SecurityIdSource decodeSecurityIdSource(byte raw) {
		return switch (raw) {
			case (byte) 52 -> com.example.trading.SecurityIdSource.ISIN;
			case (byte) 56 -> com.example.trading.SecurityIdSource.EXCHANGE_SYMBOL;
			default -> throw new IllegalArgumentException("SecurityIdSource has no value " + raw);
		};
	}

	private static com.example.trading.PartyRole decodePartyRole(short raw) {
		return switch (raw) {
			case (short) 1 -> com.example.trading.PartyRole.EXECUTING_FIRM;
			case (short) 3 -> com.example.trading.PartyRole.CLIENT_ID;
			case (short) 11 -> com.example.trading.PartyRole.ENTERING_TRADER;
			default -> throw new IllegalArgumentException("PartyRole has no value " + raw);
		};
	}

	private static QtyEncoding readQtyEncoding(QtyEncodingDecoder decoder) {
		return new QtyEncoding(decoder.mantissa(), decoder.exponent());
	}

	private static PriceEncoding readPriceEncoding(PriceEncodingDecoder decoder) {
		return new PriceEncoding(
				decoder.mantissa() == PriceEncodingDecoder.mantissaNullValue() ? null : decoder.mantissa(),
				decoder.exponent()
		);
	}
}
