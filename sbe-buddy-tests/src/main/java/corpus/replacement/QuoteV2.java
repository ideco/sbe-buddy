package corpus.replacement;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

/** The quote's replacement: a new template over the new composite. */
@SbeMessage(id = 2, sinceVersion = 1)
public record QuoteV2(
		@SbeField(id = 1) long instrumentId,
		@SbeField(id = 2) Level bid
) implements QuoteUpdate {
}
