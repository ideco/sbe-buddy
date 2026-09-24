package com.example.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import com.example.trading.ExecType;
import com.example.trading.OrdStatus;
import com.example.trading.OrdType;
import com.example.trading.PartyRole;
import com.example.trading.SecurityIdSource;
import com.example.trading.Side;
import com.example.trading.TimeInForce;
import com.example.trading.xmlref.NewOrderDecoder;
import com.example.trading.xmlref.PriceEncodingDecoder;
import com.example.trading.xmlref.SessionHeaderDecoder;

/**
 * The client's codecs against the venue's: what the client writes, sbe-tool's
 * flyweights from the venue's schema read, the members the client leaves out
 * holding their null values and no entries; what the venue's codecs write, the
 * client reads by the fields it maps.
 */
final class ClientTest {

	private static final int OFFSET = 16;

	private static final String ORDER = "ORD-0000000000000001";

	private static final NewOrder CLIENT_ORDER = new NewOrder(ORDER, "ACME", Side.BUY, 700, new BigDecimal("99.6100"));

	@Test
	void theVenueReadsWhatTheClientWrites() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
		int length = new NewOrderCodec().encode(CLIENT_ORDER, buffer, OFFSET);

		NewOrderDecoder decoder = new NewOrderDecoder().wrapAndApplyHeader(buffer, OFFSET, new SessionHeaderDecoder());
		assertThat(decoder.clOrdId()).isEqualTo(ORDER);
		assertThat(decoder.symbol()).isEqualTo("ACME");
		assertThat(decoder.side()).isEqualTo(com.example.trading.xmlref.Side.BUY);
		assertThat(decoder.orderQty().mantissa()).isEqualTo(700);
		assertThat(decoder.price().mantissa()).isEqualTo(996_100L);
		assertThat(decoder.account()).isEmpty();
		assertThat(decoder.ordType()).isEqualTo(com.example.trading.xmlref.OrdType.NULL_VAL);
		assertThat(decoder.execInst().postOnly()).isFalse();
		assertThat(decoder.stopPx().mantissa()).isEqualTo(PriceEncodingDecoder.mantissaNullValue());
		assertThat(decoder.parties().count()).isZero();
		assertThat(length).isEqualTo(SessionHeaderDecoder.ENCODED_LENGTH + decoder.sbeDecodedLength());
	}

	@Test
	void theClientReadsWhatTheVenueWrites() {
		Instant time = Instant.parse("2026-09-24T08:30:00.123456789Z");
		com.example.trading.NewOrder venueOrder = new com.example.trading.NewOrder(
				ORDER, "ACCT-0001", "ACME", Side.BUY, OrdType.LIMIT, TimeInForce.DAY,
				EnumSet.of(com.example.trading.ExecInst.POST_ONLY), SecurityIdSource.EXCHANGE_SYMBOL, time, 700,
				new BigDecimal("99.6100"), null,
				List.of(new com.example.trading.NewOrder.Party("FIRM-A", PartyRole.EXECUTING_FIRM, List.of()))
		);
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[256]);
		int length = new com.example.trading.NewOrderCodec().encode(venueOrder, buffer, OFFSET);
		NewOrderCodec codec = new NewOrderCodec();

		assertThat(codec.decode(buffer, OFFSET)).isEqualTo(CLIENT_ORDER);
		assertThat(codec.lastDecodedLength()).isEqualTo(length);

		com.example.trading.ExecutionReport fill = new com.example.trading.ExecutionReport(
				"VENUE-0000000042", ORDER, "EXEC-00000000007", ExecType.TRADE, OrdStatus.PARTIALLY_FILLED, "ACME",
				Side.BUY, 500, 200, 50, new BigDecimal("99.6100"), true, LocalDate.of(2026, 9, 24),
				YearMonth.of(2026, 12), time,
				Map.of(
						"EXEC-00000000005",
						new com.example.trading.ExecutionReport.Fill("EXEC-00000000005", new BigDecimal("99.6000"), 200)
				)
		);
		new com.example.trading.ExecutionReportCodec().encode(fill, buffer, OFFSET);

		assertThat(new ExecutionReportCodec().decode(buffer, OFFSET)).isEqualTo(
				new ExecutionReport(
						"VENUE-0000000042", ORDER, ExecType.TRADE, OrdStatus.PARTIALLY_FILLED, 200,
						new BigDecimal("99.6100")
				)
		);
	}
}
