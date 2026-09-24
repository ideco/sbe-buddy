package com.example.trading;

import static net.concini.sbebuddy.PrimitiveType.UINT8;

import net.concini.sbebuddy.SbeChoice;
import net.concini.sbebuddy.SbeSet;

/**
 * How an order is to be executed, any of them at once. The wire names are the
 * flyweights' method names, {@code postOnly()} rather than {@code pOST_ONLY()}.
 */
@SbeSet(primitiveType = UINT8)
public enum ExecInst {

	@SbeChoice(value = 0, name = "postOnly")
	POST_ONLY,

	@SbeChoice(value = 1, name = "reduceOnly")
	REDUCE_ONLY,

	@SbeChoice(value = 2, name = "allOrNone")
	ALL_OR_NONE
}
