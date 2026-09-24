package net.concini.sbebuddy;

import org.jspecify.annotations.Nullable;

/**
 * What the schema says about the component a {@link TypeBinding} stands for,
 * handed to it on every call, so one binding class serves every component it
 * fits. Each component with a binding has one context in its codec, built once
 * from the schema; sbe-buddy interprets none of it.
 *
 * @param name
 *            the component's name, as the codec's own messages name it
 * @param primitiveType
 *            the wire primitive after a named type is resolved: an array's or a
 *            string's element, an enum's or a set's encoding; null for a
 *            composite and a group
 * @param characterEncoding
 *            the type's, for a {@code char} array and text var-data; otherwise
 *            null
 * @param epoch
 *            the field's {@code epoch} as the schema writes it; null where it
 *            is absent or the schema has none, as on a composite's member
 * @param timeUnit
 *            the field's {@code timeUnit} as the schema writes it; null where
 *            it is absent or the schema has none
 */
public record BindingContext(
		String name,
		@Nullable PrimitiveType primitiveType,
		@Nullable String characterEncoding,
		@Nullable String epoch,
		@Nullable String timeUnit
) {
}
