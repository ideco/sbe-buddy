package com.example.quotes;

import static net.concini.sbebuddy.PrimitiveType.UINT8;

import net.concini.sbebuddy.SbeChoice;
import net.concini.sbebuddy.SbeSet;

/**
 * What a quote says about itself; a field of this set is a
 * {@code Set<QuoteFlag>}. The wire names are given, so the flyweights' choice
 * methods read as {@code indicative()} while the constants keep Java's style.
 */
@SbeSet(primitiveType = UINT8, description = "What a quote says about itself")
public enum QuoteFlag {

	@SbeChoice(value = 0, name = "indicative")
	INDICATIVE,

	@SbeChoice(value = 1, name = "crossed")
	CROSSED,

	@SbeChoice(value = 2, name = "locked")
	LOCKED
}
