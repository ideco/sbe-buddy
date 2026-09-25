package net.concini.sbebuddy;

/**
 * The primitive encodings supported by SBE. Unsigned encodings below 64 bits
 * use a wider Java primitive; {@link #NONE} indicates that an annotation member
 * is unspecified.
 */
public enum PrimitiveType {
	/**
	 * A one-byte character, represented by Java byte for scalar values.
	 */
	CHAR,

	/**
	 * A signed 8-bit integer, represented by Java byte.
	 */
	INT8,

	/**
	 * A signed 16-bit integer, represented by Java short.
	 */
	INT16,

	/**
	 * A signed 32-bit integer, represented by Java int.
	 */
	INT32,

	/**
	 * A signed 64-bit integer, represented by Java long.
	 */
	INT64,

	/**
	 * An unsigned 8-bit integer, represented by Java short.
	 */
	UINT8,

	/**
	 * An unsigned 16-bit integer, represented by Java int.
	 */
	UINT16,

	/**
	 * An unsigned 32-bit integer, represented by Java long.
	 */
	UINT32,

	/**
	 * An unsigned 64-bit integer, represented by its bit pattern in a Java long.
	 */
	UINT64,

	/**
	 * A 32-bit floating-point value, represented by Java float.
	 */
	FLOAT,

	/**
	 * A 64-bit floating-point value, represented by Java double.
	 */
	DOUBLE,

	/**
	 * An unspecified primitive type; not an SBE encoding.
	 */
	NONE
}
