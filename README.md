# sbe-buddy

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
    @SbeField(id = 3, primitiveType = UINT16) int venue,
    @SbeField(id = 4) Side side) {}

@SbeEnum(primitiveType = UINT8)
public enum Side {
    @SbeEnumValue("0") BUY,
    @SbeEnumValue("1") SELL
}
```

Compile it and sbe-buddy generates the normal SBE flyweights:

```
com.example.trading.sbe.PlaceOrderEncoder
com.example.trading.sbe.PlaceOrderDecoder
```

They come from SBE's own `JavaGenerator`.

sbe-buddy turns the annotated Java model into an SBE XML schema and hands
that schema to sbe-tool. The generated XML is also packaged as
`com/example/trading/schema.xml`, so the same schema can be used with SBE's
C, C++, C# and Rust generators.

## Why

SBE's flyweights are exactly what you want when allocations matter: they
work directly on the encoded buffer and add very little between your code
and the wire.

What tends to become awkward is describing the same messages again in the
Java model used by the rest of the application.

sbe-buddy makes the Java declaration the schema while leaving SBE's runtime
model alone.

It is not another SBE implementation. It is another way to write an SBE
schema.

## Explicit where it matters

Wire details stay explicit.

```java
public record Order(
    @SbeField(id = 1) long orderId,
    @SbeField(id = 2, primitiveType = UINT16) int venue) {}
```

A bare Java primitive gets the corresponding signed SBE type, so `long`
means `int64`. Anything that changes the wire contract is written down
explicitly: ids, unsigned types, `char`, named types and schema versions.

Enums work the same way:

```java
@SbeEnum(primitiveType = UINT8)
public enum Side {
    @SbeEnumValue("0") BUY,
    @SbeEnumValue("1") SELL
}
```

Invalid schemas fail during compilation at the declaration that caused the
problem.

There is no auto-numbering and no lockfile.

## SBE stays SBE

The Java model follows SBE's schema model rather than inventing a different
one.

Messages, fields, named types, composites, refs, enums, sets, groups and
var-data all map to their corresponding SBE concepts.

Named types remain named types. Groups remain groups. Schema evolution
remains SBE schema evolution.

The processor produces XML and then steps aside. sbe-tool validates it,
builds the IR and generates the flyweights.

That is important: offsets, null values, block lengths and the generated
encoder/decoder API still belong to SBE.

## Generated code

The output is the normal SBE API:

```java
MutableDirectBuffer buffer = new UnsafeBuffer(new byte[256]);
PlaceOrderEncoder encoder = new PlaceOrderEncoder();
encoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderEncoder())
    .accountId(42)
    .quantity(100)
    .venue(7)
    .side(Side.BUY);
```

And on the other side:

```java
PlaceOrderDecoder decoder = new PlaceOrderDecoder();
decoder.wrapAndApplyHeader(buffer, 0, new MessageHeaderDecoder());
long accountId = decoder.accountId();
int quantity = decoder.quantity();
```

There is no wrapper around the flyweights today.

## Current scope

sbe-buddy currently supports the SBE schema constructs covered by
`sbe.xsd`, including:

* primitives, unsigned types and `char`
* fixed-length arrays and named types
* composites and refs
* enums and sets
* groups and var-data
* constants and optional presence
* schema evolution with `sinceVersion`, `deprecated` and `semanticVersion`
* byte order and custom header types

The generated XML is checked against hand-written reference schemas and
then passed through sbe-tool.

The generated codec, the mapping layer between your records and the
flyweights, currently covers messages of primitive fields. A schema that
uses more sets `codecs = false` on `@SbeSchema` and gets the flyweights
alone.

That is the next part: the codec for every construct, domain-type
bindings and, later, typed flyweights for places where records are
convenient but raw SBE is lower-level than needed.

See [docs/intent.md](docs/intent.md) for the direction of the project.

## Setup

Requires JDK 21.

Add `sbe-buddy-api` to the compile classpath and `sbe-buddy-processor` to
javac's annotation processor path.

Nothing is published yet, so install it locally first:

```
./mvnw install
```

Then:

```xml
<dependency>
  <groupId>net.concini</groupId>
  <artifactId>sbe-buddy-api</artifactId>
  <version>0.1.0</version>
</dependency>
```

```xml
<plugin>
  <artifactId>maven-compiler-plugin</artifactId>
  <configuration>
    <annotationProcessorPaths>
      <path>
        <groupId>net.concini</groupId>
        <artifactId>sbe-buddy-processor</artifactId>
        <version>0.1.0</version>
      </path>
    </annotationProcessorPaths>
  </configuration>
</plugin>
```

Compiling needs no additional JVM flags.

Running code that uses Agrona buffers requires:

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
