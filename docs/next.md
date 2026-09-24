# Increment 20: Bindings everywhere, with their context

## Goal

A binding may stand in for any component that carries a value: fields of
every kind, enums and sets included, var-data, groups and the members of
composites. Every binding call receives a `BindingContext` describing its
place in the schema, so one binding class serves every field it fits: it
reads the field's wire primitive, character encoding, epoch and time unit
instead of hard-coding them. Text in any character encoding the JDK knows gets
a codec. sbe-buddy ships no bindings of its own and no wire types beyond SBE's
framing: what a value means is the schema maintainer's decision, written in
their binding.

## Settled before it started

- **sbe-buddy stays agnostic of FIX.** `sbe.xsd` and sbe-tool's behaviour are
  the contract. The FIX datatypes (`UTCTimestamp`, `LocalMktDate`,
  `TZTimestamp`, `MonthYear`, the Boolean enum) are one convention on top
  of SBE, and nothing in the api, the codec or the docs assumes them. The
  FIX order-entry schema of increment 21 stays: it is a user's schema, the
  proof, not something the api knows.
- **No built-in bindings.** No time bindings, no decimal binding, no
  `UUID`. Whatever sbe-buddy picked, a user would want another encoding,
  unit, epoch or rounding. `UuidWire` leaves the api: SBE has no UUID
  layout, and two `int64` in the schema's byte order is one opinion among
  several. It becomes the guide's example of a binding over a composite.
  The api keeps SBE's framing, the header, the group dimension and the
  var-data encodings, which every schema needs in some form.
- **The context is an argument, not a constructor.** Every method of
  `TypeBinding` and its six specializations takes a `BindingContext` after
  the value: `long toWire(J value, BindingContext context)`,
  `J fromWire(long wire, BindingContext context)`. A binding stays
  stateless with a no-arg constructor, one instance per class in a codec.
  Each field with a binding gets one `static final` context in the codec,
  passed on every call, so nothing is allocated. There are no context-free
  overloads: a binding that does not need the context ignores it.
- **The context carries what changes how a value converts:**
  - `name`: the component's name, as the codec's own messages use it, so a
    binding's exception names the field.
  - `primitiveType`: the wire primitive after a named type is resolved, the
    element's for an array or a string, the encoding type for an enum or a
    set; `null` for a composite and a group. A `long` is `int64`, `uint64`
    or `uint32`; the binding can tell which.
  - `characterEncoding`: the type's, for `char` arrays and text var-data;
    otherwise `null`.
  - `epoch` and `timeUnit`: the field's attributes as written, `null` where
    absent or where the XSD has none. sbe-buddy fills in no default and
    interprets neither: `sbe.xsd` types both as free strings, and sbe-tool
    reads an absent one as `null` and checks no value.
  - Not `sinceVersion` or `deprecated`: whether a value is there is decided
    by the codec before the binding is called. Not the acting version: it
    differs per message, and SBE evolution never changes an existing field's
    meaning. Only generated code constructs a context, so a component added
    later breaks no binding.
- **`timeUnit` is deprecated on `@SbeField`,** as `sbe.xsd` deprecates it on
  `field` ("only for back compatibility with RC2"). It keeps working; javac
  warns whoever writes it. `epoch` is not deprecated, as in the XSD. The
  Javadoc of both stops promising "SBE's default applies": nothing applies
  one.
- **A binding goes wherever a component carries a value**, because it is
  optional and nothing is gained by refusing it. The refusals left are the
  ones where a binding cannot mean anything:
  - an `unmapped` field, which has no component;
  - a declaration itself, an `@SbeType` class, `@SbeEnum`, `@SbeSet` or an
    `@SbeComposite` as a type: a binding belongs to a use of a type, and a
    declaration is never a binding;
  - the members SBE's framing owns, a group dimension's, var-data's length
    and bytes, the header's standard four, which the codec computes; no
    record component carries them, and `MessageHeader`'s accessors pin the
    header's.
- **Enums and sets bind over their faces,** the user's enum and `Set<E>`.
  Unknown values are settled before the binding runs, as the unknown-value
  contract says; the binding sees only a constant or a set of them. The
  Boolean enum is the motivating case: a schema declaring `false` = 0,
  `true` = 1 over `uint8`, a record holding a `boolean`.
- **A group binds over its `List<E>`,** so a record may hold a map, an
  immutable collection or an array of entries. `encodedLength` walks the
  entries, so `toWire` runs there and again in `encode`; the guide says so.
