package corpus.optionalfaces;

import static net.concini.sbebuddy.Presence.OPTIONAL;

import java.math.BigDecimal;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

@SbeMessage(id = 1)
record OptionalFaces(
		@SbeField(id = 1, type = Money.class, presence = OPTIONAL, binding = MoneyBinding.class) @Nullable BigDecimal price,
		@SbeField(id = 2, type = Instruction.class, presence = OPTIONAL, binding = InstructionsBinding.class) @Nullable Set<Instruction> instructions,
		@SbeField(id = 3, type = Venue.class, presence = OPTIONAL, binding = MicBinding.class) @Nullable Mic venue,
		@SbeField(id = 4, type = Money.class, presence = OPTIONAL) Money fee,
		@SbeField(id = 5, type = Instruction.class, presence = OPTIONAL) Set<Instruction> flags,
		@SbeField(id = 6, type = Venue.class, presence = OPTIONAL) String market
) {
}
