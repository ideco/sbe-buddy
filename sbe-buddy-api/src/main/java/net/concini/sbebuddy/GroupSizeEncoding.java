package net.concini.sbebuddy;

/**
 * The standard repeating-group dimension header, used unless
 * {@link SbeGroup#dimensionType()} selects a custom encoding. Both components
 * are encoded as unsigned 16-bit values.
 *
 * @param blockLength
 *            the byte length of each entry's fixed-length block, excluding
 *            nested groups and variable-length data
 * @param numInGroup
 *            the number of entries in the group
 */
@SbeComposite(name = "groupSizeEncoding")
public record GroupSizeEncoding(
		@SbeType(primitiveType = PrimitiveType.UINT16) int blockLength,
		@SbeType(primitiveType = PrimitiveType.UINT16) int numInGroup
) {
}