- **Text in any encoding.** sbe-tool's flyweights already read and write
  text in any `characterEncoding`; the codec refused because the JDK
  writes an unmappable character as `?` silently and a byte limit needs the
  bytes counted. The codec encodes through a `CharsetEncoder` that reports
  instead of replacing, checks the length in bytes, and keeps the
  allocation-free counting for ASCII and UTF-8. Another encoding encodes
  once to learn its length, so its `encodedLength` allocates; the guide says
  so. Text stays the codec's, not a binding: a binding over bytes would
  cost an allocation on every ASCII write and an encode in `encodedLength`
  for UTF-8, for no gain.

## What gets built

- **The api.**
  - `BindingContext(String name, @Nullable PrimitiveType primitiveType,
    @Nullable String characterEncoding, @Nullable String epoch,
    @Nullable String timeUnit)`.
  - `TypeBinding` and its specializations take the context on both methods.
  - `binding` on `@SbeData`, `@SbeGroup`, `@SbeRef`, and on `@SbeType` where
    it annotates a component; on an `@SbeType` class it is refused.
  - `@Deprecated` on `@SbeField.timeUnit`, and the Javadoc of `epoch` and
    `timeUnit` corrected.
  - `UuidWire` removed.
- **`Annotated`.** A binding on every component kind: data, group, inline
  member and ref beside field.
- **`FaceRules`.** One rule for every kind: a binding's `W` is the face, its
  `J` the component's type, a primitive face through its specialization.
  The enum and set refusals go; the declaration and `unmapped` refusals stay.
- **The codec.**
  - One `static final BindingContext` per bound component, built from the
    IR's token for it, passed on every call.
  - The bound read and write for enums, sets, var-data, groups and composite
    members, beside the fields that have them today. A constant with a
    binding compares `toWire(value)` with the constant.
  - Text in any encoding the JDK supports, for `char` arrays and var-data,
    through a reporting `CharsetEncoder`; an unmappable character is an
    `IllegalArgumentException` naming the field, as `symbol is not ASCII`
    is today. The codec refuses, naming the message, an encoding the JDK
    does not know, and for a `char` array one that writes a zero byte inside
    a character, UTF-16 or UTF-32, since the flyweight reads a `char` array
    up to its first zero byte; `codecs = false` keeps the flyweights, as
    the schema is valid SBE. Both "no codec for … yet" problems for text
    go.
- **The corpus.**
  - `bindings` grows: a binding over an enum (the Boolean case, a `boolean`
    over a `false`/`true` enum), over a set, over var-data, over a group,
    over an inline member and a ref; one binding class on a signed and an
    unsigned field, telling them apart by the context; a binding reading
    `epoch` and `timeUnit` from its field, and one refusing with the name
    from the context.
  - A case with text in an encoding other than ASCII and UTF-8, fixed and
    var-data, its unmappable character refused.
  - Every existing binding takes the context.
- **The example.** `Price` takes the context and names the field when it
  refuses a price.
- **The snippets.** A binding on an `unmapped` field and on a declaration
  still refused; on every newly allowed kind, compiled clean; a face
  mismatch per kind; `timeUnit`'s deprecation warning; an unknown
  character encoding.
- **The documents.**
  - `intent.md`: the scope loses "built-in bindings for the JDK types";
    increment 20 is this one.
  - `type-mappings.md`: bindings on every component, the context, the
    built-ins paragraph down to SBE's framing, the constant check through a
    binding, text in any encoding, and the running example without
    `UuidWire`.
  - `architecture.md`: the codec contract's paragraph on bindings.
  - The guide: the bindings page with the context, the new kinds and the
    `UUID` example over a composite; the var-data and named-types pages for
    text in any encoding; the enums and sets pages for their bindings; the
    primitives page on `epoch` and `timeUnit`.

## Criteria

- Every component kind that carries a value round-trips through a binding
  in the corpus, and each binding sees the context of its field.
- A schema with text in any encoding the JDK knows gets a codec.
- Nothing in the api or the docs names a FIX datatype as sbe-buddy's own.
- `./mvnw verify` is green on a fresh clone, and the CI job passes on this
  pull request.

## Out of scope

Built-in bindings of any kind. Interpreting `epoch`, `timeUnit` or
`semanticType`. The acting version in the context. A binding over a whole
message or a union.
