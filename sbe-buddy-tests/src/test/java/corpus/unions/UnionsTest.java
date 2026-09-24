package corpus.unions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.Codec;
import net.concini.sbebuddy.DefaultMessageHeader;
import net.concini.sbebuddy.tests.SchemaCase;

import corpus.unions.OrderCommand.CancelOrder;
import corpus.unions.OrderCommand.PlaceOrder;
import corpus.unions.sbe.CancelOrderEncoder;
import corpus.unions.sbe.MessageHeaderEncoder;

/**
 * Unions over four messages: {@code Everything} over {@code Ingress} and
 * {@code Audited}, {@code Ingress} over the union {@code OrderCommand} and the
 * plain sealed {@code SessionCommand}, and {@code Audited} across both
 * branches, so {@code PlaceOrder} and {@code Logon} are in three unions and
 * reached twice by {@code Everything}. The unions add nothing to the schema.
 */
final class UnionsTest implements SchemaCase {

	static final String ORACLE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.unions" id="6" version="0">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			    </types>
			    <sbe:message name="PlaceOrder" id="1">
			        <field name="orderId" id="1" type="int64"/>
			        <field name="quantity" id="2" type="int32"/>
			    </sbe:message>
			    <sbe:message name="CancelOrder" id="2">
			        <field name="orderId" id="1" type="int64"/>
			    </sbe:message>
			    <sbe:message name="Logon" id="3">
			        <field name="sessionId" id="1" type="int32"/>
			    </sbe:message>
			    <sbe:message name="Logout" id="4">
			        <field name="sessionId" id="1" type="int32"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	private static final int OFFSET = 8;

	private static final PlaceOrder PLACE_ORDER = new PlaceOrder(42L, 100);

	private static final CancelOrder CANCEL_ORDER = new CancelOrder(42L);

	private static final Logon LOGON = new Logon(7);

	private static final Logout LOGOUT = new Logout(7);

	@Override
	public String description() {
		return "Unions: a union of unions, a sealed interface flattened between them, "
				+ "a message in three unions and one reached twice";
	}

	@Override
	public String oracle() {
		return ORACLE;
	}

	@Override
	public List<RoundTrip<?>> roundTrips() {
		return List.of(
				new RoundTrip<>("a place order through its own codec", new PlaceOrderCodec(), PLACE_ORDER),
				new RoundTrip<>("a cancel order through its own codec", new CancelOrderCodec(), CANCEL_ORDER),
				new RoundTrip<>("a logon through its own codec", new LogonCodec(), LOGON),
				new RoundTrip<>("a logout through its own codec", new LogoutCodec(), LOGOUT),
				new RoundTrip<>("a place order through its union", new OrderCommandCodec(), PLACE_ORDER),
				new RoundTrip<>("a cancel order through its union", new OrderCommandCodec(), CANCEL_ORDER),
				new RoundTrip<>("a place order through the union above its union", new IngressCodec(), PLACE_ORDER),
				new RoundTrip<>("a cancel order through the union above its union", new IngressCodec(), CANCEL_ORDER),
				new RoundTrip<>("a logon through a union, its interface flattened", new IngressCodec(), LOGON),
				new RoundTrip<>("a logout through a union, its interface flattened", new IngressCodec(), LOGOUT),
				new RoundTrip<>("a place order through a second union", new AuditedCodec(), PLACE_ORDER),
				new RoundTrip<>("a logon through a second union", new AuditedCodec(), LOGON),
				new RoundTrip<>(
						"a place order through the union reaching it twice", new EverythingCodec(), PLACE_ORDER
				),
				new RoundTrip<>("a cancel order through the union of unions", new EverythingCodec(), CANCEL_ORDER),
				new RoundTrip<>("a logon through the union reaching it twice", new EverythingCodec(), LOGON),
				new RoundTrip<>("a logout through the union of unions", new EverythingCodec(), LOGOUT)
		);
	}

	@Test
	void aCallerSwitchesOverANestedUnionAsOneCase() {
		List<String> handled = List.of(PLACE_ORDER, CANCEL_ORDER, LOGON, LOGOUT).stream()
				.map(message -> handle(read(new IngressCodec(), message)))
				.toList();

		assertThat(handled).containsExactly("order 42", "order 42", "logon 7", "logout 7");
	}

	@Test
	void aUnionEncodesExactlyWhatItsMemberCodecDoes() {
		UnsafeBuffer byUnion = new UnsafeBuffer(new byte[64]);
		UnsafeBuffer byMember = new UnsafeBuffer(new byte[64]);

		int length = new EverythingCodec().encode(LOGON, byUnion, OFFSET);

		assertThat(new LogonCodec().encode(LOGON, byMember, OFFSET)).isEqualTo(length);
		assertThat(byUnion.byteArray()).isEqualTo(byMember.byteArray());
	}

	@Test
	void aUnionDecodesAMessageItsMemberCodecWrote() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		new LogoutCodec().encode(LOGOUT, buffer, OFFSET);

