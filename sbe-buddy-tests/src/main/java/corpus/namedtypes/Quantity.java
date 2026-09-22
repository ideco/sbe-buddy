package corpus.namedtypes;

import static net.concini.sbebuddy.Presence.OPTIONAL;
import static net.concini.sbebuddy.PrimitiveType.UINT32;

import net.concini.sbebuddy.SbeType;

@SbeType(primitiveType = UINT32, presence = OPTIONAL, nullValue = "4294967294", description = "Absent when the size is not disclosed")
final class Quantity {
}
