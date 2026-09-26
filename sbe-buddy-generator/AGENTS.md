# sbe-buddy-generator

The core: annotations as data in, the XML, sbe-tool's flyweights and the codecs
out. No javac here; the processor is the only front-end.

```
Annotated        the api's annotations as data, plus what javac knows
Mapping          Annotated to Schema, and the rules one node decides
FaceRules        a component's Java type against the face its token hands it, applied by Join
Schema           sbe.xsd as records
SchemaXml        Schema to XML, exactly what the model holds
SchemaEquivalence whether two documents are one schema, and where they differ
SchemaEvolution  whether a schema still reads its baseline, and where it breaks it
Generator        steps 3 to 7, all or nothing, and the rules that compare nodes
Join             one message's IR joined with its record, or with nothing: a tree of nodes and their leaves
Faces            the leaf: what a component is on the wire, its shape, absence, binding and helpers
FaceWriter       a Faces leaf to source: its read, write, null write, null test and helpers
FaceTemplates    the text of a leaf: read, write, null write, absence, binding
FaceHelperTemplates the text of the helpers the leaves call: checks, array pairs, enum and set mappings
CodecWalk        a Join to a CodecModel: bodies, wire and constructor order, the methods collected
CodecModel       what a codec is made of around its leaves
CodecWriter      a CodecModel to source, each leaf through FaceWriter
CodecTemplates   the text of a codec around its leaves: the class, the header, lengths, groups, var-data, the union
UnionModel       what a union's codec is made of: its members' codecs
FlyweightWalk    a Join to a FlyweightModel: a reader's stages, positions and steps in wire order, and the names it takes
FlyweightModel   what a message's reader is made of
ReaderWriter     a FlyweightModel to source
ReaderTemplates  the text of a reader: the class, its control, the methods that arrive at each stage, the steps
ReaderStageTemplates the text of a reader's stages and the accessors they delegate
WriterWalk       a Join to a WriterModel: a writer's stages, their objects and positions in wire order, the names it takes; the sub-chains
WriterModel      what a message's writer is made of, and a composite's or a set's sub-chain
WriterWriter     a WriterModel to source, null values through FaceWriter
WriterTemplates  the text of a writer: the class, its stages, the objects behind them, the null values
WriterStepTemplates the text of a writer's steps: guard, delegation to the encoder, how each hands on
SubWriterTemplates the text of a composite's and a set's sub-chain
FlyweightEmitter a reader and a writer for every message of the IR, the sub-chains, then the output
UnionWriter      a UnionModel to source
Template         a text block with named placeholders
CodecEmitter     the walk and the writer per message, a union's codec over them, then the output
Problem          a mistake on a node of either model
```

## The models

- `Schema` has a record per XSD element, named after it, and a component per
  attribute with the XSD's name; `value` is element text. Required components
  first, then children, then optional ones in XSD order.
- Optional is `@Nullable`, boxed where numeric. Nothing has a default: a
  default here is a second copy of the XSD.
- A name sbe-tool resolves (`type`, `encodingType`, `dimensionType`,
  `valueRef`, `headerType`) is a `String`. What the XSD enumerates is an enum.
  A number the XSD types as a string stays a `String`.
- Structure carries the XSD's order rules: a message holds `fields`, `groups`
  and `data` as three lists, a composite one list of a sealed `Member`.
- A record's Javadoc names the XSD element it mirrors and stops.
- `Annotated` has a record per annotation and a component per member, with
  references to other declarations by identity, never by `Class`.
- `Schema` and `Annotated` nodes are used qualified, `Schema.Field`. The
  codec's and the leaves' files import the interfaces of `CodecModel` and
  `Faces` and read each leaf through them, `Shape.Enum`.

## Where a rule goes

- Only javac can see it: `Discovery`, in the processor.
- One node decides it: `Mapping`.
- It compares nodes (duplicates, versions, append-only): `Generator.validate`.
- It ties a component's Java type to its face: `FaceRules`, which `Join`
  applies to every token it meets with an annotation, fields, groups,
  var-data, composite members and refs, reading the face from the token so
  the rule and the codec never disagree. It runs with `codecs = false` too.
  A rule sbe-tool crashes on rather than reports, a constant without a
  value, stays in `Mapping`, before the document.
- It compares the annotations with a resource: `SchemaEquivalence`, on
  the document rendered from them, not on the IR, which blurs since-versions
  and semantic types (`notes.md`). It is the one equivalence the oracles'
  tests apply too, through `SchemaXmlAssert`, so what the corpus proves
  against its oracles the compiler proves against a resource. Each
  difference names a path, and `Generator` puts it on the schema node the
  path reaches. Partial, it skips what only the resource has, and reads the
  rendered document against sbe.xsd with the messages optional.
- It compares the schema with its baseline: `SchemaEvolution`, on the
  rendered document against the baseline's, sharing `SchemaEquivalence`'s
  parsing and paths. Nothing is matched by name there: messages by id,
  members by position, types by structure, following each document's own
  references.
- Everything else is sbe-tool's, reported verbatim on the package. Our
  documents raise nothing there, so anything it reports is our mistake.

## Codecs

- `Join` goes through the IR as `JavaGenerator` does and names every
  flyweight member through `JavaUtil`. Never compute an offset, a null value
  or a method name.
