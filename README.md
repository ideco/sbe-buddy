# sbe-buddy

Can annotated Java provide a faithful code-first representation of SBE without
hiding SBE itself? sbe-buddy is an attempt at that.

It describes an SBE schema with annotated Java records. From those it writes
the schema XML, runs sbe-tool to generate the usual flyweights, and generates
a codec per message that maps between the record and the flyweights.

The aim is that any SBE schema can be written this way. The annotations follow
the SBE XSD element for element. The test suite checks that every XSD element
and attribute used by sbe-tool is represented by the schemas it compiles.

The same holds in the other direction. An existing XML schema can be mapped as
it is rather than rewritten into a shape the annotations prefer, and the
compiler checks that the records and XML describe the same schema.

The flyweights work directly on the buffer. The codecs are for code that wants
to handle a message as an immutable value and would otherwise map to and from
the flyweights by hand.

The XML is written out and packaged with the classes, so other tools and other
languages can use it. The flyweights are sbe-tool's, generated in the normal
way, and remain available alongside the codecs.

## An example

A message from the example project, which models order entry loosely after
FIX. The package declares the schema:

```java
@SbeSchema(id = 91, version = 0)
package com.example.trading;
```

and each message is a record:

```java
@SbeMessage(id = 4, semanticType = "8")
public record ExecutionReport(
        @SbeField(id = 37, type = OrderId.class) String orderId,
        @SbeField(id = 150) ExecType execType,
        @SbeField(id = 31, type = PriceEncoding.class, presence = OPTIONAL, binding = PriceBinding.class) @Nullable BigDecimal lastPx,
        @SbeField(id = 60, type = UtcTimestamp.class, binding = UtcTimestampBinding.class) Instant transactTime,
        @SbeGroup(id = 1362, binding = FillsBinding.class) Map<String, Fill> fills
        /* ... */) implements OrderEvent {}
```

```java
ExecutionReportCodec codec = new ExecutionReportCodec();
int length = codec.encode(report, buffer, offset);
ExecutionReport decoded = codec.decode(buffer, offset);
```

The flyweights for the same message are generated as well:

```java
ExecutionReportDecoder decoder = new ExecutionReportDecoder()
        .wrapAndApplyHeader(buffer, offset, new SessionHeaderDecoder());

long mantissa = decoder.lastPx().mantissa();
for (ExecutionReportDecoder.FillsDecoder fill : decoder.fills()) {
    // ...
}
```

## What the codec does

The codec reads and writes the record through the flyweights. Optional fields,
and fields an older message doesn't have, come back as `null`. An optional
composite, set or array has no null value on the wire, so it only comes back
as `null` when a binding gives it one. Enums are the record's own Java enums,
mapped by the values in the schema, with an optional fallback constant for
values the enum doesn't know. Composites are records, groups are lists, and
var-data is a `String` or a `byte[]`.

When the Java type wanted for a field isn't the one the wire carries, a
binding converts between the two. The example uses bindings for prices as
`BigDecimal`, timestamps as `Instant`, and a group as a `Map` keyed by fill
id. A binding gets the field's schema attributes, such as presence, epoch and
time unit, so one binding class can serve several fields.

Messages can be grouped under a sealed interface. The interface gets a codec
of its own that dispatches on the template id:

```java
switch (new OrderEventCodec().decode(buffer, offset)) {
    case ExecutionReport report -> onReport(report);
    case CancelReject reject -> onCancelReject(reject);
    case Reject reject -> onReject(reject);
}
```

Versioning is SBE's: `sinceVersion` on fields, appended in order. The
declaration states it and the compiler checks it. A field an older message
can lack is nullable in the record. A released version's XML can be checked
in and named as the `baseline`: the compiler holds the schema against it
under SBE's extension rules, and the codecs read from its version.

## Reading and writing in place

Beside sbe-tool's flyweights, every message gets a reader over them that
hands the message out as a sequence of stages in wire order, so a group or
var-data can no longer be read out of turn, and one left unread is passed
over correctly:

```java
for (ExecutionReportReader.Stage stage : reader.wrap(buffer, offset)) {
    switch (stage) {
        case ExecutionReportReader.FillsEntry fill -> fillIds.add(fill.fillExecId());
        default -> {}
    }
}
```

Every message gets a writer too, a chain whose stages offer only the next
step, so a message written out of order, or left incomplete, does not
compile:

```java
int length = writer.wrap(buffer, offset)
        .orderId(42L)
        .legs()
            .entry().legId(1).allocations().end()
        .end()
        .length();
```

Readers and writers need no record, so a message the records leave out gets
them too.

## Staying close to SBE

What a schema states remains explicit:

* layout independent of the order of the record's components;
* explicit offsets and block lengths;
* retired fields kept on the wire as `unmapped`;
* custom message headers and group dimension types;
* var-data in any encoding, constants, and big-endian schemas.

Ids are always written out; nothing is numbered automatically.

The test suite compares the generated XML against hand-written schemas. The
example's messages are round-tripped against flyweights that sbe-tool
generates directly from the example's XML.

sbe-tool's `JavaDtoGenerator` provides Java value objects from an existing
schema. Here the records are written by hand, and the schema comes from them.

## Existing schemas

An existing XML can stay the source of truth: `@SbeSchema(resource = …)`
names it. The records then describe the whole schema, and the compiler checks
that they do, reporting each difference on the declaration it concerns:

```
the schema's NewOrder has a field "locateReqd" no component carries; add it, or declare it unmapped
```

A schema sbe-buddy wrote can also be checked in and used as the resource.
Removing `resource` later goes back to generating it.

With `partial = true` the records map only the messages the code needs. The
rest get sbe-tool's flyweights and no codec, and with no records at all the
package gives the flyweights and the baseline check alone.

## Setup

JDK 21 or newer. Releases are on Maven Central under `net.concini`.

`sbe-buddy-api` goes on the compile classpath:

```xml
<dependency>
    <groupId>net.concini</groupId>
    <artifactId>sbe-buddy-api</artifactId>
    <version>0.2.0</version>
</dependency>
```

and `sbe-buddy-processor` on the annotation processor path:

```xml
<plugin>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>net.concini</groupId>
                <artifactId>sbe-buddy-processor</artifactId>
                <version>0.2.0</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

Compiling needs no JVM flags. Code that uses Agrona's buffers at runtime,
tests included, needs `--add-opens java.base/jdk.internal.misc=ALL-UNNAMED`.

## Documentation

* [Guide](docs/guide/README.md)
* [Type mappings](docs/type-mappings.md)
* [Glossary](docs/glossary.md)
* [Intent](docs/intent.md)
* [Architecture](docs/architecture.md)
* [Notes](docs/notes.md): verified behaviour of sbe-tool, javac and Agrona

## License

MIT. See [LICENSE](LICENSE).
