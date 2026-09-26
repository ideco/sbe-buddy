package net.concini.sbebuddy.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.TestFactory;

import uk.co.real_logic.sbe.generation.java.JavaUtil;
import uk.co.real_logic.sbe.ir.Ir;
import uk.co.real_logic.sbe.ir.Signal;
import uk.co.real_logic.sbe.ir.Token;
import uk.co.real_logic.sbe.otf.AbstractTokenListener;
import uk.co.real_logic.sbe.otf.OtfHeaderDecoder;
import uk.co.real_logic.sbe.otf.OtfMessageDecoder;
import uk.co.real_logic.sbe.xml.IrGenerator;
import uk.co.real_logic.sbe.xml.ParserOptions;
import uk.co.real_logic.sbe.xml.XmlSchemaParser;

import net.concini.sbebuddy.Codec;
import net.concini.sbebuddy.tests.SchemaCase.RoundTrip;

/**
 * Every generated reader against sbe-tool's {@code OtfMessageDecoder}, which
 * walks a message from the IR in the order the reader's stages come: over every
 * round trip of every case, the stages the reader hands out, with each header's
 * count and each var-data's length, are the root block, group headers, entries
 * and var-data the OTF walk meets in the same bytes, and the length the reader
 * measures is the codec's. Skipping every group's header as it comes leaves the
 * same walk with each group's entries, and all they hold, taken out; skipping
 * every entry, with what each entry holds past its block. The reader is found
 * by name, as nothing else names it here.
 */
final class ReaderSequenceTest {

	private static final int OFFSET = 16;

	/** What the reader skips as the stages come. */
	private enum Skip {
		NOTHING, HEADERS, ENTRIES
	}

	@TestFactory
	@DisplayName("readers")
	Stream<DynamicContainer> everyRoundTrip() {
		return Cases.discover().stream().map(aCase -> {
			Ir ir = ir(aCase);
			return dynamicContainer(
					aCase.description(),
					aCase.roundTrips().stream().flatMap(
							roundTrip -> Stream.of(
									dynamicTest(
											roundTrip.description() + ": the reader's stages are the OTF walk's",
											() -> sequence(aCase, ir, roundTrip, Skip.NOTHING)
									),
									dynamicTest(
											roundTrip.description() + ": skipping each header prunes its entries",
											() -> sequence(aCase, ir, roundTrip, Skip.HEADERS)
									),
									dynamicTest(
											roundTrip.description() + ": skipping each entry prunes what it holds",
											() -> sequence(aCase, ir, roundTrip, Skip.ENTRIES)
									)
							)
					)
			);
		});
	}

	private static void sequence(SchemaCase aCase, Ir ir, RoundTrip<?> roundTrip, Skip skip)
			throws ReflectiveOperationException {
		Codec<?, ?> codec = roundTrip.codec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[OFFSET + encodedLength(roundTrip) + OFFSET]);
		int length = encode(roundTrip, buffer);
		int templateId = codec.decodeHeader(buffer, OFFSET).templateId();
		List<Token> tokens = ir.getMessage(templateId);
		assertThat(tokens).as("the schema's message %d", templateId).isNotNull();

		Object reader = Class.forName(
				aCase.getClass().getPackageName() + "." + JavaUtil.formatClassName(tokens.get(0).name())
						+ "Reader"
		).getConstructor().newInstance();
		invoke(reader, "wrap", new Class<?>[]{DirectBuffer.class, int.class}, buffer, OFFSET);
		List<String> stages = read(reader, kinds(tokens), skip);

