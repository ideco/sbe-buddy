# sbe-buddy

> [!IMPORTANT]
> **Zen garden disclaimer:** This is my private playground for SBE ideas and, just as importantly, for experimenting with agentic coding workflows. Expect things to change as I learn. Stability, compatibility, and a polished roadmap are not the point.

Code-first Simple Binary Encoding for Java.

Define an SBE schema as annotated Java:

```java
// package-info.java
@SbeSchema(id = 100, version = 0)
package com.example.trading;
```

```java
@SbeMessage(id = 1)
public record PlaceOrder(
    @SbeField(id = 1) long accountId,
    @SbeField(id = 2) int quantity,
    @SbeField(id = 3, primitiveType = UINT16) int venue) {}
```

Compile it and sbe-buddy generates the normal SBE flyweights:

```
com.example.trading.sbe.PlaceOrderEncoder
com.example.trading.sbe.PlaceOrderDecoder
```

They come from SBE's own `JavaGenerator`.

For messages supported by the codec generator, it also generates a mapping
between the Java record and those flyweights:

```
com.example.trading.PlaceOrderCodec
```

```java
PlaceOrderCodec codec = new PlaceOrderCodec();
MutableDirectBuffer buffer = new UnsafeBuffer(new byte[256]);

PlaceOrder order = new PlaceOrder(42, 100, 7);
int length = codec.encode(order, buffer, 0);
PlaceOrder decoded = codec.decode(buffer, 0);
```

The flyweights are still there when the lower-level API is the better fit.

## What it does

sbe-buddy turns the annotated Java model into an SBE XML schema and passes
that schema to sbe-tool.

From there, SBE does the usual work:

```
annotated Java
      |
      v
  SBE schema
      |
      v
   sbe-tool
      |
      +--> IR
      |
      +--> standard SBE flyweights
      |
      +--> sbe-buddy codecs
```

The generated XML is also packaged as:

```
com/example/trading/schema.xml
```

It can therefore be used with the other SBE generators as well.

sbe-buddy is not another SBE implementation. It is a way to describe an SBE
schema from Java, with an optional generated mapping layer back to the Java
model.

## Why

SBE's generated flyweights are deliberately close to the wire. They operate
directly on the encoded buffer and expose the structure of the SBE schema.

That is useful at the transport boundary, but it often means describing the
same messages again in the application's Java model:

```
trading.xml
    |
    +--> PlaceOrderEncoder / PlaceOrderDecoder

PlaceOrder record
    |
    +--> hand-written mapping
```

sbe-buddy starts from the Java declaration instead:

```
PlaceOrder record
    |
    +--> SBE schema
    |
    +--> PlaceOrderEncoder / PlaceOrderDecoder
    |
    +--> PlaceOrderCodec
```

The record describes the message used by the application. The generated SBE
schema remains the definition of its wire representation.

## Explicit where it matters

Wire details stay explicit.

```java
public record Order(
    @SbeField(id = 1) long orderId,
    @SbeField(id = 2, primitiveType = UINT16) int venue) {}
```

A bare Java primitive gets its corresponding signed SBE primitive type, so
`long` becomes `int64`.

Anything that changes the wire contract is written down explicitly: field
ids, unsigned types, `char`, named types and schema versions.

Enums follow the same model:

```java
@SbeEnum(primitiveType = UINT8)
public enum Side {
    @SbeEnumValue("0") BUY,
    @SbeEnumValue("1") SELL
}
```

There is no auto-numbering and no lockfile.

Invalid schemas fail during compilation, preferably at the Java declaration
that caused the problem. The generated schema is then validated by sbe-tool
as well.

## SBE stays SBE

The Java model follows SBE's schema model rather than replacing it with a
different one.

Messages, fields, named types, composites, refs, enums, sets, groups and
var-data map to the corresponding SBE concepts.

Named types remain named types. Groups remain groups. Schema evolution
remains SBE schema evolution.

The processor produces the schema and then uses sbe-tool to parse it, build
the IR and generate the standard Java flyweights.

Offsets, block lengths, null values and the generated encoder/decoder API
therefore remain SBE's.

## Generated codecs

The generated codec is an additional API for code that wants to work with
the annotated record rather than directly with the flyweights.

```java
Codec<PlaceOrder> codec = new PlaceOrderCodec();

int encodedLength = codec.encodedLength(order);
int written = codec.encode(order, buffer, offset);

PlaceOrder decoded = codec.decode(buffer, offset);
int consumed = codec.lastDecodedLength();
```

A codec owns its encoder and decoder flyweights and is stateful. Instances
are intended to be reused by one thread rather than shared between threads.

The codec checks the SBE message header when decoding and uses the acting
block length and version from the encoded message.

Codec generation is still incomplete. It currently covers messages made up
of primitive fields.

Schemas using constructs not yet supported by the codec generator can
disable codecs:

```java
@SbeSchema(id = 100, version = 0, codecs = false)
package com.example.trading;
```

The schema and standard SBE flyweights are still generated.

## Schema model

The schema side currently covers the SBE constructs represented by
`sbe.xsd`, including:

* primitives, unsigned types and `char`
* fixed-length arrays and named types
* composites and refs
* enums and sets
* groups and var-data
* constants and optional presence
* `sinceVersion`, `deprecated` and `semanticVersion`
* byte order and custom header types

The generated XML is checked against hand-written reference schemas in the
test suite and is then parsed by sbe-tool.

Codec support is deliberately narrower than schema support for now.

## Setup

Requires JDK 21 or newer.

Nothing is published yet, so install it locally first:

```
git clone --recurse-submodules https://github.com/ideco/sbe-buddy.git
cd sbe-buddy
./mvnw install
```

Add `sbe-buddy-api` to the compile classpath:

```xml
<dependency>
    <groupId>net.concini</groupId>
    <artifactId>sbe-buddy-api</artifactId>
    <version>0.2.0-SNAPSHOT</version>
</dependency>
```

and `sbe-buddy-processor` to javac's annotation processor path:

```xml
<plugin>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>net.concini</groupId>
                <artifactId>sbe-buddy-processor</artifactId>
                <version>0.2.0-SNAPSHOT</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

Compilation requires no additional JVM flags.

Code that uses Agrona buffers requires:

```
--add-opens java.base/jdk.internal.misc=ALL-UNNAMED
```

That includes test JVMs.

## Building

```
git clone --recurse-submodules https://github.com/ideco/sbe-buddy.git
cd sbe-buddy
./mvnw verify
```

JDK 21 is enough. The Maven wrapper handles Maven itself.

## Documentation

* [Intent](docs/intent.md): direction and scope
* [Type mappings](docs/type-mappings.md): Java representation of the SBE schema model
* [Architecture](docs/architecture.md): modules and processor pipeline
* [Notes](docs/notes.md): verified behaviour of sbe-tool, javac and Agrona

## License

MIT. See [LICENSE](LICENSE).