		assertThat(new IngressCodec().decode(buffer, OFFSET)).isEqualTo(LOGOUT);
	}

	@Test
	void aUnionEncodesTheHeaderPassedIn() {
		IngressCodec codec = new IngressCodec();
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		codec.encode(CANCEL_ORDER, new DefaultMessageHeader(0, 0, 0, 0), buffer, OFFSET);

		assertThat(codec.decodeHeader(buffer, OFFSET)).isEqualTo(
				new DefaultMessageHeader(
						CancelOrderEncoder.BLOCK_LENGTH,
						CancelOrderEncoder.TEMPLATE_ID,
						CancelOrderEncoder.SCHEMA_ID,
						CancelOrderEncoder.SCHEMA_VERSION
				)
		);
	}

	@Test
	void eachUnionTakesOnlyItsOwnTemplates() {
		assertThat(canDecode(new OrderCommandCodec())).containsExactly(true, true, false, false);
		assertThat(canDecode(new AuditedCodec())).containsExactly(true, false, true, false);
		assertThat(canDecode(new IngressCodec())).containsExactly(true, true, true, true);
		assertThat(canDecode(new EverythingCodec())).containsExactly(true, true, true, true);
		assertThat(canDecode(new LogonCodec())).containsExactly(false, false, true, false);
	}

	@Test
	void aUnionRefusesAnotherTemplateNamingItsOwn() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		new LogonCodec().encode(LOGON, buffer, OFFSET);
		OrderCommandCodec codec = new OrderCommandCodec();

		assertThatThrownBy(() -> codec.decode(buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("not a OrderCommand: schemaId 6, templateId 3; its templates are 1, 2");
		assertThatThrownBy(() -> codec.decodedLength(buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("not a OrderCommand: schemaId 6, templateId 3; its templates are 1, 2");
	}

	@Test
	void aUnionRefusesAnotherSchema() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		new PlaceOrderCodec().encode(PLACE_ORDER, buffer, OFFSET);
		buffer.putShort(OFFSET + MessageHeaderEncoder.schemaIdEncodingOffset(), (short) 7);
		EverythingCodec codec = new EverythingCodec();

		assertThat(codec.canDecode(buffer, OFFSET)).isFalse();
		assertThatThrownBy(() -> codec.decode(buffer, OFFSET))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("not a Everything: schemaId 7, templateId 1; its templates are 1, 2, 3, 4");
	}

	@Test
	void aUnionReportsTheLengthOfTheMessageItLastDecoded() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		IngressCodec codec = new IngressCodec();
		int placeOrder = codec.encode(PLACE_ORDER, buffer, OFFSET);
		codec.decode(buffer, OFFSET);
		assertThat(codec.lastDecodedLength()).isEqualTo(placeOrder);

		int logout = codec.encode(LOGOUT, buffer, OFFSET);
		codec.decode(buffer, OFFSET);
		assertThat(codec.lastDecodedLength()).isEqualTo(logout).isNotEqualTo(placeOrder);
	}

	@Test
	void everyCodecRefusesANullValue() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		List<Codec<?, DefaultMessageHeader>> codecs = List.of(new PlaceOrderCodec(), new EverythingCodec());

		for (Codec<?, DefaultMessageHeader> codec : codecs) {
			Codec<Object, DefaultMessageHeader> any = unchecked(codec);
			assertThatThrownBy(() -> any.encodedLength(null)).hasMessage("value is required");
			assertThatThrownBy(() -> any.encode(null, buffer, OFFSET)).hasMessage("value is required");
			assertThatThrownBy(() -> any.encode(null, new DefaultMessageHeader(0, 0, 0, 0), buffer, OFFSET))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessage("value is required");
		}
	}

	/**
	 * The exhaustive switch a caller writes: {@code OrderCommand} is one case,
	 * which javac checks covers its messages through the hierarchy.
	 */
	private static String handle(Ingress ingress) {
		return switch (ingress) {
			case OrderCommand order -> "order " + order.orderId();
			case Logon logon -> "logon " + logon.sessionId();
			case Logout logout -> "logout " + logout.sessionId();
		};
	}

	private static <T> T read(Codec<T, ?> codec, T value) {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		codec.encode(value, buffer, OFFSET);
		return codec.decode(buffer, OFFSET);
	}

	/** Whether the codec takes each of the four messages, in template id order. */
	private static List<Boolean> canDecode(Codec<?, ?> codec) {
		EverythingCodec writer = new EverythingCodec();
		return List.<Everything>of(PLACE_ORDER, CANCEL_ORDER, LOGON, LOGOUT).stream().map(message -> {
			UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
			writer.encode(message, buffer, OFFSET);
			return codec.canDecode(buffer, OFFSET);
		}).toList();
	}

	@SuppressWarnings("unchecked")
	private static Codec<Object, DefaultMessageHeader> unchecked(Codec<?, DefaultMessageHeader> codec) {
		return (Codec<Object, DefaultMessageHeader>) codec;
	}
}
