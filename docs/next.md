# Increment 21: A FIX-like order-entry showcase

## Goal

The example's `com.example.trading` grows from a toy into a realistic
order-entry schema in FIX's shapes, without being FIX or claiming to be:
FIX tag numbers as ids, FIX's message types as `semanticType`, char enums
with FIX's values, decimals with constant exponents, timestamps and dates
as counts. It is the showcase: every sbe-buddy feature appears where such a
schema would naturally use it, the bindings a user writes beside it, and
sbe-tool's flyweights generated from its hand-written oracle read what our
codecs write and the reverse. On the way, a composite, a set and an array
field may be optional, as SBE allows, and a binding decides what null is on
the wire.

## Settled before it started

- **Not called FIX.** The package stays `com.example.trading`; the schema
  follows FIX's shapes and says so in its description, nothing more.
  `intent.md`'s increment 21 becomes "a FIX-like order-entry schema".
- **Not copied from the FIX standard.** Its example schemas are licensed CC
  BY-ND 4.0, and their wire dumps disagree with their own schemas (the
  order schema's id against its header, the execution report's table
  against its dump). The schema is written fresh, and sbe-tool's flyweights
  are the only byte reference.
- **An optional field whose face has no null value of its own.** SBE puts
  `presence="optional"` on any field, and sbe-tool accepts it on a
  composite, a set and an array; sbe-buddy refuses it today
  (`Price is a composite; a field of it cannot be optional`, and the same
  for a set and a type with a length). It stops refusing, and the codec
  reads and writes such a field as its face, which has no null to write:
  - With a binding, the component goes to `toWire` as it is, `null`
    included, and `fromWire` is called on whatever is read. The binding
    chooses the null's representation, a null mantissa, a zeroed composite,
    an empty set, and returns `null` when it reads it back.
  - Without one, a `null` component is refused although the field is
    optional, `price has no null value on the wire; a binding may write
    one`, and a decoded component is never `null`.
  - Where the wire has a null of its own, a scalar's or an enum's null
    value, or a field added above the message's version, nothing changes:
    the codec settles absence and the binding never sees `null`. A required
    field still refuses `null`.
  - The composites guide stops saying a composite has no null value: SBE
    says an optional composite is null when its first element is. That
    convention is the binding's to apply, not the codec's.
- **`BindingContext` gains `presence`**, the field's or member's as the
  schema has it, a field left at the default taking its named type's, so a
  binding tells an optional field from a required one.
- **Unions of unions.** `OrderEntry` over what a client sends, `OrderEvent`
  over what the venue sends back, and `TradingMessage` over both, for a
  journal or a gateway that carries either direction.
- **Version 0.** `quotes` covers evolution; the showcase reads better
  without frozen versions. `trading.xml` is rewritten, not frozen.

## What gets built

- **The api.** `BindingContext` gains `@Nullable Presence presence`, null
  for a group and var-data, which have none; `TypeBinding`'s contract says
  when a binding is handed `null`.
- **The rules.** `FaceRules` stops refusing an optional composite, set or
  array field.
- **The codec.** On an optional field whose face has no null value: no
  null check and a straight read with a binding; without one, the refusal
  above on the way out. The context carries the presence.
- **The corpus.** A case with an optional composite, set and array field
  each with a binding writing and reading its null, and each without one,
  its `null` refused.
- **The schema, `trading.xml` and its records:**

  | Message | Direction | What it shows |
  | --- | --- | --- |
  | `NewOrder` (D) | entry | fixed ids and symbol as named `char` strings, `Side`, `OrdType`, `TimeInForce` as char enums over a named encoding type, `ExecInst` as a set, a constant `securityIdSource`, `price` and `stopPx` as optional decimals bound to `BigDecimal`, a quantity composite with exponent 0, `transactTime` as a `uint64` with no `timeUnit` bound to `Instant`, a `parties` group with nested `partySubIds`, explicit `offset`s and `blockLength` with alignment padding |
  | `ReplaceOrder` (G) | entry | `layout` and `unmapped`: a field the venue's schema keeps and the record no longer carries |
  | `CancelOrder` (F) | entry | the smallest message, sharing the named types |
  | `ExecutionReport` (8) | event | `ExecType` and `OrdStatus` with `@UnknownValue`, `lastLiquidity` as an enum bound to a `boolean`, `tradeDate` as a `uint16` of days bound to `LocalDate`, `maturity` as a year-month composite with optional day and week bound to a record of its own, a `fills` group bound to a map keyed by fill id |
  | `CancelReject` (9) | event | `CxlRejReason` as an enum, `text` as var-data in ISO-8859-1 |
  | `Reject` (j) | event | `text` as var-data in UTF-8 |

  - A header of its own, `sequenceNumber` (`uint32`) beside the standard
    four, as order-entry sessions number their messages.
  - The bindings the example writes beside the records: decimals reading
    the exponent from the record's constant member and writing a null
    price as a null mantissa; the timestamp reading `timeUnit` from its
    context, whose absence this schema defines as nanoseconds; the date,
    the year-month, the liquidity flag and the fills map.
- **The proof.**
  - `schema.xml` equals `trading.xml`.
  - The pom runs `SbeTool` over `trading.xml` into `xmlref`, as for
    `quotes`. Every message goes through our codecs into those flyweights
    and back, the header's `sequenceNumber` included, with the edge
    values: a market order's null prices, an empty `fills`, a party
    without sub-ids, full-length ids, an unknown `ExecType` read as the
    unknown constant, text at its maximum length, a character ISO-8859-1
    cannot hold refused.
  - Each union round-trips its messages and refuses the other direction's
    through `canDecode`; a gateway test routes a mixed stream by
    `canDecode` alone; a caller's `switch` over `TradingMessage` takes
    `OrderEntry` and `OrderEvent` as one case each.
  - The existing `CodecsTest`, `FlyweightsTest` and `SchemaResourceTest`
    follow the new schema.
- **The snippets.** An optional composite, set and array field compiled
  clean.
- **The documents.** `intent.md` rewords increment 21 and ticks it;
  `type-mappings.md` on optional faces without a null value and the
  context's presence; the guide's bindings and composites pages, and the
  sets and named-types pages where they refuse an optional field today;
  the example's `AGENTS.md`.

## Criteria

- Every message of `trading.xml` crosses between our codecs and sbe-tool's
  flyweights in both directions, the edge values included.
- A schema with an optional composite, set or array field compiles, and a
  binding carries `null` through it.
- `./mvnw verify` is green on a fresh clone, and the CI job passes on this
  pull request.

## Out of scope

The Simple Open Framing Header, which is framing, big-endian before a
body of either order, and the transport's. A schema claiming to be FIX, or
anything taken from the FIX standard's text. Evolution of `trading`.
