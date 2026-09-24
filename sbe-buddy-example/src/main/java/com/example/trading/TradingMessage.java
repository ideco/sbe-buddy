package com.example.trading;

import net.concini.sbebuddy.SbeUnion;

/**
 * Every message of the session, either direction: what a journal or a gateway
 * carries. A caller's {@code switch} may take {@link OrderEntry} and
 * {@link OrderEvent} as one case each.
 */
@SbeUnion
public sealed interface TradingMessage permits OrderEntry, OrderEvent {
}
