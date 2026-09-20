# Increment 2: the schema model and its XML

## Goal

The generator's core, complete before anything touches it: a model that
mirrors `sbe.xsd` node for node, a writer from the model to the XML
schema, and a corpus proving the XML equivalent to hand-written schemas
for every element and attribute the XSD declares. No annotations, no
javac, no IR, no flyweights, no codec. sbe-tool appears only in tests, to
confirm that each oracle is a schema it accepts.

## What gets built

- `sbe-buddy-generator`, a module depending on sbe-tool and nothing else,
  `net.concini.sbebuddy.generator` `@NullMarked`, `Automatic-Module-Name`
  `net.concini.sbebuddy.generator`. The processor module depends on it
  and loses its own `generator` package; the example does not depend on
  it. sbe-tool `1.40.2` joins the root POM.
- The model: immutable records, one per `sbe.xsd` element, named after it
  with a `Def` suffix (`SchemaDef`, `TypeDef`, `CompositeDef`, `RefDef`,
  `EnumDef`, `ValidValueDef`, `SetDef`, `ChoiceDef`, `MessageDef`,
  `FieldDef`, `GroupDef`, `DataDef`), one component per XSD attribute
  with the XSD's name, `value` for element text. An attribute the XSD
  makes optional is `@Nullable`; nothing carries a default. Children are
  `List`s in declaration order. Enumerated attributes use sbe-tool's
  `PrimitiveType` and `Presence`, and `java.nio.ByteOrder`. A composite's
  members are a sealed choice of `TypeDef`, `RefDef`, `EnumDef`, `SetDef`
  and `CompositeDef`, as the XSD allows.
- `SchemaXml`: `Document of(SchemaDef)` and `void write(SchemaDef,
  Writer)`. Namespace `http://fixprotocol.io/2016/sbe`, elements in the
  XSD's order (`types`, then each `message`), children in model order, an
  attribute written only when its component is non-null, so the document
  reads as a person would write it and never states a default.
- Test libraries, versions in the root POM: JUnit, AssertJ, XMLUnit with
  its AssertJ module. The surefire and JUnit versions are checked to agree.
- `SchemaXmlAssert` in the generator's tests: whitespace and comments
  ignored; both documents parsed with `sbe.xsd` from the sbe-tool jar
  attached, so XSD defaults are filled and an absent attribute equals its
  default; `type`, `composite`, `enum`, `set` and `message` matched by
  `name` regardless of order, everything else in sequence; the namespace
  prefix registered once for XPath probes. A corpus test is one line
  through it.
- The corpus, `net.concini.sbebuddy.generator.corpus`: each case a class
  with `static SchemaDef schema()` beside a same-named oracle `.xml`
  resource, listed explicitly. Per case: the emitted XML is equivalent to
  the oracle, and the oracle parses through `XmlSchemaParser` with no
  error and no warning. Cases, one per XSD feature and the shapes worth
  taking from sbe-tool's own test schemas, written fresh:
  `Primitives` (every `primitiveType` as a field), `NamedTypes` (every
  attribute of `type`), `Constants` (constant `type`, `valueRef`, a
  constant enum field), `Enums`, `Sets`, `Composites` (inline types,
  `ref`, offsets, an inline enum and set, a nested composite), `Groups`
  (nested, with data, a custom `dimensionType`), `VarData` (with and
  without `characterEncoding`, a custom `type`), `Versions`
  (`sinceVersion` and `deprecated` on every node kind,
  `semanticVersion`), `Header` (a custom `headerType`), `BigEndian`,
  `Messages` (`blockLength`, `offset`, `semanticType`, `epoch`,
  `timeUnit`, `description` everywhere).
- The coverage test: read `sbe.xsd` from the jar, list every element and
  attribute it declares, assert each occurs in at least one oracle, and
  name the missing ones. Completeness is a failing test until it is true.

## Criteria

- Every corpus case passes both assertions, and the coverage test passes.
- The generator module's compile classpath holds sbe-tool and JSpecify
  only; nothing in it names `javax.lang.model` or
  `javax.annotation.processing`.
- A deliberately wrong attribute in one case fails with the XPath of the
  difference and both values; tried once and not committed.
- `./mvnw verify` is green on a fresh clone, and the CI job passes on this
  pull request.

## Out of scope

Annotations, the processor, javac, `Generator.validate` and every rule,
the IR, flyweights, the codec, the schema resource in the jar, the
example module's contents, the api module's contents.
