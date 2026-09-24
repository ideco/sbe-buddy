package corpus.bindings;

import static net.concini.sbebuddy.PrimitiveType.INT64;

import java.time.Instant;

import net.concini.sbebuddy.SbeField;
import net.concini.sbebuddy.SbeMessage;

/** A field without a time unit, which the time binding refuses. */
@SbeMessage(id = 3)
record Untimed(@SbeField(id = 1, primitiveType = INT64, binding = EpochTime.class) Instant at) {
}
