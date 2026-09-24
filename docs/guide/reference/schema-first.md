# Schema-first

Sometimes the schema comes first. A venue publishes its SBE XML and every client speaks it; or a schema sbe-buddy wrote has shipped, is checked in, and should now be the thing the records follow rather than the thing they produce. `@SbeSchema(resource = …)` points the package at that XML. sbe-tool generates the flyweights of every message in it, the records map the messages they choose to, and every member an annotation states is checked against the document. Nothing is written: the resource is the schema.

## Declaration

```java
@SbeSchema(id = 91, version = 0, headerType = SessionHeader.class, resource = "/trading.xml")
package com.example.client;
```

`resource` is a class path name, as `Class.getResource` reads one: relative to the package, `"schema.xml"` for `com/example/client/schema.xml`, or absolute with a leading slash. `id` and `version` stay required and are checked against the document, as is `headerType` where the document frames its messages in a header of its own. `codecs` and `baselineVersion` mean what they always mean. XIncludes in the document resolve relative to it, in a directory or inside a jar.

## The records

The annotations are the ones code-first uses, and they mean the same. A record maps the message whose `id` it names; a component maps the field, group or var-data of its wire name, the Java name unless `name` says otherwise; an enum's constants and a set's choices map the values and choices of their names.

```java
@SbeMessage(id = 1)
public record NewOrder(
        @SbeField(id = 11) String clOrdId,
        @SbeField(id = 55) String symbol,
        @SbeField(id = 54) Side side,
        @SbeField(id = 38, binding = QtyBinding.class) long orderQty,
        @SbeField(id = 44, binding = PriceBinding.class) @Nullable BigDecimal price) {}
```

What is left unwritten, the document decides: `clOrdId` is a `char` array of twenty in the venue's schema, so its face is a `String`, with no `@SbeType` declared anywhere. What is written is checked: the `id`s here, and any `presence`, `sinceVersion`, `offset`, `type`, `semanticType` and the rest, wherever sbe-tool's IR carries the attribute. A disagreement is a compile error naming both sides, `the schema has id="2", not "3"`. That is what makes the switch safe: a schema sbe-buddy wrote agrees with its annotations as it is, so checking the written `schema.xml` in as the package's resource and adding `resource = "schema.xml"` changes nothing else, and generates the same code.

An enum, a set or a composite the record reaches, as a component's type or as a binding's wire type, needs its Java declaration, matched to the document's by wire name, since the codec maps constants and builds records. Declarations may live in any package: `Side`, `QtyBinding` and `PriceBinding` above are the venue package's own. The face rules apply as always, from the document's side: `long symbol` against a `char` array is `long is not the face of Symbol, which is String`, and a plain `int` on an optional field is refused as it is code-first.

## Mapping part of a schema

A message with no record gets its flyweights and no codec, and a union covers the mapped messages. Within a mapped message, a field no component carries is written as its null value, a composite as its members' null values, a group with no entries and var-data with zero length, and on the way in each is passed over whatever a fuller writer put there, the codec's `decodedLength` agreeing with the bytes. A component the document lacks is an error, `the schema's Order has no field named "nowhere"`.

## The build

The processor reads the resource from the compile class path.

**Maven.** Put the XML under `src/main/resources` and nothing else is needed: Maven copies it to `target/classes` before javac runs, and `target/classes` is on the class path. A change to the XML alone does not recompile, because Maven watches sources; run `mvn clean` or touch a source after editing only the schema.

**Gradle.** Gradle keeps resources off the compile class path and out of `compileJava`'s inputs, so two lines put them back:

```groovy
tasks.named('compileJava') {
    classpath += files(sourceSets.main.resources.srcDirs)
    inputs.files(fileTree('src/main/resources') { include '**/*.xml' })
}
```

**A schema in a jar.** A dependency's jar is on the compile class path in either build. Name the resource absolutely, `resource = "/com/venue/schema.xml"`, and it is read from the jar.

## What is and is not checked

Compared where the IR carries them: `id`, `version`, `semanticVersion`, `description` and `byteOrder` on the schema; `id`, `name`, `type` and `primitiveType`, `presence`, `sinceVersion`, `deprecated`, `offset`, `blockLength`, `length`, `characterEncoding`, `semanticType`, `description`, `epoch`, `timeUnit`, a constant's `value` and an enum value's and a choice's `value` on the nodes. Three things sbe-tool's IR blurs: a node's `sinceVersion` in the IR is the later of its own and its type's, so only a value written above it is a disagreement; a field's `semanticType` gives way to its type's, so it is compared only where the type has none; a group's `semanticType` is not read at all. `nullValue`, `minValue`, `maxValue` and `valueRef` are not compared.
