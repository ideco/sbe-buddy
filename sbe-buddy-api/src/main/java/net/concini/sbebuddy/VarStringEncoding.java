package net.concini.sbebuddy;

/** Var-data holding UTF-8 text. */
@SbeComposite(name = "varStringEncoding")
public record VarStringEncoding(
		@SbeType(primitiveType = PrimitiveType.UINT16) int length,
		@SbeType(primitiveType = PrimitiveType.CHAR, length = 0, characterEncoding = "UTF-8") String varData
) {
}
