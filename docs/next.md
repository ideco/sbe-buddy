# Increment 22: Schema-first mapping

## Goal

A package maps an SBE schema that already exists. `@SbeSchema` names an XML
resource, sbe-tool generates the flyweights of every message in it, and the
records in the package map the messages they choose to, with the same
annotations as today; the codecs and the bindings follow. Nothing of the
schema is written: the XML is the authority, and it ships from where the
user put it. Both directions of the workflow are covered: a schema written
by sbe-buddy is checked in and the package switches to reading it, and a
schema owned by someone else, a venue's in a jar, is mapped as far as a
client needs.

Two commits, the second reviewed after the first. The first makes the
pipeline one flow from the document down, so the second adds a way to get
the document and little else.

## Settled before it started

- **Schemas are never merged.** A package's schema is written from its
  annotations or read from a resource, never assembled from both. An XML
  that supplies some types for annotations to build on is a merge; it is
  out of scope, and `intent.md` says so.
- **Hybrid means partial.** Some messages of the resource have a record,
  the rest get flyweights only. A message without a record has no codec,
  and a union covers only mapped messages. Within a mapped message, a
  field, group or var-data no component carries is treated as `unmapped`
  is today: skipped on decode, written as its null value, as empty or as
  zero length on encode.
- **The annotations mean the same in both modes.** A member left unwritten
  lets the XML decide; a member written is checked against the XML node it
  describes, and a disagreement is an error. The switch from code-first to
  schema-first is one member on `@SbeSchema`, and on the day the XML is
  what sbe-buddy wrote, every check passes by construction. `name` still
  defaults to the Java name, because that is how a record finds its node.
  An explicit `presence = REQUIRED` cannot be told from the default and is
  not checked; the face rules catch the drift it could hide.
- **The face rules run over the IR.** Today `FaceRules` derives the wire
  side of a component from `Annotated`, duplicating what `CodecWalk`
  derives from the token, and with minimal annotations there is nothing on
  the `Annotated` side to derive from. The rules move to where the token
  meets its annotation, in the walk, and run with `codecs = false` too:
  turning codecs off does not make a wrong record right. Rules sbe-tool
  crashes on rather than reports stay before the document, in `Mapping`.
- **The resource is found on `CLASS_PATH`** through `Filer.getResource`,
  relative to the package or absolute with a leading slash, as
  `Class.getResource` reads a name. It is the one location that works in
  Maven as it is, in Gradle with one line, and for a jar in both. Nothing is
  written in this mode: writing `<pkg>/schema.xml` beside the user's own
  copy fails Gradle's `jar` with a duplicate entry. The spike's facts are in
  `notes.md`.
- **XInclude resolves against the resource's URI.** The document is parsed
  from an `InputSource` carrying `FileObject.toUri()`, and the `sbe.xsd`
  validation of a resource runs on the document after inclusion.
- **The verify pass falls out.** With complete annotations and a resource,
  the compilation is the check that the two agree; there is no separate
  mode for it.

## What gets built

### Commit 1: the join

- **`FaceRules`** takes the type's token and the component's annotation:
  the face is derived from the token's signal, primitive and array length
  as `CodecWalk` derives its shape, and an enum, set or composite is
  matched by the wire name the token names. The rules for a field, a
  composite's inline member, a ref, a group's `List` face and var-data
  apply in the walk, each problem on its node; the boxing rule reads the
  absence the walk already decided. `Mapping` keeps only what must fire
  before sbe-tool parses: a constant field or member without a value, an
  unmapped member without a name, an optional member with a length.
- **`CodecWalk`** collects `Problem`s on their nodes instead of strings on
  the message, and a message with an error gets no model. It runs for
  every message whether or not the schema wants codecs; what a codec lacks
  is reported only when one is wanted, and `CodecEmitter` writes, and
  checks unions and duplicate codec names, only then. A face problem in a
  composite that several messages share is reported once.
- **`Generator`** parses from a document and a node to report sbe-tool's
  problems on, rendered from the `Schema` in code-first; warnings from the
  join no longer stop the output, in the generator or in the processor.
- **The tests** pass as they are, except one that mixed a face rule with a
  rule of the mapping's in one snippet, which is split into one test per
  layer. `PlacementTest` and `AnnotationMistakesTest`'s notes say where the
  face rules now fire.
- **The documents.** `architecture.md`'s pipeline and rules, the
  generator's and the processor's `AGENTS.md`.

### Commit 2: the resource

- **The api.** `@SbeSchema` gains `resource`, empty by default: the
  schema's XML on the class path, relative to the package or absolute with
  a leading slash. `id` and `version` stay required and are checked
  against the document.
- **The processor** reads the resource through `Filer.getResource` on
  `CLASS_PATH`, hands the document and its URI to the generator, and writes
  no `schema.xml`. A resource that is not there is an error on the
  annotation. `Mapping` and `Generator.validate` do not run.
- **The generator** parses the resource through an `InputSource` with its
  URI and XInclude on, validates the included document against `sbe.xsd`,
  and runs the join as in commit 1. The join gains what code-first could
  not reach: a record whose id names no message, a component whose wire
  name names no field, an enum constant or choice the XML lacks, a message
  whose wire name differs from the token's, and `baselineVersion` above
  the document's version are problems on their nodes; a field, group or
  var-data no component carries is written as `unmapped` is, a composite as
  its members' null values, a group with no entries and var-data with zero
  length, and passed over on the way in; a declaration a component reaches,
  through the component or its binding's wire type, is matched to the XML's
  type by wire name.
- **The checks.** Every annotation member the XML also states is compared
  where the token carries it: `id`, `name`, `presence`, `sinceVersion`,
  `deprecated`, `offset`, `blockLength`, `semanticType`, `description`,
  `primitiveType`, `length`, `characterEncoding`, `epoch`, `timeUnit`, an
  enum value's and a choice's `value`. A member written with a value the
  XML disagrees with is a problem on the annotation naming both.
- **The corpus.** `schemafirst`: `bigendian`'s records over the XML that
  package writes, checked in, expecting the generated sources to be the
  twin's; `partial`: one message of three mapped by two members of seven,
  round-tripped, the rest shown written empty and passed over whatever a
  full writer put there. The jar case rests on the spike's facts and the
  processor's one `CLASS_PATH` lookup, which the example's client exercises
  with an absolute name.
- **The example.** `com.example.client`, mapping part of `trading.xml` from
  the class path with the venue's bindings, crossed with the venue's codecs
  and sbe-tool's flyweights.
- **The documents.** `intent.md` ticks 22; `type-mappings.md` gains the
  `resource` member and a section on schema-first; `architecture.md`'s
  pipeline shows both ways to the document; the guide gains a schema-first
  page with the Maven note and the two Gradle lines, and the schemas page
  points to it; `notes.md` carries the spike.

## Criteria

- After commit 1, `./mvnw verify` is green with the test changes above and
  no other, and the corpus's generated code is byte-identical to before.
- A package over an XML sbe-buddy wrote compiles to the same flyweights and
  codecs as the package that wrote it.
- A partial mapping of a foreign schema compiles, round-trips against
  sbe-tool's flyweights and reports each disagreement with the XML on the
  annotation that states it.
- `./mvnw verify` is green on a fresh clone, and the CI job passes on this
  pull request.

## Out of scope

Merging a resource with annotations. `unmapped` groups and var-data in
code-first, which need `@SbeGroup` and `@SbeData` under `unmapped`, though
the codec now writes and passes over both. A composite in a header no
component carries.
Recompiling on a schema-only edit under Maven, which has no mechanism for
it; the guide says `clean`. Reading a schema from anywhere but the class
path.
