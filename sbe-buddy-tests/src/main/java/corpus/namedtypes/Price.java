package corpus.namedtypes;

import static net.concini.sbebuddy.PrimitiveType.INT64;

import net.concini.sbebuddy.SbeType;

@SbeType(primitiveType = INT64, minValue = "0", maxValue = "9223372036854775806", semanticType = "Price")
final class Price {
}
