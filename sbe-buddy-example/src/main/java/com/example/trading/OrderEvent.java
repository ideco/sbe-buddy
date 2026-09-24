package com.example.trading;

import net.concini.sbebuddy.SbeUnion;

/** What the venue sends back: reports on orders and refusals. */
@SbeUnion
public sealed interface OrderEvent extends TradingMessage permits ExecutionReport, CancelReject, Reject {
}
