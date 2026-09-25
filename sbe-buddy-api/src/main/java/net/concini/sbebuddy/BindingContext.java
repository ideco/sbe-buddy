package net.concini.sbebuddy;

import org.jspecify.annotations.Nullable;

/**
 * Schema metadata supplied to a {@link TypeBinding} for a record component.
 * Generated codecs create one immutable context per bound component and reuse
 * it for every conversion.
 *
 * <p>The context describes the declared schema, not the header or version of
 * the message currently being processed. A binding can use this information
 * to serve components with different wire representations.</p>
 *
 * @param name
 *            the component name used in codec diagnostics
 * @param primitiveType
 *            the resolved SBE primitive type; for strings and arrays, the
 *            element type; for enums and sets, the encoding type; null for
 *            composites and groups
 * @param characterEncoding
 *            the character encoding declared on a fixed-length string or
 *            text variable-length data; null when unspecified or inapplicable
 * @param epoch
 *            the field's epoch label, passed through without interpretation;
 *            null when unspecified or inapplicable
 * @param timeUnit
 *            the field's time-unit label, passed through without interpretation;
 *            null when unspecified or inapplicable
 * @param presence
 *            the effective presence of the field or composite member, including
 *            presence inherited from a named type; null for groups and
 *            variable-length data
 */
public record BindingContext(
		String name,
		@Nullable PrimitiveType primitiveType,
		@Nullable String characterEncoding,
		@Nullable String epoch,
		@Nullable String timeUnit,
		@Nullable Presence presence
) {
}
