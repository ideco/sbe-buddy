# Increment 3: the annotations, and the mapping to the model

## Goal

Annotated Java becomes the model, without javac. The api gains one
annotation per XSD element; the generator gains `Annotated`, the
annotations as data, and `Mapping`, the function from it to `Schema` that
`type-mappings.md` specifies; the rules of ours land, each on the node it
blames; and every corpus case gains a third view, so mapping is proven by
record equality against the schemas already proven against the oracles.
Discovery, the processor and the example are increment 4.

## Before it starts, in its own pull request

- The XSD coverage test takes an explicit list of attributes the XSD
  declares and sbe-tool ignores, `data`'s `presence`, `valueRef`, `epoch`
  and `timeUnit`, with the `notes.md` fact as its justification, and the
  `VarData` case loses them; otherwise no annotated twin can say that
  oracle.
- Two corpus cases the coverage test cannot ask for: a fixed-length
  primitive array (`primitiveType="uint8" length="3"`), and
  `presence="optional"` on a field rather than on its type.

## What gets built

- The api: one annotation per XSD element and one member per attribute,
  exactly as `type-mappings.md` lists them: `@SbeSchema`, `@SbeMessage`,
  `@SbeField`, `@SbeGroup`, `@SbeData`, `@SbeType`, `@SbeComposite`,
  `@SbeRef`, `@SbeEnum`, `@SbeEnumValue`, `@SbeSet`, `@SbeChoice`,
  retained at `CLASS`. Members are typed as the XSD types them: the api's
  own `PrimitiveType`, `Presence` and `ByteOrder` enums, since users never
  see sbe-tool's; `Class<?>` where the XSD holds the name of a declared
  type; `String` where the XSD types a number as a string. The standard
  composites as `@SbeComposite` records, `MessageHeader`,
  `GroupSizeEncoding`, `VarStringEncoding`, `VarAsciiEncoding`,
  `VarDataEncoding` and `UuidWire`, each carrying SBE's conventional wire
  name through `name`, so `@SbeSchema` defaults `headerType` to
  `MessageHeader.class`, `@SbeGroup` defaults `dimensionType` to
  `GroupSizeEncoding.class`, and the XML needs neither attribute. Nothing
  of the codec: no `Codec`, no `TypeBinding`, no `@Bind`, no bindings, no
  Agrona.
- `Annotated`, in the generator: one file, the `@SbeSchema` package as the
  root record with a nested record per annotation named after its element,
  one component per member with the member's name, plus the Java name of
  the annotated thing, its Java type as a small sealed descriptor (a
  primitive, `String`, `byte[]`, a `List` of a record, a declared type),
  and references to declarations by identity. A member left at its
  default is the default's value, since that is what javac hands over; a
  `name` left empty means the Java name. The root also holds the
  declarations the package makes, in source order: a declared type no
  message references still belongs in `types`, and nothing else could
  put it there.
- `Mapping`, in the generator: `Mapped map(Annotated annotated)`, where
  `Mapped` holds the `Schema`, the `Problem`s, and an identity map from
  each `Schema` node to the `Annotated` node it came from. Wire name from
  `name` or the Java name; a bare primitive component through the default
  mapping; a reference to the declared type's wire name; `types` holding
  the header, then what the schema declares in its own order, then what a
  reference reaches elsewhere in the order first reached, each once and
  every one after the declarations it refers to; fields, then groups, then
  data. The single-element rules
  of `architecture.md` fire here and return `Problem`s naming the
  `Annotated` node.
- `Generator.validate(Schema)`: the cross-node rules of `architecture.md`,
  returning `Problem`s naming the `Schema` node.
- The corpus, extended: every case gains `static Annotated annotated()`,
  built through `Fixtures`, which grows the builders for annotations
  beside the ones for the model; `CorpusTest` asserts
  `Mapping.map(annotated()).schema()` equals `schema()` and that its
  problems are empty. One unit test per rule, asserting the `Problem` and
  the node it names.

## Criteria

- Every corpus case maps to its schema by equality, with no problems.
- Every rule of ours has a test that builds the mistake and asserts the
  `Problem` and the node; every rule is reachable from `Annotated` or
  `Schema` alone.
- The generator's compile classpath holds the api, sbe-tool and JSpecify;
  nothing in it names `javax.lang.model` or `javax.annotation.processing`.
- `./mvnw verify` is green on a fresh clone, and the CI job passes on this
  pull request.

## Out of scope

Discovery, the processor, javac, `Filer`, `Messager`, the schema resource,
the example module. sbe-tool in the pipeline. `Codec`, `TypeBinding`,
`@Bind`, the built-in bindings, message families, Agrona.
