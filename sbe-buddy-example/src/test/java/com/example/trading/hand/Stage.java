package com.example.trading.hand;

/**
 * One stage of a message a reader hands out: the root block, a group's header,
 * one of its entries or a var-data. Stands in for the api's {@code Stage} until
 * increment 26's step 2 adds it.
 */
public interface Stage {

	/**
	 * Prunes what this stage contains, so its stages never come: on the root block
	 * the rest of the message, on a group header its entries, on an entry its
	 * nested groups and var-data, on a var-data nothing. Only the current stage can
	 * skip; on any other it is an {@link IllegalStateException}.
	 */
	void skip();
}
