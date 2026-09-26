package net.concini.sbebuddy.generator;

import static net.concini.sbebuddy.generator.FaceTemplates.BINDING_FIELD;
import static net.concini.sbebuddy.generator.FaceTemplates.CONTEXT_FIELD;
import static net.concini.sbebuddy.generator.ReaderStageTemplates.*;
import static net.concini.sbebuddy.generator.ReaderTemplates.*;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.concini.sbebuddy.generator.FlyweightModel.Accessor;
import net.concini.sbebuddy.generator.FlyweightModel.Parameter;
import net.concini.sbebuddy.generator.FlyweightModel.Part;
import net.concini.sbebuddy.generator.FlyweightModel.Position;
import net.concini.sbebuddy.generator.FlyweightModel.Step;

/**
 * A {@link FlyweightModel} as a reader's Java source, through
 * {@link ReaderTemplates} and {@link ReaderStageTemplates}: one switch per
 * question, what a part declares, what a step does and what it asks. A bound
 * stage reads each component through {@link FaceWriter}, as the codec does, the
 * helpers called through the reader's class.
 */
final class ReaderWriter {

	private final FlyweightModel model;
	private final FaceWriter faces;

	private ReaderWriter(FlyweightModel model) {
		this.model = model;
		this.faces = new FaceWriter(model.flyweights(), model.reader() + ".", model.reader() + ".this.");
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
		FlyweightModel.Bound rootBound = rootBlock.bound();
		stages.add(
				ROOT_BLOCK.fill(
						rootBlock, "messageName", model.messageName(), "reader", model.reader(), "accessors",
						accessors(
								rootBlock.accessors(), "decoder", rootBlock.position(), rootBlock.last(), "RootBlock"
						),
						"bound", bound(rootBound, rootBlock.position(), rootBlock.last(), "RootBlock")
				)
		);
		if (rootBound != null) {
			stages.add(
					boundBlock(
							rootBound, "decoder", rootBlock.position(), rootBlock.last(), "RootBlock", "rootBlockStage",
							"the root block", ""
					)
			);
		}
		for (Part part : model.parts()) {
			switch (part) {
				case FlyweightModel.Group group -> {
					permits.add(group.name());
					permits.add(group.entry());
					FlyweightModel.Bound bound = group.bound();
					fields.add(GROUP_FIELDS.fill(group, "bound", field(bound)));
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
									), "bound", bound(bound, group.entryPosition(), group.last(), group.entry())
							)
					);
					if (bound != null) {
						stages.add(
								boundBlock(
										bound, GROUP_DECODER.fill(group), group.entryPosition(), group.last(),
										group.entry(), group.property() + "EntryStage",
										"an entry of {@code " + group.path()
												+ "}",
										BOUND_INDEX.fill(
												group, "reader", model.reader(), "first", group.entryPosition(),
												"stage",
												group.entry()
										)
								)
						);
					}
				}
				case FlyweightModel.Data data -> {
					permits.add(data.name());
					FlyweightModel.BoundData bound = data.bound();
					fields.add(DATA_FIELDS.fill(data, "bound", bound == null ? "" : BOUND_FIELD.fill(bound)));
					methods.add(DATA_METHODS.fill(data));
					stages.add(
							DATA.fill(
									data, "reader", model.reader(), "bound",
									bound == null
											? ""
											: BOUND.fill(
													bound, "reader", model.reader(), "first", data.position(), "last",
													data.position(), "stage", data.name()
											)
							)
					);
					if (bound != null) {
						String read = faces.readData(bound.leaf(), data.owner());
						if (bound.binding() != null) {
							read = FaceTemplates.BOUND_READ.fill(bound, "read", read);
						}
						stages.add(
								BOUND_DATA.fill(
										bound, "path", data.path(), "reader", model.reader(), "position",
										data.position(),
										"stage", data.name(), "property", data.property(), "read", read, "stageField",
										data.property() + "Stage"
								)
						);
					}
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
		List<String> helpers = new ArrayList<>();
		for (Faces.Helper helper : model.helpers()) {
			helpers.addAll(faces.helper(helper, false, true));
		}
		List<String> bindings = new ArrayList<>();
		for (Faces.Binding binding : model.bindings()) {
			bindings.add(BINDING_FIELD.fill(binding));
		}
		List<String> contexts = new ArrayList<>();
		for (Faces.Context context : model.contexts()) {
			contexts.add(CONTEXT_FIELD.fill(context));
		}
		return READER.fill(
				model,
				"contexts", String.join("\n", contexts),
				"bindings", String.join("\n", bindings),
				"rootBlockBound", field(rootBound),
				"helpers", helpers.isEmpty() ? "" : "\n" + String.join("\n\n", helpers),
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

	// ---- the bound stages

	private static String field(FlyweightModel.@Nullable Bound bound) {
		return bound == null ? "" : BOUND_FIELD.fill(bound);
	}

	/** A stage's {@code bound()}, or nothing where no record maps its block. */
	private String bound(FlyweightModel.@Nullable Bound bound, String first, String last, String stage) {
		if (bound == null) {
			return "";
		}
		return BOUND.fill(bound, "reader", model.reader(), "first", first, "last", last, "stage", stage);
	}

	/**
	 * A block's bound stage: {@code index} first on an entry's, then each component
	 * read from {@code flyweight} as the codec reads it, once the stage is open.
	 */
	private String boundBlock(
			FlyweightModel.Bound bound, String flyweight, String first, String last, String stage, String stageField,
			String subject, String index
	) {
		List<String> accessors = new ArrayList<>();
		if (!index.isEmpty()) {
			accessors.add(index);
		}
		for (Faces.Face.Mapped leaf : bound.components()) {
			accessors.add(
					BOUND_GETTER.fill(
							leaf, "reader", model.reader(), "first", first, "last", last, "stage", stage, "read",
							faces.read(leaf, flyweight, bound.decoder())
					)
			);
		}
		return BOUND_BLOCK.fill(
				bound, "subject", subject, "accessors", String.join("\n", accessors), "reader", model.reader(),
				"first", first, "last", last, "stage", stage, "stageField", stageField
		);
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
