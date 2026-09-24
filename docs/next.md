# Increment 19: Unions

## Goal

A sealed interface over messages of one schema, annotated `@SbeUnion`, gets a
codec of its own: `<Union>Codec implements Codec<Union, H>`. It decodes any
of its messages to the interface, chosen by the header's template id, and
encodes any of them by the record's type. The caller handles the result with
an exhaustive `switch`, which javac checks, so a message joining the union
breaks every `switch` that misses it at compile time. A union is how a stream
carries several messages, how a message is replaced by a new template, since
a composite cannot grow, and what a router dispatches on.

## Settled before it started

- **The name is union.** On the wire a union is a tagged union whose tag is
  the template id. "Family" suggested lineage, which is one use among
  several; the docs rename it throughout.
- **A union is opted into.** `@SbeUnion` goes on a sealed interface in a
  schema package. Nothing is inferred from a sealed interface alone: an
  interface that mixes a message with another record stays an ordinary
  interface, and one meant as a union but holding a mistake is an error
  instead of a silently missing codec. The annotation contributes nothing
  to the schema.
- **Compose, don't regenerate.** A union codec owns one codec per member
  message and delegates to it; it writes no wire code of its own, so its
  bytes are its members' bytes, and every member codec stays usable alone.
  No visitor or handler interface is generated: pattern matching is the
  visitor.
- **Hierarchies follow one rule.** Every annotated interface gets a codec
  over all the messages beneath it; an unannotated sealed interface in
  between flattens into the one above it. A record may belong to several
  unions, and one reached through two paths of a hierarchy counts once.
  Two unions' codecs are unrelated types: `Codec<OrderCommand, H>` is no
  `Codec<Ingress, H>`.
- **A union of unions dispatches flat.** A union's codec switches once over
  every message beneath it and never delegates to a nested union's codec:
  the bytes are the messages' either way, and a second switch buys nothing.
  What nesting gives lives in Java's types: a caller's `switch` may take a
  nested union as one case, exhaustively checked through the hierarchy, and
  each nested union's codec serves a channel of its own.
- **What a union may hold.** Every permitted subtype is an `@SbeMessage`
  record of the schema or a sealed interface whose own subtypes follow the
  same rule. A class, a `non-sealed` subtype, a record without
  `@SbeMessage`, a generic union, a union on a schema with `codecs = false`
  and `@SbeUnion` outside a schema package are refused by `Discovery`, on
  the element that is wrong. Java already keeps a sealed interface's
  subtypes in its package outside a named module.
- **`Codec` gains `boolean canDecode(DirectBuffer buffer, int offset)`.** It
  answers, without throwing, whether `decode` would take the message at the
  offset: the schema id, the template id, and a version at or above the
  baseline. A message codec knows one template, a union codec its members'.
  A router asks it instead of catching `decode`'s
  `IllegalArgumentException`, which `decode` keeps. No codec skips a
  message it cannot read: with groups or var-data its length is unknown to
  it, so skipping is the transport's.
- **No unknown member.** A template id outside the union is not a value:
  an unknown-message record would have no body to hold, and every `switch`
  would carry a case for it.
- **`encode(null, …)` is refused on every codec**, message and union alike,
  with `IllegalArgumentException("value is required")`, from
  `encodedLength` and both `encode`s. Today a message codec throws a
  `NullPointerException`.

## What gets built

- **The api.** `@SbeUnion`, retained at `CLASS` on a type, and
  `Codec.canDecode`, documented as a contract.
- **`Annotated`.** `Union(javaName, qualifiedName, members)` per annotated
  interface, `members` being messages of `messages` by identity, the
  hierarchy beneath flattened, each once, in template id order. A nested
  union is a second `Union` over a subset of the same messages; the tree
  stays javac's, checked by `Discovery`. `Message` gains `qualifiedName`, so
  a message nested in its union's interface, the natural style, gets a
  codec naming it rightly.
- **`Discovery`.** It finds `@SbeUnion` interfaces in the schema package,
  nested ones included, walks their permitted subtypes and applies the
  union rules. A wrong subtype reached from two unions is reported once, on
  the subtype. An `@SbeUnion` in a package without `@SbeSchema` is refused
  where it stands.
- **One codec per name.** Codecs are named by simple name in the schema
  package, so two sources of one codec name, a union and a message nested
  in different types or two such messages, are refused naming both.
- **The union codec.** A model of its own beside `CodecModel`,
  `UnionModel(packageName, codec, union, unionName, flyweights, header,
  cases)`, each case a member's record, its codec and the flyweight whose
  `TEMPLATE_ID` labels the case. The writer renders it through templates:
  - `encodedLength`, `encode` and `encode` with a header switch on the
    record's type, exhaustively, and delegate.
  - `decode`, `decodedLength` and `canDecode` read the header and switch on
    the template id, the cases being the flyweights' constants, so no wire
    number is written. `lastDecodedLength` is the last member codec's.
  - `decodeHeader` is any member's, since the header is the schema's.
  - A template id outside the union is `IllegalArgumentException("not a
    <Union>: schemaId …, templateId …; its templates are …")`.
  - A member whose message has no codec, because it holds a construct the
    codec lacks, is a problem naming the union as well as the message.
- **Message codecs.** `canDecode`, and the `null` check before any write.
- **The corpus.**
  - `unions`: a flat union; a hierarchy with codecs at two levels and an
    unannotated interface flattened between them; a record in two unions;
    a record reached twice. Round trips through every union codec, and
    tests that a narrower union's `canDecode` refuses its wider union's
    other templates.
  - `replacement.v0` and `replacement`: a message over a composite in
    version 0, and in version 1 a new template over a new composite beside
    it, both in one union. A version 0 reader's `canDecode` says no to the
    new template and its `decode` refuses it; the current union reads both
    templates; a current writer still sends the old template to old
    readers.
  - `SchemaCasesTest` asserts `canDecode` on every round trip's bytes.
- **The snippets.** Every refusal above, and a clean hierarchy with an
  unannotated interface in the middle.
- **The documents.** `type-mappings.md`'s families section becomes unions;
  a guide page for unions, with the exhaustive `switch`, hierarchies,
  replacing a message and routing with `canDecode`; `architecture.md`'s
  codec contract; `intent.md` renames families and ticks 19; `rpc.md`'s
  mentions follow the name.

## Criteria

- Every union codec round-trips every member through the whole codec
  contract, and a caller's `switch` over its result compiles only when it
  covers every member.
- A router can tell every codec's messages from others' through `canDecode`
  alone.
- The replacement case crosses its versions both ways.
- `./mvnw verify` is green on a fresh clone, and the CI job passes on this
  pull request.

## Out of scope

The FIX order-entry union, increment 21. RPC and any service layer
(`rpc.md`). A union spanning schemas. Skipping a message the codec cannot
read.
