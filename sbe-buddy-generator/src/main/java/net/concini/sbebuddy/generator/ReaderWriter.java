package net.concini.sbebuddy.generator;

import static net.concini.sbebuddy.generator.ReaderStageTemplates.*;
import static net.concini.sbebuddy.generator.ReaderTemplates.*;

import java.util.ArrayList;
import java.util.List;

import net.concini.sbebuddy.generator.FlyweightModel.Accessor;
import net.concini.sbebuddy.generator.FlyweightModel.Parameter;
import net.concini.sbebuddy.generator.FlyweightModel.Part;
import net.concini.sbebuddy.generator.FlyweightModel.Position;
import net.concini.sbebuddy.generator.FlyweightModel.Step;

/**
 * A {@link FlyweightModel} as a reader's Java source, through
 * {@link ReaderTemplates} and {@link ReaderStageTemplates}: one switch per
 * question, what a part declares, what a step does and what it asks.
 */
final class ReaderWriter {

	private final FlyweightModel model;

	private ReaderWriter(FlyweightModel model) {
		this.model = model;
	}

	static String write(FlyweightModel model) {
		return new ReaderWriter(model).reader();
	}

	private String reader() {
		List<String> permits = new ArrayList<>(List.of("RootBlock"));
		List<String> fields = new ArrayList<>();
		List<String> methods = new ArrayList<>();
		List<String> stages = new ArrayList<>();
		FlyweightModel.RootBlock rootBlock = model.rootBlock();
		stages.add(
				ROOT_BLOCK.fill(
						rootBlock, "messageName", model.messageName(), "reader", model.reader(), "accessors",
						accessors(rootBlock.accessors(), "decoder", rootBlock.position(), rootBlock.last(), "RootBlock")
				)
		);
		for (Part part : model.parts()) {
			switch (part) {
				case FlyweightModel.Group group -> {
					permits.add(group.name());
					permits.add(group.entry());
					fields.add(GROUP_FIELDS.fill(group));
					methods.add(
							GROUP_METHODS.fill(
									group, "following", next(group.following()), "hasRest",
									or(HAS_NEXT_ENTRY.fill(group), group.following())
							)
					);
					stages.add(HEADER.fill(group, "reader", model.reader()));
					stages.add(
							ENTRY.fill(
									group, "reader", model.reader(), "accessors",
									accessors(
											group.accessors(), GROUP_DECODER.fill(group), group.entryPosition(),
											group.last(), group.entry()
									)
							)
					);
				}
				case FlyweightModel.Data data -> {
					permits.add(data.name());
					fields.add(DATA_FIELDS.fill(data));
					methods.add(DATA_METHODS.fill(data));
					stages.add(DATA.fill(data, "reader", model.reader()));
				}
			}
		}
		List<String> positions = new ArrayList<>();
		List<String> hasNextCases = new ArrayList<>();
		List<String> nextCases = new ArrayList<>();
		for (Position position : model.positions()) {
			positions.add(POSITION.fill(position));
			hasNextCases.add(CASE.fill(position, "step", has(position.next())));
			nextCases.add(CASE.fill(position, "step", next(position.next())));
		}
		return READER.fill(
				model,
				"permits", String.join(", ", permits),
				"positions", String.join("\n", positions),
				"fields", String.join("\n", fields),
				"hasNextCases", String.join("\n", hasNextCases),
				"nextCases", String.join("\n", nextCases),
				"methods", String.join("\n", methods),
				"stages", String.join("\n\n", stages)
		);
	}

	/**
	 * A stage's accessors, each delegating to {@code flyweight} once the stage is
	 * open.
	 */
	private String accessors(List<Accessor> accessors, String flyweight, String first, String last, String stage) {
		List<String> written = new ArrayList<>();
		for (Accessor accessor : accessors) {
			List<String> parameters = new ArrayList<>();
			List<String> arguments = new ArrayList<>();
			for (Parameter parameter : accessor.parameters()) {
				parameters.add(PARAMETER.fill(parameter));
				arguments.add(parameter.name());
			}
			Template template = switch (accessor) {
				case Accessor.Getter getter -> GETTER;
				case Accessor.Wrap wrap -> WRAP;
			};
			written.add(
					template.fill(
							(Record) accessor, "parameters", String.join(", ", parameters), "arguments",
							String.join(", ", arguments), "reader", model.reader(), "first", first, "last", last,
							"stage", stage, "flyweight", flyweight
					)
			);
		}
		return String.join("\n", written);
	}

	// ---- the steps

	/** What {@code next()} does to take the step. */
	private static String next(Step step) {
		return switch (step) {
			case Step.Arrive arrive -> CALL.fill(arrive);
			case Step.ArriveSince since -> ARRIVE_SINCE
					.fill(since, "present", PRESENT.fill(since), "otherwise", next(since.otherwise()));
			case Step.Rest rest -> CALL.fill(rest);
			case Step.End end -> END_CALL.fill();
		};
	}

	/** What {@code hasNext()} asks: whether the step leads to a stage. */
	private static String has(Step step) {
		return switch (step) {
			case Step.Arrive arrive -> TRUE.fill();
			case Step.ArriveSince since -> or(PRESENT.fill(since), since.otherwise());
			case Step.Rest rest -> CALL.fill("method", rest.hasMethod());
			case Step.End end -> FALSE.fill();
		};
	}

	/** {@code condition}, or else whether {@code otherwise} leads to a stage. */
	private static String or(String condition, Step otherwise) {
		if (otherwise.certain()) {
			return TRUE.fill();
		}
		if (otherwise instanceof Step.End) {
			return condition;
		}
		return OR.fill("left", condition, "right", has(otherwise));
	}
}
