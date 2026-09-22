package corpus.composites;

import java.math.BigDecimal;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

@SbeMessage(id = 1)
record Composites(
		@SbeField(id = 1) Quote quote,
		@SbeField(id = 2, type = Decimal.class, binding = DecimalBinding.class) BigDecimal last
) {
}
