package corpus.flyweightsonly;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;

import net.concini.sbebuddy.tests.SchemaCase;

import corpus.flyweightsonly.sbe.MessageHeaderDecoder;
import corpus.flyweightsonly.sbe.MessageHeaderEncoder;
import corpus.flyweightsonly.sbe.PingDecoder;
import corpus.flyweightsonly.sbe.PingEncoder;
import corpus.flyweightsonly.sbe.PingReader;
import corpus.flyweightsonly.sbe.PongEncoder;
import corpus.flyweightsonly.sbe.PongReader;

/**
 * A schema with no record: its package is a {@code package-info.java} naming
 * the resource and a baseline, which the compile held it against, and what it
 * gets is sbe-tool's flyweights and a reader over them, for every message.
 */
final class FlyweightsOnlyTest implements SchemaCase {

	static final String ORACLE = """
			<?xml version="1.0" encoding="UTF-8"?>
			<sbe:messageSchema xmlns:sbe="http://fixprotocol.io/2016/sbe" package="corpus.flyweightsonly" id="1" version="1">
			    <types>
			        <composite name="messageHeader">
			            <type name="blockLength" primitiveType="uint16"/>
			            <type name="templateId" primitiveType="uint16"/>
			            <type name="schemaId" primitiveType="uint16"/>
			            <type name="version" primitiveType="uint16"/>
			        </composite>
			    </types>
			    <sbe:message name="Ping" id="1">
			        <field name="sequence" id="1" type="uint64"/>
			        <field name="sentAt" id="2" type="int64" sinceVersion="1"/>
			    </sbe:message>
			    <sbe:message name="Pong" id="2" sinceVersion="1">
			        <field name="sequence" id="1" type="uint64"/>
			    </sbe:message>
			</sbe:messageSchema>
			""";

	@Override
	public String description() {
		return "FlyweightsOnly: a resource and a baseline, no record, flyweights alone";
	}

	@Override
	public String oracle() {
		return ORACLE;
	}

	@Override
	public List<RoundTrip<?>> roundTrips() {
		return List.of();
	}

	@Test
	void everyMessageHasItsFlyweights() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		new PingEncoder().wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder()).sequence(3L).sentAt(-1L);

		PingDecoder ping = new PingDecoder().wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());

		assertThat(ping.sequence()).isEqualTo(3L);
		assertThat(ping.sentAt()).isEqualTo(-1L);
		assertThat(PingEncoder.SCHEMA_VERSION).isEqualTo(1);
		assertThat(PongEncoder.TEMPLATE_ID).isEqualTo(2);
	}

	@Test
	void everyMessageHasAReader() {
		UnsafeBuffer buffer = new UnsafeBuffer(new byte[64]);
		new PingEncoder().wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder()).sequence(3L).sentAt(-1L);
		PingReader reader = new PingReader().wrap(buffer, 0);

		PingReader.RootBlock block = (PingReader.RootBlock) reader.next();

		assertThat(block.sequence()).isEqualTo(3L);
		assertThat(block.sentAt()).isEqualTo(-1L);
		assertThat(reader.hasNext()).isFalse();
		assertThat(reader.decodedLength()).isEqualTo(MessageHeaderEncoder.ENCODED_LENGTH + PingEncoder.BLOCK_LENGTH);
		new PongEncoder().wrapAndApplyHeader(buffer, 32, new MessageHeaderEncoder()).sequence(4L);
		assertThat(((PongReader.RootBlock) new PongReader().wrap(buffer, 32).next()).sequence()).isEqualTo(4L);
	}

	@Test
	void noMessageHasACodec() {
		for (String codec : List.of("PingCodec", "PongCodec")) {
			assertThatThrownBy(() -> Class.forName("corpus.flyweightsonly." + codec))
					.isInstanceOf(ClassNotFoundException.class);
		}
	}
}
