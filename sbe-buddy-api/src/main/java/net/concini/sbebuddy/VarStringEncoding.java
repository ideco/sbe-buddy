package net.concini.sbebuddy;

/**
 * A variable-length encoding for UTF-8 text, selected through {@link SbeData#type()}.
 * The payload follows an unsigned 16-bit length prefix that counts bytes.
 * Text is encoded using {@code UTF-8}.
 *
 * <p>The message component uses {@code String}, or an application type with
 * a binding; this record declares the encoding composite.</p>
 *
 * @param length the payload length in bytes
 * @param varData the payload
 */
@SbeComposite(name = "varStringEncoding")
public record VarStringEncoding(
		@SbeType(primitiveType = PrimitiveType.UINT16) int length,
		@SbeType(primitiveType = PrimitiveType.CHAR, length = 0, characterEncoding = "UTF-8") String varData
) {
}
