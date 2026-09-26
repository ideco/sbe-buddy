package net.concini.sbebuddy.generator;

import java.util.List;

/**
 * What a message's reader is made of: its stages over sbe-tool's flyweights,
 * the positions the reader passes through in wire order, and for each position
 * the step that moves it on. A stage is open while the reader's position lies
 * between its {@code first} and {@code last}; the names are sbe-tool's, through
 * {@code JavaUtil}, and every class, field and method of the reader is named
 * here, never in the writer.
 */
record FlyweightModel(
		String flyweights,
		String messageName,
		String message,
		String reader,
		String headerClass,
		RootBlock rootBlock,
		List<Part> parts,
		List<Position> positions
) {

	FlyweightModel {
		parts = List.copyOf(parts);
		positions = List.copyOf(positions);
	}

	/** The root block: the message's fields, open from its arrival to the end. */
	record RootBlock(String position, String last, List<Accessor> accessors) {

		RootBlock {
			accessors = List.copyOf(accessors);
		}
	}

	/** A group or var-data, in the order its first stage comes. */
	sealed interface Part {
	}

	/**
	 * A group at {@code path}, its wire names joined by dots: the header stage
	 * {@code name} and the entry stage {@code entry}, both over the group decoder
	 * {@code decoder} that {@code owner}, the flyweight of the block holding the
	 * group, hands out as {@code property()}. The header is open from
	 * {@code position} to {@code last}, the entry from {@code entryPosition} to
	 * {@code last}, which is where an entry's skip leaves the reader; {@code after}
	 * is where a header's skip leaves it, and {@code following} what comes after
	 * the group at its level.
	 */
	record Group(
			String path,
			String name,
			String entry,
			String property,
			String decoder,
			String owner,
			String position,
			String entryPosition,
			String last,
			String after,
			Step following,
			List<Accessor> accessors
	) implements Part {

		Group {
			accessors = List.copyOf(accessors);
		}
	}

	/**
	 * Var-data at {@code path}: the stage {@code name}, open at {@code position}
	 * alone, read through the accessors of {@code owner}, the flyweight of the
	 * block holding it, named after {@code property} and, for the bulk ones,
	 * {@code bulk}.
	 */
	record Data(String path, String name, String property, String bulk, String owner, String position) implements Part {
	}

	/**
	 * One of sbe-tool's accessors, delegated as it is by a stage: a getter
	 * returning {@code type}, or a {@code wrap} returning nothing.
	 */
	sealed interface Accessor {

		String name();

		List<Parameter> parameters();

		record Getter(String type, String name, List<Parameter> parameters) implements Accessor {

			public Getter {
				parameters = List.copyOf(parameters);
			}
		}

		record Wrap(String name, List<Parameter> parameters) implements Accessor {

			public Wrap {
				parameters = List.copyOf(parameters);
			}
		}
	}

	record Parameter(String type, String name) {
	}

	/** A position of the reader, an {@code At} constant, and how it moves on. */
	record Position(String name, Step next) {
	}

	/**
	 * How the reader moves on: what {@code next()} does, and what {@code hasNext()}
	 * asks without moving.
	 */
	sealed interface Step {

		/** Whether a stage comes by this step whatever the message holds. */
		boolean certain();

		/** A stage that always comes, by the method that arrives at it. */
		record Arrive(String method) implements Step {

			@Override
			public boolean certain() {
				return true;
			}
		}

		/**
		 * A group or var-data added in a later version: arrived at where the acting
		 * version reaches {@code sinceVersion}, the flyweight's static method, else
		 * {@code otherwise}.
		 */
		record ArriveSince(String method, String sinceVersion, Step otherwise) implements Step {

			@Override
			public boolean certain() {
				return otherwise.certain();
			}
		}

		/**
		 * Past an entry's content: the group's next entry, else what follows it, by
		 * {@code method}; {@code hasMethod} asks the same.
		 */
		record Rest(String method, String hasMethod) implements Step {

			@Override
			public boolean certain() {
				return false;
			}
		}

		/** Nothing follows. */
		record End() implements Step {

			@Override
			public boolean certain() {
				return false;
			}
		}
	}
}
