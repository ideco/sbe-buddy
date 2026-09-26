package net.concini.sbebuddy.generator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import uk.co.real_logic.sbe.generation.Generators;
import uk.co.real_logic.sbe.generation.java.JavaUtil;
import uk.co.real_logic.sbe.ir.Ir;
import uk.co.real_logic.sbe.ir.Token;

import net.concini.sbebuddy.generator.CodecModel.Body;
import net.concini.sbebuddy.generator.CodecModel.Helper;
import net.concini.sbebuddy.generator.CodecModel.Member;
import net.concini.sbebuddy.generator.Faces.Face;

/**
 * One message's {@link CodecModel}: the message {@link Join joined} with its
 * record, then its bodies built from the tree in wire and constructor order,
 * and the helpers its members call collected, each once, in the order the tree
 * first uses it, a composite's pair after its members' helpers and a group's
 * methods after its entry's. A message with an error gets no model.
 */
final class CodecWalk {

	private final List<Helper> helpers = new ArrayList<>();
	private final Set<Faces.Helper> leaves = new HashSet<>();
	private final Set<Join.Composite> pairs = Collections.newSetFromMap(new IdentityHashMap<>());
	private final Map<Object, Member> members = new IdentityHashMap<>();

	private CodecWalk() {
	}

	/**
	 * The message's model, or null with an error among the problems added to
	 * {@code problems}; a warning is added and stops nothing. {@code baseline} is
	 * the oldest version the codec reads.
	 */
	static @Nullable CodecModel walk(
			Ir ir, Annotated annotated, int baseline, Annotated.Message message, List<Problem> problems
	) {
		List<Problem> found = new ArrayList<>();
		CodecModel model = model(ir, annotated, baseline, message, found);
		problems.addAll(found);
		return found.stream().anyMatch(Problem::isError) ? null : model;
	}

	/** Null, with the problem, for a record whose id names no message. */
	private static @Nullable CodecModel model(
			Ir ir, Annotated annotated, int baseline, Annotated.Message message, List<Problem> problems
	) {
		List<Token> tokens = ir.getMessage(message.id());
		if (tokens == null) {
			problems.add(new Problem(message, "the schema has no message with id " + message.id()));
			return null;
		}
		String wireName = Join.wireName(message.name(), message.javaName());
		if (!tokens.get(0).name().equals(wireName)) {
			problems.add(
					new Problem(
							message, "the schema's message with id " + message.id() + " is named \""
									+ tokens.get(0).name() + "\", not \"" + wireName + "\""
					)
			);
		}
		Join.Message joined = Join.join(ir, annotated, baseline, tokens, message, problems);
		if (problems.stream().anyMatch(Problem::isError)) {
			return null;
		}
		CodecWalk walk = new CodecWalk();
		CodecModel.Header header = walk.header(joined.header());
		Body body = walk.body(joined.block());
		return new CodecModel(
				annotated.packageName(), message.javaName() + "Codec", message.qualifiedName(),
				ir.applicableNamespace(), header, JavaUtil.formatClassName(tokens.get(0).name()), baseline,
				joined.bindings(), joined.contexts(), body, walk.helpers
		);
	}

	/**
	 * The header's body reads every member and writes its own, and its nulls write
	 * those as their null value.
	 */
	private CodecModel.Header header(Join.Header header) {
		Join.Composite composite = header.composite();
		for (Join.Field field : composite.members()) {
			member(field);
		}
		List<Member> own = new ArrayList<>();
		for (Join.Field field : header.own()) {
			own.add(joined(field));
		}
		List<Member.Unmapped> nulls = new ArrayList<>();
		for (Join.Field field : header.nulls()) {
			if (member(field) instanceof Member.Unmapped unmapped) {
				nulls.add(unmapped);
			}
		}
		return new CodecModel.Header(
				composite.name(), composite.record(),
				new Body(composite.encoder(), composite.decoder(), own, constructorOrder(composite.constructorOrder())),
				nulls
		);
	}

