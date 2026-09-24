package com.example.trading;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.trading.ExecutionReport.Fill;
import com.example.trading.NewOrder.Party;
import com.example.trading.NewOrder.PartySubId;

/**
 * One of each message, and the edge values: a market order without prices and
 * parties, a report without a trade, ids at their full length.
 */
final class Samples {

	static final Instant TIME = Instant.parse("2026-09-24T08:30:00.123456789Z");

	/** Twenty characters, the whole of a {@code ClOrdId}. */
	static final String ORDER = "ORD-0000000000000001";

	static final String REPLACEMENT = "ORD-0000000000000002";

	static final String CANCEL = "ORD-0000000000000003";

	static final NewOrder LIMIT_ORDER = new NewOrder(
			ORDER, "ACCT-0001", "ACME", Side.BUY, OrdType.LIMIT, TimeInForce.DAY, EnumSet.of(ExecInst.POST_ONLY),
			SecurityIdSource.EXCHANGE_SYMBOL, TIME, 700, new BigDecimal("99.6100"), null,
			List.of(
					new Party("FIRM-A", PartyRole.EXECUTING_FIRM, List.of(new PartySubId("DESK-7", (short) 4))),
					new Party("TRADER-12", PartyRole.ENTERING_TRADER, List.of())
			)
	);

	static final NewOrder MARKET_ORDER = new NewOrder(
			ORDER, "ACCT-0001", "ACME", Side.SELL, OrdType.MARKET, TimeInForce.IMMEDIATE_OR_CANCEL,
			EnumSet.noneOf(ExecInst.class), SecurityIdSource.EXCHANGE_SYMBOL, TIME, 300, null, null, List.of()
	);

	static final ReplaceOrder REPLACE = new ReplaceOrder(
			ORDER, REPLACEMENT, "ACME", Side.BUY, OrdType.LIMIT, 800, new BigDecimal("99.6200"), TIME
	);

	static final CancelOrder CANCEL_ORDER = new CancelOrder(REPLACEMENT, CANCEL, "ACME", Side.BUY, TIME);

	static final ExecutionReport FILL = new ExecutionReport(
			"VENUE-0000000042", ORDER, "EXEC-00000000007", ExecType.TRADE, OrdStatus.PARTIALLY_FILLED, "ACME",
			Side.BUY, 500, 200, 50, new BigDecimal("99.6100"), true, LocalDate.of(2026, 9, 24), YearMonth.of(2026, 12),
			TIME, fills(
					new Fill("EXEC-00000000005", new BigDecimal("99.6000"), 150),
					new Fill("EXEC-00000000006", new BigDecimal("99.6100"), 50)
			)
	);

	static final ExecutionReport ACKNOWLEDGEMENT = new ExecutionReport(
			"VENUE-0000000042", ORDER, "EXEC-00000000001", ExecType.NEW, OrdStatus.NEW, "ACME", Side.BUY, 700, 0, 0,
			null, null, LocalDate.of(2026, 9, 24), YearMonth.of(2026, 12), TIME, fills()
	);

	static final CancelReject CANCEL_REJECT = new CancelReject(
			"VENUE-0000000042", CANCEL, REPLACEMENT, OrdStatus.FILLED, CxlRejReason.TOO_LATE, "Ordre déjà exécuté"
	);

	static final Reject REJECT = new Reject(
			17, BusinessRejectReason.NOT_AUTHORIZED, "Not authorised to trade ACME — ask your desk"
	);

	static final List<OrderEntry> ENTRIES = List.of(LIMIT_ORDER, MARKET_ORDER, REPLACE, CANCEL_ORDER);

	static final List<OrderEvent> EVENTS = List.of(FILL, ACKNOWLEDGEMENT, CANCEL_REJECT, REJECT);

	private Samples() {
	}

	static Map<String, Fill> fills(Fill... fills) {
		Map<String, Fill> map = new LinkedHashMap<>();
		for (Fill fill : fills) {
			map.put(fill.fillExecId(), fill);
		}
		return map;
	}
}
