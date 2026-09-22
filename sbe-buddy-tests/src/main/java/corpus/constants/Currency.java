package corpus.constants;

import static net.concini.sbebuddy.Presence.CONSTANT;
import static net.concini.sbebuddy.PrimitiveType.CHAR;

import net.concini.sbebuddy.SbeType;

@SbeType(primitiveType = CHAR, length = 3, presence = CONSTANT, value = "USD")
final class Currency {
}