	/**
	 * A block's members in wire order, each group's and var-data's methods after
	 * it.
	 */
	private Body body(Join.Block block) {
		List<Member> wireOrder = new ArrayList<>();
		for (Join.Field field : block.fields()) {
			Member member = member(field);
			if (member != null) {
				wireOrder.add(member);
			}
		}
		for (Join.Group group : block.groups()) {
			wireOrder.add(group(group, block));
		}
		for (Join.Data data : block.data()) {
			wireOrder.add(data(data, block));
		}
		return new Body(block.encoder(), block.decoder(), wireOrder, constructorOrder(block.constructorOrder()));
	}

	private List<Member> constructorOrder(List<? extends Join.Node> nodes) {
		List<Member> order = new ArrayList<>();
		for (Join.Node node : nodes) {
			order.add(joined(node));
		}
		return order;
	}

	/**
	 * A field or member as the record meets it, after the helpers its shape calls;
	 * null where it meets none, a constant no component carries.
	 */
	private @Nullable Member member(Join.Field field) {
		Member known = members.get(field);
		if (known != null) {
			return known;
		}
		Face face = field.face();
		if (face == null) {
			return null;
		}
		Member member = switch (face) {
			case Face.Mapped mapped -> {
				Join.Composite composite = field.composite();
				if (composite != null) {
					pair(composite);
				}
				field.helpers().forEach(this::leaf);
				Faces.Bound bound = mapped.bound();
				yield new Member.Field(
						mapped.component(), field.property(), mapped.shape(), mapped.absence(),
						bound == null ? null : bound.binding(), bound == null ? null : bound.context()
				);
			}
			case Face.Null unmapped -> new Member.Unmapped(field.property(), unmapped.shape());
		};
		members.put(field, member);
		return member;
	}

	/**
	 * A composite type's pair, declared once, after the helpers its members call.
	 */
	private void pair(Join.Composite composite) {
		if (!pairs.add(composite)) {
			return;
		}
		List<Member> wireOrder = new ArrayList<>();
		for (Join.Field field : composite.members()) {
			Member member = member(field);
			if (member != null) {
				wireOrder.add(member);
			}
		}
		Body body = new Body(
				composite.encoder(), composite.decoder(), wireOrder, constructorOrder(composite.constructorOrder())
		);
		helpers.add(new Helper.CompositePair(composite.name(), composite.record(), body));
	}

	private void leaf(Faces.Helper helper) {
		if (leaves.add(helper)) {
			helpers.add(new Helper.Leaf(helper));
		}
	}

	private Member.Group group(Join.Group group, Join.Block parent) {
		Annotated.Group component = mapped(group.component(), group.property());
		Faces.Bound bound = group.bound();
		Member.Group member = new Member.Group(
				component.javaName(), group.property(), group.path(), mapped(group.record(), group.property()),
				body(group.entry()), group.addedSince(), bound == null ? null : bound.binding(),
				bound == null ? null : bound.context()
		);
		helpers.add(new Helper.GroupMethods(member, parent.encoder()));
		members.put(group, member);
		return member;
	}

	private Member.Data data(Join.Data data, Join.Block parent) {
		Annotated.Data component = mapped(data.component(), data.property());
		data.helpers().forEach(this::leaf);
		Faces.Bound bound = data.bound();
		Member.Data member = new Member.Data(
				component.javaName(), data.property(), data.path(), mapped(data.content(), data.property()),
				data.charset(), data.addedSince(), bound == null ? null : bound.binding(),
				bound == null ? null : bound.context()
		);
		helpers.add(
				new Helper.DataMethods(
						member, parent.encoder(), parent.decoder(), Generators.toUpperFirstChar(data.property()),
						data.lengthEncoder()
				)
		);
		members.put(data, member);
		return member;
	}

	/** The member a node the record maps became, built before it is asked for. */
	private Member joined(Join.Node node) {
		Member member = members.get(node);
		if (member == null) {
			throw new IllegalStateException("a node the record maps with no member");
		}
		return member;
	}

	/**
	 * What only a record gives a node, which every node of a codec's message has.
	 */
	private static <T> T mapped(@Nullable T value, String property) {
		if (value == null) {
			throw new IllegalStateException(property + " is joined without a record");
		}
		return value;
	}
}
