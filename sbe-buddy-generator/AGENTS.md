# sbe-buddy-generator

The core: annotations as data in, the XML, sbe-tool's flyweights and the codecs
out. No javac here; the processor is the only front-end.

```
Annotated        the api's annotations as data, plus what javac knows
Mapping          Annotated to Schema, and the rules one node decides
FaceRules        a component's Java type against the type the wire hands it
Schema           sbe.xsd as records
SchemaXml        Schema to XML, exactly what the model holds
Generator        steps 3 to 7, all or nothing, and the rules that compare nodes
CodecWalk        the IR and Annotated to a CodecModel
CodecModel       what a codec is made of
CodecWriter      a CodecModel to source
CodecTemplates   the text blocks the writer fills
Template         a text block with named placeholders
CodecEmitter     the walk and the writer per message, then the output
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
  codec's files import `CodecModel`'s interfaces and read each leaf through
  them, `Shape.Enum`.

## Where a rule goes

- Only javac can see it: `Discovery`, in the processor.
- One node decides it: `Mapping`.
- It ties a component's Java type to its face: `FaceRules`, which runs on
  every field, composite member and ref.
- It compares nodes (duplicates, versions, append-only): `Generator.validate`.
- Everything else is sbe-tool's, reported verbatim on the package. Our
  documents raise nothing there, so anything it reports is our mistake.

## Codecs

- The walk goes through the IR as `JavaGenerator` does and names every
  flyweight member through `JavaUtil`. Never compute an offset, a null value
  or a method name.
- Generated code holds no wire numbers; lengths come from the flyweights'
  constants. The only literals are the baseline, the valid values' text and
  the choices' bits.
- A construct the codec does not cover yet is a `Problem` on the message,
  collected once, and the message gets no model. Nothing is skipped silently.
- A new construct is a node in `CodecModel`, a case in the writer's switches
  and its templates in `CodecTemplates`.
- Templates hold no conditionals and no loops. What varies is decided in Java
  and filled in; no code is assembled by concatenation.
- Generated codecs use fully qualified names and no imports, and are not
  formatted afterwards. Keep them readable: they are read while debugging.

## Tests

- A rule a user can break is tested in the processor, as source. Codec
  behaviour is tested in sbe-buddy-tests, against compiled generated code.
- Here: `GeneratorTest` for the rules that compare nodes, the all-or-nothing
  pipeline and the constructs the codec refuses; `MappingTest`,
  `TemplateTest`, and `SchemaXmlAssert` with its own test.
- `SchemaXmlAssert` is the XML equivalence every module uses, shipped in the
  test jar. It fills the XSD's defaults, matches `type`, `composite`, `enum`,
  `set` and `message` by name and everything else in sequence.
- `Fixtures` holds plain factories, fixed defaults inside, for the few models
  built by hand. Build a node once and pass it to its parent.
