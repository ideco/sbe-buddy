package net.concini.sbebuddy;

/**
 * One stage of a message as a generated reader hands it out: the root block, a
 * group's header, one of its entries, or a var-data. Each message's reader has
 * a sealed {@code Stage} of its own extending this one, so a read is one loop
 * and one {@code switch}. Only generated code implements it.
 */
public interface Stage {

	/**
	 * Prunes what this stage contains, so its stages never come: on the root block
	 * the rest of the message, on a group's header its entries, on an entry its
	 * nested groups and var-data, on a var-data nothing. What a stage leaves unread
	 * is passed over anyway when the reader moves on; skipping is for what should
	 * not come at all. Only the current stage can skip; on any other it is an
	 * {@link IllegalStateException}.
	 */
	void skip();
}
