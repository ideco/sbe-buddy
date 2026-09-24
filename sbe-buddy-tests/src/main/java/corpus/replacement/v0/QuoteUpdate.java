package corpus.replacement.v0;

import net.concini.sbebuddy.SbeUnion;

/**
 * A union of one message, so a reader is written against the union from the
 * start.
 */
@SbeUnion
public sealed interface QuoteUpdate permits Quote {

	long instrumentId();
}
