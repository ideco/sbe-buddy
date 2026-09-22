package corpus.constants;

import static net.concini.sbebuddy.Presence.CONSTANT;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

@SbeMessage(id = 1)
record Constants(
		@SbeField(id = 1, type = Currency.class) String currency,
		@SbeField(id = 2, type = BuySide.class) byte buySide,
		@SbeField(id = 3, presence = CONSTANT, valueRef = "Side.Sell") Side side
) {
}
