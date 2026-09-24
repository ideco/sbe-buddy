package corpus.replacement;

import net.concini.sbebuddy.SbeUnion;

/**
 * Both quotes, so a reader takes the old template and its replacement alike.
 */
@SbeUnion
public sealed interface QuoteUpdate permits Quote, QuoteV2 {

	long instrumentId();
}