- Generated code holds no wire numbers; lengths come from the flyweights'
  constants. The only literals are the baseline, the valid values' text and
  the choices' bits.
- The header is walked as a composite from `ir.headerStructure()`. Its
  standard four are read and never written: the message flyweight's
  `wrapAndApplyHeader` writes them. Its own members are written from a
  header or as their null value.
- A construct the codec does not cover yet is a `Problem` on the message,
  reported only when codecs are wanted, and the message gets no model.
  Nothing is skipped silently. A composite is walked by every message that
  uses it; `CodecEmitter` reports each of its problems once.
- A new construct is a shape in `Faces`, its templates in `FaceTemplates`
  (a helper's in `FaceHelperTemplates`), and a case in `FaceWriter`; the
  codec and the flyweights both read it. What surrounds the leaves, a
  block, a group, var-data, is a node in `Join` and `CodecModel`, with its
  templates in `CodecTemplates`.
- Templates hold no conditionals and no loops. What varies is decided in Java
  and filled in; no code is assembled by concatenation.
- A binding stands in front of any member's write and behind its read, and
  is handed its component's `BindingContext`, one `static final` constant per
  bound component built from the IR's tokens. A member's helpers and context
  are named after its path, a group entry's member after the group's path and
  `$`, a composite's after its class and `$$`, so members that spell alike
  once joined, `fillsPrice` and `fills.price`, never share one. Absence and
  unknown values are settled before it is called, and it is never handed
  `null`, except on an optional field whose face has no null value: a
  composite, a set, an array or a string. There the binding chooses null's
  wire form, and without one the codec refuses `null`; appended above the
  baseline, such a field is still `null`, unbound, when the message predates
  it.
- Text goes through the flyweight's `String` form in ASCII, is counted
  without encoding in UTF-8 var-data, and in any other encoding goes through
  a reporting `CharsetEncoder`, never `String.getBytes`, which would write an
  unmappable character as `?`.
- A union's codec composes its members' codecs and writes no wire code of its
  own. It switches on the record's type to encode and on the flyweights'
  `TEMPLATE_ID` constants to decode, once over every message beneath it.
- Generated codecs use fully qualified names and no imports, and are not
  formatted afterwards. Keep them readable: they are read while debugging.

## Readers

- Every message of the IR gets a reader, `ir.messages()` in template id
  order, a record or not. `FlyweightEmitter` runs after `CodecEmitter`, and
  only when it reported no error: the reader joins each message with its
  record the same way, and the join's problems are the codec emitter's.
- The reader's order is `OtfMessageDecoder`'s: each position of the `At`
  enum has one step, the level's next present group or var-data, else the
  next entry of the group the level is an entry of, else what follows that
  group. `ReaderSequenceTest` in sbe-buddy-tests holds every reader of the
  corpus against it.
- A stage's accessors are sbe-tool's decoder getters for the field, named
  and typed as `JavaGenerator` names them, derived from the type token; a
  new kind of field is a case in `FlyweightWalk.accessors`.
- Every name the reader declares comes from the model. A group, var-data or
  field that would take one the reader has is a `Problem`, never a rename.
- `GroupsReader.java` is checked in as a golden in sbe-buddy-tests; a change
  to the reader's text shows there, and `-Dgolden.update=true` rewrites it.

## Writers

- Every message of the IR gets a writer beside its reader, walked only when
  the reader's walk reported no error, so a name both would take is
  reported once. It writes the current version: every field but the
  constants is a step, mapped or not.
- A block's required fields are a stage each, in wire order; its optional
  fields and its first group or var-data sit on the stage where it is
  complete. Each stage is an interface, and one object per block, and one
  per group, implements all of its stages. A new kind of field is a case in
  `WriterWalk.steps`, with sbe-tool's setters for it.
- A block is filled with its null values when it opens, through
  `FaceWriter.writeNull`, the codec's null writers; a composite through a
  `nulls` overload of its own. The null writers take a `Faces.Shape` built
  from the token alone, since a writer needs no record.
- A composite's and a set's sub-chain is generated once per package, for
  those a step opens, from the IR's type by name.
- Every name the writer declares comes from the model; a field, group or
  var-data that would take one the writer has, a class or a method of an
  object, is a `Problem`, never a rename.
- `GroupsWriter.java` and `QuoteWriter.java` are goldens in sbe-buddy-tests,
  beside `GroupsReader.java`.

## Tests

- A rule a user can break is tested in the processor, as source. Codec
  behaviour is tested in sbe-buddy-tests, against compiled generated code.
- Here: `GeneratorTest` for the rules that compare nodes, the all-or-nothing
  pipeline and the constructs the codec refuses; `JoinTest` for the join
  with and without a record; `MappingTest`, `TemplateTest`, and
  `SchemaXmlAssert` with its own test.
- `SchemaXmlAssert` is the assertion every module uses over
  `SchemaEquivalence`, shipped in the test jar: the XSD's defaults filled,
  `type`, `composite`, `enum`, `set` and `message` matched by name and
  everything else in sequence.
- `Fixtures` holds plain factories, fixed defaults inside, for the few models
  built by hand. Build a node once and pass it to its parent.
