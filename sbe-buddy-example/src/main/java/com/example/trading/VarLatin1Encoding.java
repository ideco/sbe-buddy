package com.example.trading;

import static net.concini.sbebuddy.PrimitiveType.CHAR;
import static net.concini.sbebuddy.PrimitiveType.UINT16;

import net.concini.sbebuddy.SbeComposite;
import net.concini.sbebuddy.SbeType;

/** Var-data holding text in ISO-8859-1, as an older venue writes it. */
@SbeComposite
public record VarLatin1Encoding(
		@SbeType(primitiveType = UINT16) int length,
		@SbeType(primitiveType = CHAR, length = 0, characterEncoding = "ISO-8859-1") String varData
) {
}
