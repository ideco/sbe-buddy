package corpus.namedtypes;

import static net.concini.sbebuddy.PrimitiveType.CHAR;

import net.concini.sbebuddy.SbeType;

@SbeType(primitiveType = CHAR, length = 6, characterEncoding = "ASCII", semanticType = "String", description = "An instrument symbol")
final class Symbol {
}
