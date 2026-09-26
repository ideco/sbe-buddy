package net.concini.sbebuddy.generator;

import java.util.List;

import net.concini.sbebuddy.generator.FlyweightModel.Parameter;

/**
 * What the writers are made of: a message's typestate over sbe-tool's encoder,
 * and the sub-chains a composite or a set field opens. A stage is an interface
 * whose methods are the only ways on; every stage of a block is implemented by
 * one object, and each method is guarded by the writer's position, so a stage
 * kept past the point where the writer moved on refuses. Every class, field and
 * method is named here, never in the writer.
 */
final class WriterModel {

	private WriterModel() {
	}

	/**
	 * A message's writer: its stages in chain order, the objects implementing them,
	 * the positions it passes through in wire order, and the null values each block
	 * is filled with when it opens. {@code first} is the stage {@code wrap} hands
	 * out; {@code header} the header's own members, written as their null values.
	 */
	record Message(
			String flyweights,
			String messageName,
			String message,
			String writer,
			String headerClass,
			String first,
			List<Stage> stages,
			List<String> positions,
			List<Implementation> implementations,
			List<Field> encoders,
			List<NullWrite> header,
			List<Nulls> nulls
	) {

		Message {
			stages = List.copyOf(stages);
			positions = List.copyOf(positions);
			implementations = List.copyOf(implementations);
			encoders = List.copyOf(encoders);
			header = List.copyOf(header);
			nulls = List.copyOf(nulls);
		}
	}

	/**
	 * A composite's sub-chain, {@code writer}: every member but the constants in
	 * wire order, the first on the class itself, each later one a stage of its own,
	 * the last returning the stage the caller continues with.
	 */
	record Composite(
			String flyweights,
			String compositeName,
			String writer,
			String encoder,
			List<Stage> stages,
			List<Site> sites,
			List<Method> methods,
			List<String> chain,
			List<Method> chainMethods
	) {

		Composite {
			stages = List.copyOf(stages);
			sites = List.copyOf(sites);
			methods = List.copyOf(methods);
			chain = List.copyOf(chain);
			chainMethods = List.copyOf(chainMethods);
		}
	}

	/**
	 * A set's sub-chain, {@code writer}: its choices in any order, then
	 * {@code end()}.
	 */
	record Set(String flyweights, String setName, String writer, String encoder, List<String> choices) {

		Set {
			choices = List.copyOf(choices);
		}
	}

	/**
	 * A stage: an interface, what it stands before or after, the wire path of
	 * {@code subject}, what it extends, and its methods.
	 */
	record Stage(Kind kind, String subject, String name, List<String> parents, List<Signature> methods) {

		Stage {
			parents = List.copyOf(parents);
			methods = List.copyOf(methods);
		}

		enum Kind {

			/** Before a block's required field. */
			FIELD,

			/** The root block complete. */
			ROOT_BLOCK,

			/** A group's entry complete. */
			ENTRY,

			/** A group, between its entries. */
			GROUP,

			/** Past a group or var-data. */
			AFTER,

			/** Before a composite's member. */
			MEMBER
		}
	}

	/**
	 * The object behind the stages of a block, or of a group: a class implementing
	 * them, held in the writer's field {@code field}, with the sub-chain writers
	 * its steps hand out.
	 */
	record Implementation(String name, String field, List<String> stages, List<Site> sites, List<Method> methods) {

		Implementation {
			stages = List.copyOf(stages);
			sites = List.copyOf(sites);
			methods = List.copyOf(methods);
		}
	}

	/** A field a class holds: its type and its name. */
	record Field(String type, String name) {
	}

	/**
	 * A sub-chain a class holds in {@code name}: the composite's or set's
	 * {@code writer}, handing back {@code next}.
	 */
	record Site(String writer, String next, String name) {
	}

	/** A method's signature, as a stage declares it. */
	record Signature(String returns, String name, List<Parameter> parameters) {

		Signature {
			parameters = List.copyOf(parameters);
		}
	}

	/**
	 * What the writer checks before a step: its position, in a message's writer, or
	 * that a sub-chain has not ended.
	 */
	sealed interface Guard {

		/**
		 * The writer's position between {@code first} and {@code last}, else
		 * {@code stage} is not the current stage.
		 */
		record Current(String first, String last, String stage) implements Guard {
		}

		/** The sub-chain {@code writer} has not ended. */
		record Open(String writer) implements Guard {
		}
	}

	/** How a step hands on. */
	sealed interface Then {

		/** The object of the next stage. */
		record Return(String object) implements Then {
		}

		/** The writer moved to {@code position}, and the object of its stage. */
		record Move(String position, String object) implements Then {
		}

		/** The sub-chain ends, handing back the stage its caller continues with. */
		record Release() implements Then {
		}
	}

	/** A method of a stage's object, by what it does. */
	sealed interface Method {

		Signature signature();

		/**
		 * The flyweight's method of the same name and parameters, on {@code flyweight}.
		 */
		record Put(Signature signature, Guard guard, String flyweight, Then then) implements Method {
		}

		/**
		 * Every element of an array from {@code src} at {@code srcOffset}, through the
		 * indexed setter {@code property} of {@code flyweight}, whose class is
		 * {@code encoder}.
		 */
		record PutEach(Signature signature, Guard guard, String flyweight, String encoder, String property, Then then)
				implements
					Method {
		}

		/**
		 * The member written as its null value over {@code encoder}, the class of the
		 * flyweight held as {@code encoder}.
		 */
		record PutNull(Signature signature, Guard guard, String encoder, NullWrite write, Then then)
				implements
					Method {
		}

		/**
		 * A composite's or a set's sub-chain, the writer held in {@code site}, over the
		 * flyweight {@code flyweight} hands out as {@code property()}.
		 */
		record Sub(Signature signature, Guard guard, String site, String flyweight, String property, Then then)
				implements
					Method {
		}

		/**
		 * A group opened with room for its maximum, its encoder, of the class
		 * {@code groupEncoder}, from {@code flyweight} into the writer's field
		 * {@code encoder}.
		 */
		record Open(
				Signature signature,
				Guard guard,
				String flyweight,
				String property,
				String groupEncoder,
				String encoder,
				Then then
		) implements Method {
		}

		/** A group's next entry, filled with its null values. */
		record Entry(Signature signature, Guard guard, String encoder, Then then) implements Method {
		}

		/** A group's count settled to the entries written. */
		record End(Signature signature, Guard guard, String encoder, Then then) implements Method {
		}

		/** The bytes written, header included. */
		record Length(Signature signature, Guard guard) implements Method {
		}

		/**
		 * The same method on the object {@code object}, whose stage this one extends.
		 */
		record Delegate(Signature signature, String object) implements Method {
		}
	}

	/**
	 * A field or member written as its null value, through {@code FaceWriter}'s
	 * null writers; {@code shape} is how, whatever maps it.
	 */
	record NullWrite(String property, Faces.Shape shape) {
	}

	/**
	 * The null values of one block or composite, over its encoder class
	 * {@code encoder}.
	 */
	record Nulls(String encoder, List<NullWrite> writes) {

		Nulls {
			writes = List.copyOf(writes);
		}
	}
}
