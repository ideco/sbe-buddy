# Schema-first

A schema that has shipped is frozen: the XML is what every other reader has, and the records must keep saying exactly that. `@SbeSchema(resource = …)` points the package at its XML on the class path. The compiler then proves that the annotations and the document are one schema, and generates the flyweights and codecs from the document; nothing is written. Develop code-first, check the written `schema.xml` in as the resource and name it, and the switch changes nothing else. To grow the schema later, drop `resource` and the package writes it again, and what it writes is the document it just read.

## Declaration

```java
@SbeSchema(id = 91, version = 0, semanticVersion = "1.0", description = "Order entry in FIX's shapes", headerType = SessionHeader.class, resource = "trading.xml")
package com.example.trading;
```

`resource` is a class path name, as `Class.getResource` reads one: relative to the package, `"trading.xml"` for `com/example/trading/trading.xml`, or absolute with a leading slash. Every other member of `@SbeSchema` means what it means code-first. XIncludes in the document resolve relative to it, in a directory or inside a jar.

## The check

The annotations are the ones that wrote the document, complete, unless the package is [partial](#mapping-some-messages): every message has its record, every field of a message is a component or an `unmapped` entry, every group and var-data a component, since only a field can be unmapped, and every type of the document has its declaration. The compiler renders the schema from them as code-first would and holds it against the resource, with the XSD's defaults filled on both sides so an absent attribute equals its default, declarations and messages matched by name regardless of order and everything else in sequence. Each difference is an error on the node it is on, naming both sides:

* on the package, for what the document has and no annotation maps: `the schema has a message "Reject" (id 6) and no record maps it; add one, or declare the package partial`, `the schema has an enum "ExecType" and no declaration maps it`;
* on the record, for a member it does not carry: `the schema's NewOrder has a field "locateReqd" no component carries; add it, or declare it unmapped`, `the schema's ExecutionReport has a group "fills" no component carries`;
* on the annotation, for what the document lacks: `the schema has no message named "Order"`, `the schema's Order has no field named "nowhere"`, `the schema's Side has no value named "SHORT"`;
* on the annotation, for an attribute or a value that differs: `the schema has presence="optional", not "required"`, `the schema has the value "F", not "E"`.

Nothing is left for the document to decide. `@SbeField(id = 11) String clOrdId` names no type and is refused as it is code-first; `type = ClOrdId.class` says which. That is what makes the trip back safe: a package that compiles against its resource writes that resource again.

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

## Mapping some messages

A schema of two hundred messages, of which the code wants records for five, is mapped in part:

```java
@SbeSchema(id = 7, version = 2, resource = "/com/venue/schema.xml", partial = true)
package com.example.venue;
```

The records then map the messages they choose, and only those:

* a message without a record gets sbe-tool's flyweights and no codec;
* a type needs a declaration only where a record reaches it, and the header always;
* a message that has a record is still mapped whole, every field a component or `unmapped`;
* everything the annotations say is compared with the resource as above, so a record the resource lacks, or a value its enum does not have, is still an error.

A partial package may have no records at all. It is then a `package-info.java` alone, and what it gets is the flyweights and, with a `baseline`, the check that the resource still reads its baseline:

```java
@SbeSchema(id = 7, version = 2, resource = "schema.xml", partial = true, baseline = "schema-v1.xml")
package com.example.venue;
```

The baseline is held against the whole resource, the messages no record maps included; a difference on one of those lands on the package.

A partial package cannot go back to code-first: the records would write a schema without the messages they leave out. A baseline turns that into errors, one per message it has and the schema lacks.

## What it is not

Mapping part of a message, the fields of a venue's message that a client reads, is a different thing: the record would not be able to write the message back. That is a view, decode-only, and not built yet.
