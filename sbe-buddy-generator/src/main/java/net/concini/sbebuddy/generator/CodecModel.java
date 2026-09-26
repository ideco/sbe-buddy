package net.concini.sbebuddy.generator;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * What one message's codec is made of, every name resolved: the record's
 * structure over the message's reader and writer, which hold every leaf, and
 * the header, which the codec reads and writes itself. {@link CodecWalk} builds
 * it from a {@link Join} and the reader's and writer's models;
 * {@link CodecWriter} renders it. Class names are qualified as the generated
 * code writes them.
 */
record CodecModel(
		String packageName,
		String codec,
		String record,
		String flyweights,
		Header header,
		String message,
		String reader,
		String writer,
		String first,
		int baseline,
		List<Faces.Binding> bindings,
		List<Faces.Context> contexts,
		Block block,
		List<Group> groups,
		List<Faces.Helper> helpers
) {

	CodecModel {
		bindings = List.copyOf(bindings);
		contexts = List.copyOf(contexts);
		groups = List.copyOf(groups);
		helpers = List.copyOf(helpers);
	}

	/**
	 * The header every message is framed in, over the flyweights named after
	 * {@code headerClass}; {@code record} is what {@code decodeHeader} returns,
	 * read from any message's header, so through no reader. {@code own} writes the
	 * header's own members, never the standard four, which the writer writes, and
	 * {@code constructorOrder} reads every component; the helpers they call are the
	 * codec's.
	 */
	record Header(
			String headerClass,
			String record,
			String encoder,
			String decoder,
			List<Faces.Face.Mapped> own,
			List<Faces.Face.Mapped> constructorOrder
	) {

		Header {
			own = List.copyOf(own);
			constructorOrder = List.copyOf(constructorOrder);
		}
	}

	/**
	 * The message's block or a group's entry, read through the reader's stage
	 * {@code stage}, whose bound stage {@code bound} reads the components, null
	 * where the block carries none; its groups and var-data in wire order, and the
	 * components in the order the record's constructor takes them. {@code checks}
	 * are the groups refused as null before the block is written, and {@code chain}
	 * the calls that write it, from the stage it starts at.
	 */
	record Block(
			String stage,
			@Nullable String bound,
			List<Variable> variable,
			List<Component> constructorOrder,
			List<Variable.Group> checks,
			List<Call> chain
	) {

		Block {
			variable = List.copyOf(variable);
			constructorOrder = List.copyOf(constructorOrder);
			checks = List.copyOf(checks);
			chain = List.copyOf(chain);
		}
	}

	/** A constructor argument: a field's component, or a group or var-data. */
	sealed interface Component {

		/** A component the block's bound stage reads, by its name. */
		record Field(String component) implements Component {
		}
	}

	/**
	 * A group or var-data, read into a local of its component's name before the
	 * constructor, in wire order; {@code addedSince} is the flyweight's
	 * since-version method, static on {@code decoder}, when it was appended above
	 * the baseline, or null; {@code binding} and {@code context} stand in front of
	 * it where the component has one.
	 */
	sealed interface Variable extends Component {

		String component();

		/**
		 * A repeating group at {@code path}, which names its methods, of the entry
		 * record {@code record}; the binding over the list. {@code encoder} is the
		 * entry's flyweight, whose sizes it is counted by.
		 */
		record Group(
				String component,
				String path,
				String record,
				String encoder,
				@Nullable String addedSince,
				String decoder,
				@Nullable String binding,
				@Nullable String context
		) implements Variable {
		}

		/**
		 * Var-data, the reader's stage {@code stage} read through its bound stage
		 * {@code bound} as {@code type}, bound by the reader; {@code length} is the
		 * writer's method that counts it, which the codec hands its binding's view.
		 */
		record Data(
				String component,
				String stage,
				String bound,
				String type,
				String length,
				@Nullable String addedSince,
				String decoder,
				@Nullable String binding,
				@Nullable String context
		) implements Variable {
		}
	}

	/**
	 * One call of a block's chain on the writer's stages; the message's chain ends
	 * with its length, an entry's hands on to its group.
	 */
	sealed interface Call {

		/** From a stage to its twin. */
		record Hop() implements Call {
		}

		/** A twin's step taking the component of its name. */
		record Take(String component) implements Call {
		}

		/**
		 * A group opened by {@code method}, its stage {@code stage} held in
		 * {@code local}, and its entries written by the group's method, from whose
		 * return the chain goes on.
		 */
		record Open(Variable.Group group, String method, String stage, String local) implements Call {
		}
	}

	/**
	 * A group's write, read and length methods, over its entry: {@code stage} is
	 * the writer's group stage and {@code after} what its {@code end()} returns,
	 * {@code header} the reader's header stage.
	 */
	record Group(Variable.Group group, Block entry, String stage, String after, String header) {
	}
}
