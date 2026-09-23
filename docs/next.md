# Increment 16: Var-data

## Goal

A message or a group entry ends in var-data: a length on the wire followed by
that many bytes, text in a character encoding or opaque bytes. The mapping,
the XML and sbe-tool's flyweights have carried `@SbeData` since increment 4;
the codec refuses it. This increment gives the codec var-data wherever the
schema allows it, through the api's three built-in encodings and any
`{length, varData}` composite of the user's own, and makes `encodedLength`
count it without encoding, as it counts groups.

## Settled before it started

- The face is decided by the `varData` type: a `char` is a `String` in its
  `characterEncoding`, anything else a `byte[]`, as `FaceRules` already
  reads a type of `length = 0`. A `uint8` given a `characterEncoding` is
  still bytes: its flyweight has the byte form too, and the composite's own
  record says `byte[]`.
- Data has no presence. It is never `null` on the way out, so a `null`
  component is an `IllegalArgumentException`, `note is required`, from
  `encodedLength` and `encode` alike, as a group's list is; an empty string
  or array is a value. Data appended above the message's baseline decodes to
  `null` from an older message, decided on the acting version.
- Text is ASCII or UTF-8. ASCII goes through the existing `ascii` check;
  UTF-8 is counted by a helper of its own without encoding, and a lone
  surrogate, which `String.getBytes` would silently turn into `?`, is an
  `IllegalArgumentException`. Text in any other encoding is a construct the
  codec lacks, as a fixed-length string outside ASCII already is.
- Over the length type's maximum is the codec's `IllegalArgumentException`
  before the flyweight's `IllegalStateException`. The maximum is the
  length's `lengthMaxValue()` on the encoding's own composite flyweight, the
  same `applicableMaxValue` the message's flyweight checks, so the codec
  holds no wire number.
- Decoding is the flyweight's: text through its `String` form, bytes through
  `get<Data>` into an array sized by `<data>Length()`. Bytes that are not
  valid in the encoding decode as the JDK decodes them.

## What gets built

- **`CodecModel`.** `Member.Data(component, property, path, content,
  addedSince)`, with `enum Content { BYTES, ASCII, UTF_8 }`; and
  `Helper.DataMethods(data, encoder, decoder, bulk, lengthEncoder)`, the
  length, write and, for bytes, read methods of one data member, keyed by
  its path as a group's are. Each content's check is a helper shared by
  the codec: the existing `Ascii`, and `Utf8` and `Bytes`, each refusing
  over the maximum.
- **`CodecWalk`.** The var-data tokens after the groups become `Data`
  members, found by wire name among the components. Data appended above
  the baseline inside a group is a construct the codec lacks, as a field
  or a group appended there is.
- **`CodecWriter`.** A body's variable part, its groups and then its data,
  is read into locals in wire order before the constructor; `encodedLength`
  adds a term per data member at the message and per entry in a group; the
  walked `decodedLength` covers data as it covers groups.
- **`FaceRules`.** `data(Annotated.Data)`: the component is the face of its
  encoding's `varData`, replacing `Mapping`'s `data must be a String or a
  byte[]`, which let a `String` reach a flyweight with no `String` form.
- **The corpus.** `vardata` gets its codecs and its round trips: empty and
  full, a data member inside a group's entry, the `uint8` length's maximum.
  A new case, `varencodings`, uses the api's `VarStringEncoding`,
  `VarAsciiEncoding` and `VarDataEncoding`, one appended in version 1, with
  the refusals: `null`, over the maximum, a lone surrogate, a non-ASCII
  character.
- **The example.** `quotes` version 8 appends a var-data remark, freezing
  `quotes-v7.xml` with its reference flyweights; `trading`, whose `NewOrder`
  ends in a note, gets its codecs.
- **The documents.** `type-mappings.md`'s faces for data; the guide's
  variable data page; `notes.md` on the var-data flyweights; `intent.md`
  ticks 16.

## Criteria

- Every var-data round trip passes the whole codec contract, the lengths
  included, and the refusals are the codec's `IllegalArgumentException`s.
- The quotes reference tests cross version 7 and 8 in both directions:
  sbe-tool's flyweights read our var-data, and our codec reads theirs.
- `./mvnw verify` is green on a fresh clone, and the CI job passes on this
  pull request.

## Out of scope

Var-data appended inside a group, with fields and groups appended there, in
increment 18. Text in encodings other than ASCII and UTF-8. Bindings on
var-data. Byte order and header types, increment 17.
