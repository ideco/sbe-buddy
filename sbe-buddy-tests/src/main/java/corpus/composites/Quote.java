package corpus.composites;

import static net.concini.sbebuddy.Presence.CONSTANT;
import static net.concini.sbebuddy.Presence.OPTIONAL;
import static net.concini.sbebuddy.PrimitiveType.CHAR;
import static net.concini.sbebuddy.PrimitiveType.UINT64;
import static net.concini.sbebuddy.PrimitiveType.UINT8;

import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.SbeChoice;
import net.concini.sbebuddy.SbeComposite;
import net.concini.sbebuddy.SbeEnum;
import net.concini.sbebuddy.SbeEnumValue;
import net.concini.sbebuddy.SbeRef;
import net.concini.sbebuddy.SbeSet;
import net.concini.sbebuddy.SbeType;

@SbeComposite
record Quote(
		@SbeRef Decimal bid,
		@SbeRef(offset = 9) Decimal ask,
		Side side,
		Set<Flags> flags,
		Stamp stamp
) {

	@SbeEnum(primitiveType = CHAR, offset = 18)
	enum Side {

		@SbeEnumValue("B")
		Buy,

		@SbeEnumValue("S")
		Sell
	}

	@SbeSet(primitiveType = UINT8, offset = 19)
	enum Flags {

		@SbeChoice(0)
		firm
	}

	@SbeComposite(offset = 20, description = "When the quote was made")
	record Stamp(
			@SbeType(primitiveType = UINT64) long time,
			@SbeType(primitiveType = UINT8, presence = OPTIONAL, nullValue = "255") @Nullable Short precision,
			@SbeType(primitiveType = CHAR, presence = CONSTANT, value = "Z") byte zone
	) {
	}
}
