package net.concini.sbebuddy;

/**
 * Whether an SBE field or type is required, optional or constant.
 */
public enum Presence {
	/**
	 * A value is required.
	 */
	REQUIRED,

	/**
	 * A value may be absent, represented by the type's null encoding.
	 */
	OPTIONAL,

	/**
	 * The value is declared in the schema and occupies no bytes.
	 */
	CONSTANT
}