		assertThat(stages).as("the reader's stages").isEqualTo(otf(ir, tokens, buffer, skip));
		assertThat(invoke(reader, "decodedLength", new Class<?>[0])).as("decodedLength").isEqualTo(length);
	}

	/**
	 * Each stage as its class's simple name, with a header's {@code count()} and a
	 * var-data's {@code length()}.
	 */
	private static List<String> read(Object reader, Map<String, Signal> kinds, Skip skip)
			throws ReflectiveOperationException {
		List<String> stages = new ArrayList<>();
		Iterator<?> iterator = (Iterator<?>) reader;
		while (iterator.hasNext()) {
			Object stage = iterator.next();
			String name = stage.getClass().getSimpleName();
			Signal kind = kinds.get(name);
			if (kind == Signal.BEGIN_GROUP) {
				stages.add(name + " " + invoke(stage, "count", new Class<?>[0]));
				if (skip == Skip.HEADERS) {
					invoke(stage, "skip", new Class<?>[0]);
				}
			} else if (kind == Signal.BEGIN_VAR_DATA) {
				stages.add(name + " " + invoke(stage, "length", new Class<?>[0]));
			} else {
				stages.add(name);
				if (skip == Skip.ENTRIES && name.endsWith("Entry")) {
					invoke(stage, "skip", new Class<?>[0]);
				}
			}
		}
		return stages;
	}

	/** What each stage class of the message is: a group's header, or var-data. */
	private static Map<String, Signal> kinds(List<Token> tokens) {
		Map<String, Signal> kinds = new HashMap<>();
		for (Token token : tokens) {
			if (token.signal() == Signal.BEGIN_GROUP || token.signal() == Signal.BEGIN_VAR_DATA) {
				kinds.put(JavaUtil.formatClassName(token.name()), token.signal());
			}
		}
		return kinds;
	}

	/**
	 * The OTF walk in the reader's terms. A group or var-data above the acting
	 * version is met with no entries or bytes by the walk and never comes from the
	 * reader, so it is left out; so is everything between a header and the end of
	 * its last entry where headers are skipped, and between the start and the end
	 * of an entry where entries are.
	 */
	private static List<String> otf(Ir ir, List<Token> tokens, DirectBuffer buffer, Skip skip) {
		OtfHeaderDecoder header = new OtfHeaderDecoder(ir.headerStructure());
		int actingVersion = header.getSchemaVersion(buffer, OFFSET);
		List<String> stages = new ArrayList<>();
		OtfMessageDecoder.decode(
				buffer, OFFSET + header.encodedLength(), actingVersion, header.getBlockLength(buffer, OFFSET), tokens,
				new AbstractTokenListener() {

					private Token skipping;

					private int skippingIndex;

					@Override
					public void onBeginMessage(Token token) {
						stages.add("RootBlock");
					}

					@Override
					public void onGroupHeader(Token token, int numInGroup) {
						if (skipping == null && token.version() <= actingVersion) {
							stages.add(JavaUtil.formatClassName(token.name()) + " " + numInGroup);
							if (skip == Skip.HEADERS && numInGroup > 0) {
								skipping = token;
								skippingIndex = numInGroup - 1;
							}
						}
					}

					@Override
					public void onBeginGroup(Token token, int groupIndex, int numInGroup) {
						if (skipping == null) {
							stages.add(JavaUtil.formatClassName(token.name()) + "Entry");
							if (skip == Skip.ENTRIES) {
								skipping = token;
								skippingIndex = groupIndex;
							}
						}
					}

					@Override
					public void onEndGroup(Token token, int groupIndex, int numInGroup) {
						if (token == skipping && groupIndex == skippingIndex) {
							skipping = null;
						}
					}

					@Override
					public void onVarData(
							Token fieldToken, DirectBuffer buffer, int bufferIndex, int length, Token typeToken
					) {
						if (skipping == null && fieldToken.version() <= actingVersion) {
							stages.add(JavaUtil.formatClassName(fieldToken.name()) + " " + length);
						}
					}
				}
		);
		return stages;
	}

	/**
	 * The case's schema as the processor wrote it into the jar, through sbe-tool.
	 */
	private static Ir ir(SchemaCase aCase) {
		String resource = "/" + aCase.getClass().getPackageName().replace('.', '/') + "/schema.xml";
		try (InputStream schema = aCase.getClass().getResourceAsStream(resource)) {
			assertThat(schema).as("the processor wrote %s", resource).isNotNull();
			return new IrGenerator().generate(XmlSchemaParser.parse(schema, ParserOptions.DEFAULT));
		} catch (Exception e) {
			throw new IllegalStateException(resource, e);
		}
	}

	private static <T> int encodedLength(RoundTrip<T> roundTrip) {
		return roundTrip.codec().encodedLength(roundTrip.value());
	}

	private static <T> int encode(RoundTrip<T> roundTrip, UnsafeBuffer buffer) {
		return roundTrip.codec().encode(roundTrip.value(), buffer, OFFSET);
	}

	private static Object invoke(Object target, String name, Class<?>[] types, Object... arguments)
			throws ReflectiveOperationException {
		Method method = target.getClass().getMethod(name, types);
		try {
			return method.invoke(target, arguments);
		} catch (InvocationTargetException e) {
			if (e.getCause() instanceof RuntimeException runtime) {
				throw runtime;
			}
			throw e;
		}
	}
}
