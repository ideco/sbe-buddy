package corpus.namedtypes;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

@SbeMessage(id = 1)
record NamedTypes(
		@SbeField(id = 1, type = Symbol.class) String symbol,
		@SbeField(id = 2, type = Price.class) long price,
		@SbeField(id = 3, type = Quantity.class) @Nullable Long quantity
) {
}
