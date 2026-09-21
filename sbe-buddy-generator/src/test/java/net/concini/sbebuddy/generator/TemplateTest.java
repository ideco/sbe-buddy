package net.concini.sbebuddy.generator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

final class TemplateTest {

	@Test
	void fillsEachPlaceholderWithItsValue() {
		Template template = Template.of("class {name} extends {parent} implements {name}Marker {}");

		assertThat(template.fill("name", "Order", "parent", "Message"))
				.isEqualTo("class Order extends Message implements OrderMarker {}");
	}

	@Test
	void indentsAValueThatSpansLinesToItsPlaceholdersColumn() {
		Template template = Template.of("""
				void run() {
					{body}
				}
				""");

		assertThat(template.fill("body", "first();\nsecond();")).isEqualTo("""
				void run() {
					first();
					second();
				}
				""");
	}

	@Test
	void aValueAfterOtherTextOnItsLineIsNotIndented() {
		Template template = Template.of("\tnew Order({arguments});");

		assertThat(template.fill("arguments", "a,\nb")).isEqualTo("\tnew Order(a,\nb);");
	}

	@Test
	void aPlaceholderAloneOnItsLineFilledEmptyTakesTheLineWithIt() {
		Template template = Template.of("""
				void run() {
					{check}
					first();
				}
				""");

		assertThat(template.fill("check", "")).isEqualTo("""
				void run() {
					first();
				}
				""");
	}

	@Test
	void aPlaceholderBesideOtherTextFilledEmptyLeavesItsLine() {
		Template template = Template.of("run({arguments});\n");

		assertThat(template.fill("arguments", "")).isEqualTo("run();\n");
	}

	@Test
	void aPlaceholderLeftUnfilledIsAMistake() {
		Template template = Template.of("{codec} implements Codec<{record}>");

		assertThatThrownBy(() -> template.fill("codec", "OrderCodec"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("{record} is not filled");
	}

	@Test
	void aValueNeverUsedIsAMistake() {
		Template template = Template.of("{codec}");

		assertThatThrownBy(() -> template.fill("codec", "OrderCodec", "record", "Order"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("[record] are not placeholders of this template");
	}

	@Test
	void aNameGivenTwiceIsAMistake() {
		Template template = Template.of("{codec}");

		assertThatThrownBy(() -> template.fill("codec", "OrderCodec", "codec", "Other"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("codec is given twice");
	}

	@Test
	void namesAndValuesComeInPairs() {
		Template template = Template.of("{codec}");

		assertThatThrownBy(() -> template.fill("codec"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("names and values alternate");
	}
}
