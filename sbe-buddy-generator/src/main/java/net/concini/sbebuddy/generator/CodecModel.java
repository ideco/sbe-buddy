package net.concini.sbebuddy.generator;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.generator.Faces.Shape;

/**
 * What one message's codec is made of, every name resolved: its bodies of
 * members, each field a {@link Faces} leaf, and the helper methods they call.
 * {@link CodecWalk} builds it from a {@link Join}; {@link CodecWriter} renders
 * it. Class names are qualified as the generated code writes them.
 */
record CodecModel(
		String packageName,
		String codec,
		String record,
		String flyweights,
		Header header,
		String message,
		int baseline,
		List<Faces.Binding> bindings,
		List<Faces.Context> contexts,
		Body body,
		List<Helper> helpers
) {

	CodecModel {
		bindings = List.copyOf(bindings);
		contexts = List.copyOf(contexts);
		helpers = List.copyOf(helpers);
	}

	/**
	 * The header every message is framed in, over the flyweights named after
	 * {@code headerClass}; {@code record} is what {@code decodeHeader} returns.
	 * {@code body} reads every component and writes the members of the header's
	 * own, never the standard four, which {@code wrapAndApplyHeader} writes;
	 * {@code nulls} writes those members as their null value when no header is
	 * given.
	 */
	record Header(String headerClass, String record, Body body, List<Member.Unmapped> nulls) {

		Header {
			nulls = List.copyOf(nulls);
		}
	}

	/**
	 * A message's, a composite's or a group entry's members over its flyweights: in
	 * the order the wire takes them, and in the order the record's constructor
	 * takes its components, which leaves out what no component carries.
	 */
	record Body(String encoder, String decoder, List<Member> wireOrder, List<Member> constructorOrder) {

		Body {
			wireOrder = List.copyOf(wireOrder);
			constructorOrder = List.copyOf(constructorOrder);
		}
	}

	sealed interface Member {

		/** A record component on a field or a composite member: its leaf. */
		record Field(Faces.Face.Mapped leaf) implements Member {
		}

		/**
		 * A field or member no component carries: written as its null value, an array's
		 * in every element, and never read.
		 */
		record Unmapped(String property, Shape shape) implements Member {
		}

		/**
		 * A repeating group read into a local of its component's name before the
		 * constructor; {@code addedSince} is the flyweight's since-version method when
		 * the group was appended above the baseline, or null; {@code binding} and
		 * {@code context} as on a field, the binding over the list.
		 */
		record Group(
				String component,
				String property,
				String path,
				String record,
				Body entry,
				@Nullable String addedSince,
				@Nullable String binding,
				@Nullable String context
		) implements Member {
		}

		/**
		 * Var-data, read into a local of its component's name before the constructor,
		 * through the methods of its {@code leaf}; {@code addedSince} is the
		 * flyweight's since-version method when the data was appended above the
		 * baseline, or null; {@code binding} and {@code context} as on a group.
		 */
		record Data(
				String component,
				Faces.Helper.Data leaf,
				@Nullable String addedSince,
				@Nullable String binding,
				@Nullable String context
		) implements Member {
		}
	}

	/**
	 * A method the codec declares once, however many members call it, in the order
	 * the walk first met each: a leaf's helper, or the methods of a group.
	 */
	sealed interface Helper {

		/** A method a leaf calls. */
		record Leaf(Faces.Helper helper) implements Helper {
		}

		/**
		 * A group's write, read and length methods, over its entry's body;
		 * {@code parent} is the encoder of the body the group is in, which sizes it.
		 */
		record GroupMethods(Member.Group group, String parent) implements Helper {
		}
	}
}
