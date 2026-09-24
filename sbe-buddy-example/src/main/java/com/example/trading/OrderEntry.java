package com.example.trading;

import net.concini.sbebuddy.SbeUnion;

/** What a client sends: orders, their replacements and their cancels. */
@SbeUnion
public sealed interface OrderEntry extends TradingMessage permits NewOrder, ReplaceOrder, CancelOrder {

	/** The id the client gives the order, or its replacement or cancel. */
	String clOrdId();
}
