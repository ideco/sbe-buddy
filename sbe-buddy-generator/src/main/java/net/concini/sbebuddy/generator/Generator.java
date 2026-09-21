package net.concini.sbebuddy.generator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.ToIntFunction;

import org.jspecify.annotations.Nullable;

/**
 * The rules of ours that compare nodes, which no single node can decide: names
 * and ids that collide, and versions that do not line up with the schema's or
 * with the siblings they follow. Each problem names the {@link Schema} node it
 * was decided from.
 */
public final class Generator {

	private final List<Problem> problems = new ArrayList<>();
	private final int version;

	private Generator(int version) {
		this.version = version;
	}

	public static List<Problem> validate(Schema schema) {
		Generator generator = new Generator(schema.version());
		generator.schema(schema);
		return List.copyOf(generator.problems);
	}

	private void schema(Schema schema) {
		Set<String> names = new HashSet<>();
		for (Schema.Declaration declaration : schema.types()) {
			String name = wireName(declaration);
			if (!names.add(name)) {
				problem(declaration, "two declarations are named \"" + name + "\"");
			}
			declaration(declaration);
		}
		Set<String> messageNames = new HashSet<>();
		Set<Integer> messageIds = new HashSet<>();
		for (Schema.Message message : schema.messages()) {
			if (!messageNames.add(message.name())) {
				problem(message, "two messages are named \"" + message.name() + "\"");
			}
			if (!messageIds.add(message.id())) {
				problem(message, "two messages have id " + message.id());
			}
			versions(message, message.sinceVersion(), message.deprecated());
			block(message.fields(), message.groups(), message.data());
		}
	}

	private void declaration(Schema.Declaration declaration) {
		switch (declaration) {
			case Schema.Type type -> versions(type, type.sinceVersion(), type.deprecated());
			case Schema.Composite composite -> composite(composite);
			case Schema.Enum enumeration -> enumeration(enumeration);
			case Schema.Set set -> set(set);
		}
	}

	private void composite(Schema.Composite composite) {
		versions(composite, composite.sinceVersion(), composite.deprecated());
		appendOnly(composite.members(), member -> since(memberSinceVersion(member)));
		for (Schema.Member member : composite.members()) {
			switch (member) {
				case Schema.Type type -> versions(type, type.sinceVersion(), type.deprecated());
				case Schema.Ref ref -> versions(ref, ref.sinceVersion(), ref.deprecated());
				case Schema.Composite nested -> composite(nested);
				case Schema.Enum enumeration -> enumeration(enumeration);
				case Schema.Set set -> set(set);
			}
		}
	}

	private void enumeration(Schema.Enum enumeration) {
		versions(enumeration, enumeration.sinceVersion(), enumeration.deprecated());
		for (Schema.ValidValue value : enumeration.validValues()) {
			versions(value, value.sinceVersion(), value.deprecated());
		}
	}

	private void set(Schema.Set set) {
		versions(set, set.sinceVersion(), set.deprecated());
		for (Schema.Choice choice : set.choices()) {
			versions(choice, choice.sinceVersion(), choice.deprecated());
		}
	}

	/**
	 * A message's or a group's body: one namespace over the three lists, and each
	 * list appended to in version order, because each lays out in its own order.
	 */
	private void block(List<Schema.Field> fields, List<Schema.Group> groups, List<Schema.Data> data) {
		Set<String> names = new HashSet<>();
		Set<Integer> ids = new HashSet<>();
		for (Schema.Field field : fields) {
			member(field, field.name(), field.id(), names, ids);
			versions(field, field.sinceVersion(), field.deprecated());
		}
		for (Schema.Group group : groups) {
			member(group, group.name(), group.id(), names, ids);
			versions(group, group.sinceVersion(), group.deprecated());
			block(group.fields(), group.groups(), group.data());
		}
		for (Schema.Data datum : data) {
			member(datum, datum.name(), datum.id(), names, ids);
			versions(datum, datum.sinceVersion(), datum.deprecated());
		}
		appendOnly(fields, field -> since(field.sinceVersion()));
		appendOnly(groups, group -> since(group.sinceVersion()));
		appendOnly(data, datum -> since(datum.sinceVersion()));
	}

	private void member(Object node, String name, int id, Set<String> names, Set<Integer> ids) {
		if (!names.add(name)) {
			problem(node, "two members are named \"" + name + "\"");
		}
		if (!ids.add(id)) {
			problem(node, "two members have id " + id);
		}
	}

	private void versions(Object node, @Nullable Integer sinceVersion, @Nullable Integer deprecated) {
		int since = since(sinceVersion);
		if (since > version) {
			problem(node, "sinceVersion " + since + " is above the schema's version " + version);
		}
		if (deprecated != null && deprecated < since) {
			problem(node, "deprecated " + deprecated + " is below sinceVersion " + since);
		}
	}

	private <T> void appendOnly(List<T> siblings, ToIntFunction<T> sinceVersion) {
		int highest = 0;
		for (T sibling : siblings) {
			int since = sinceVersion.applyAsInt(sibling);
			if (since < highest) {
				problem(sibling, "sinceVersion " + since + " follows a sibling added in " + highest);
			}
			highest = Math.max(highest, since);
		}
	}

	private void problem(Object node, String message) {
		problems.add(new Problem(node, message));
	}

	private static int since(@Nullable Integer sinceVersion) {
		return sinceVersion == null ? 0 : sinceVersion;
	}

	private static String wireName(Schema.Declaration declaration) {
		return switch (declaration) {
			case Schema.Type type -> type.name();
			case Schema.Composite composite -> composite.name();
			case Schema.Enum enumeration -> enumeration.name();
			case Schema.Set set -> set.name();
		};
	}

	private static @Nullable Integer memberSinceVersion(Schema.Member member) {
		return switch (member) {
			case Schema.Type type -> type.sinceVersion();
			case Schema.Ref ref -> ref.sinceVersion();
			case Schema.Composite composite -> composite.sinceVersion();
			case Schema.Enum enumeration -> enumeration.sinceVersion();
			case Schema.Set set -> set.sinceVersion();
		};
	}
}
