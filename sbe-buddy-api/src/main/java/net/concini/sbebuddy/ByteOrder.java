package net.concini.sbebuddy;

/**
 * The byte order used to encode multibyte values in an SBE schema.
 */
public enum ByteOrder {
	/**
	 * Encodes the least significant byte first.
	 */
	LITTLE_ENDIAN,

	/**
	 * Encodes the most significant byte first.
	 */
	BIG_ENDIAN
}
