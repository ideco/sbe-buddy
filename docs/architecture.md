# Architecture

How sbe-buddy is put together. What it does for a user is in
`type-mappings.md` and the guide; the facts it relies on about sbe-tool,
javac and Agrona are in `notes.md`.

## Modules

```
sbe-buddy-api          annotations, PrimitiveType, Presence, ByteOrder, the built-in composites; Codec, TypeBinding
                       dep: agrona
sbe-buddy-generator    the models, the mapping, the XML, sbe-tool's pipeline, the codecs; no javac
                       deps: sbe-buddy-api, sbe-tool
sbe-buddy-processor    javac elements to Annotated, problems to Messager, sources to Filer
                       dep: sbe-buddy-generator
sbe-buddy-example      realistic schemas with their oracles, and interop with sbe-tool's own flyweights; not deployed
sbe-buddy-tests        the corpus, compiled by the real build and run against the generated code; not deployed
reference/             sbe-tool's sources as a submodule, for reading
```

`api ← generator ← processor`. The example and the tests depend on the api
and reach the processor only through `annotationProcessorPaths`, as a user's
build does, so sbe-tool is never on a user's compile or runtime classpath.
Base package `net.concini.sbebuddy`; each published jar sets
`Automatic-Module-Name` to its own package; no `module-info.java`.

## The pipeline

One compilation of an `@SbeSchema` package runs:

```
1  javac elements ──► Annotated       ours      Discovery, in the processor
2  Annotated ──► Schema               ours      Mapping and FaceRules
3  Schema ──► schema.xml              ours      SchemaXml
4  schema.xml ──► MessageSchema       sbe-tool  XmlSchemaParser
5  MessageSchema ──► Ir               sbe-tool  IrGenerator
6  Ir ──► flyweight sources           sbe-tool  JavaGenerator
7  Ir + Annotated ──► codec sources   ours      CodecWalk, CodecModel, CodecWriter, UnionWriter
8  schema.xml ──► a resource          ours      the schema ships in the jar
```

sbe-buddy writes the XML that goes in and consumes the IR that comes out;
nothing with wire semantics is computed here. `@SbeSchema(codecs = false)`
skips step 7. The unit of work is the package: every annotated element
brings its package, which is generated once, in the first round that shows
it. Discovery orders the top level by id and qualified name, never by
javac's order, so the same sources give the same output however they were
compiled.

## The models

Three closed grammars, each one file of nested records.

* **`Schema`** is `sbe.xsd`: a record per element, a component per
  attribute with the XSD's name, `@Nullable` where the XSD makes it
  optional, no defaults. `SchemaXml` writes exactly what it holds.
* **`Annotated`** is the api's annotations as data: a record per
  annotation, a component per member, plus what javac knows and an
  annotation cannot say: the Java name, the Java type, declarations by
  identity.
* **`CodecModel`** is what a codec is made of: bodies of members in wire and
  constructor order, each field a shape, an absence and a binding, and the
  helper methods they call.

Models carry no positions. Discovery maps each `Annotated` node to its javac
`Element` and `AnnotationMirror`, Mapping each `Schema` node to the
`Annotated` node it came from, both by identity. A `Problem` names a node of
either model, and the processor places it through those maps; an error
stops generation, a warning does not.

## Rules

Three layers, in the order a mistake meets them.

* **The type system.** Annotation members are typed as the XSD types them,
  so most of sbe-tool's name-resolution rules cannot fire.
* **Ours, positioned on the element.** Discovery holds what only javac can
  see; Mapping what one node decides; FaceRules what ties a component's
  Java type to the type the wire hands it; `Generator.validate` what
  compares nodes.
* **sbe-tool, as backstop.** The document is validated against `sbe.xsd`
  and parsed with warnings fatal; anything reported lands on the package
  with sbe-tool's text. A rule that matters to users graduates to ours.

## Generation

* **All or nothing.** Flyweights and codecs are generated into memory, the
  problems of every step collected, and only a package with no error
  reaches `Filer`.
* **Flyweights** come from `JavaGenerator` with sbe-tool's default
  configuration into `<schema package>.sbe`; the schema goes into the jar as
  `<schema package>/schema.xml`.
* **Codecs** are walked from the IR into a `CodecModel` and written from it,
  so a codec calls what sbe-tool generated and holds no wire numbers. A
  union's codec is a `UnionModel` over its members' models.

## The codec contract

```java
public interface Codec<T, H extends MessageHeader> {
  int encodedLength(T value);                                    // exact, header included
  int encode(T value, MutableDirectBuffer buffer, int offset);   // writes exactly encodedLength(value) bytes
  int encode(T value, H header, MutableDirectBuffer buffer, int offset);  // the header's own members from header
  T decode(DirectBuffer buffer, int offset);                     // acting version and block length from the header
  boolean canDecode(DirectBuffer buffer, int offset);            // whether decode takes it, by the header alone
  H decodeHeader(DirectBuffer buffer, int offset);               // the whole header, nothing checked
  int lastDecodedLength();                                       // bytes consumed by the last decode
  int decodedLength(DirectBuffer buffer, int offset);            // bytes a decode would consume, without decoding
}
```

A codec is a stateful instance, one per thread; bindings are stateless, and
each call hands one the `BindingContext` of its component, a constant of the
codec built from the IR. Its
one exception of its own is `IllegalArgumentException`, for a value it
cannot represent or bytes that are not its message; everything else passes
through unwrapped. `H` is the schema's header record; the standard four
members of a header are always the message's own. `null` is never a value.
A message's codec knows its one template; a union's codec composes its
members' codecs, switching once over every message beneath it, and writes no
wire code of its own. `Codec` is implemented only by generated code, so it
may grow.

## Testing

Each layer is tested where it lives; the module's own `AGENTS.md` says how.

* **sbe-buddy-tests** holds the corpus: one schema package per case, its
  oracle and its round trips, run against the generated code.
* **The processor** tests every rule a user can break as the source they
  write.
* **The generator** tests what no source can reach: the rules that compare
  nodes, the all-or-nothing pipeline, `Template` and `SchemaXmlAssert`.
* **The example** proves the wiring on realistic schemas and interop with
  sbe-tool's own flyweights across frozen versions.

## Build

* Java 21 target, Maven 4 through the wrapper. `./mvnw verify` is the gate;
  `./mvnw spotless:apply` before every commit.
* Spotless with the Eclipse formatter, tabs, 120 columns. JSpecify with
  `@NullMarked` packages, checked by NullAway; test code is not checked.
* Test JVMs carry `--add-opens java.base/jdk.internal.misc=ALL-UNNAMED` for
  Agrona; a user's javac needs no flag, because the processor never loads a
  buffer class.
* Versions live in the root pom only; a dependency arrives with the change
  that first needs it.
* CI runs `./mvnw -B -ntp verify` on JDK 21 and JDK 25. `main` takes pull
  requests only.
