package corpus.replacement.v0;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

@SbeMessage(id = 1)
public record Quote(
		@SbeField(id = 1) long instrumentId,
		@SbeField(id = 2) Price bid
) implements QuoteUpdate {
}
