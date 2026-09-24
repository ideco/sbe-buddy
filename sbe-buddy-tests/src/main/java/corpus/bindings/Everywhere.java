package corpus.bindings;

import static net.concini.sbebuddy.Presence.CONSTANT;
import static net.concini.sbebuddy.Presence.OPTIONAL;
import static net.concini.sbebuddy.PrimitiveType.INT64;
import static net.concini.sbebuddy.PrimitiveType.UINT64;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.SbeData;
import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeGroup;
import net.concini.sbebuddy.SbeMessage;
import net.concini.sbebuddy.VarStringEncoding;

/**
 * A binding on every kind of component that carries a value: an enum, optional
 * and constant ones included, a set, a composite with bound members, one
 * binding class over a signed and an unsigned field, the time attributes a
 * binding reads, a group and var-data.
 */
@SbeMessage(id = 2)
@SuppressWarnings("deprecation") // timeUnit, which the binding reads
record Everywhere(
		@SbeField(id = 1, type = Flag.class, binding = FlagBinding.class) boolean urgent,
		@SbeField(id = 2, type = Flag.class, presence = OPTIONAL, binding = FlagBinding.class) @Nullable Boolean acknowledged,
		@SbeField(id = 3, type = Flag.class, presence = CONSTANT, valueRef = "Flag.YES", binding = FlagBinding.class) boolean live,
		@SbeField(id = 4, type = Permission.class, binding = AccessBinding.class) Access access,
		@SbeField(id = 5, type = Quote.class) Quote quote,
		@SbeField(id = 6, primitiveType = INT64, binding = CountBinding.class) BigInteger signedCount,
		@SbeField(id = 7, primitiveType = UINT64, binding = CountBinding.class) BigInteger unsignedCount,
		@SbeField(id = 8, primitiveType = INT64, epoch = "unix", timeUnit = "millisecond", binding = EpochTime.class) Instant sentAt,
		@SbeField(id = 9, primitiveType = INT64, timeUnit = "nanosecond", binding = EpochTime.class) Instant stampedAt,
		@SbeGroup(id = 10, binding = LegsBinding.class) Map<Integer, Leg> legs,
		@SbeData(id = 11, type = VarStringEncoding.class, binding = NoteBinding.class) Note note
) {
}
