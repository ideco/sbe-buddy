# Variable data

An SBE `data` element is variable-length data: a length on the wire followed by that many bytes, text in a character encoding or opaque bytes. In sbe-buddy, `@SbeData` declares it on a `String` or `byte[]` component, and `type` names its encoding, a composite of the `{length, varData}` shape.

## Declaration

The quotes example appends the quoting desk's remark after the contributors group:

```java
@SbeData(id = 22, type = VarStringEncoding.class, sinceVersion = 8, description = "The quoting desk's own words on the quote") @Nullable String remark
```

The schema writes the data inside the message, after its groups, and the encoding among the types:

```xml
<composite name="varStringEncoding">
    <type name="length" primitiveType="uint16"/>
    <type name="varData" primitiveType="char" length="0" characterEncoding="UTF-8"/>
</composite>
```

```xml
<data name="remark" id="22" type="varStringEncoding" sinceVersion="8" description="The quoting desk's own words on the quote"/>
```

`@SbeData` takes `id`, `name`, `type`, `offset`, `semanticType`, `description`, `sinceVersion` and `deprecated`. Data follows every field and every group, in a message and in a group's entry, and a body may hold several, one after another.

## The encodings

The api provides three, each with SBE's conventional wire name and a `uint16` length:

| Class | Wire name | `varData` | Component |
| --- | --- | --- | --- |
| `VarStringEncoding` | `varStringEncoding` | `char`, UTF-8 | `String` |
| `VarAsciiEncoding` | `varAsciiEncoding` | `char`, US-ASCII | `String` |
| `VarDataEncoding` | `varDataEncoding` | `uint8` | `byte[]` |

An encoding of your own is an `@SbeComposite` record of two inline types, `length` and `varData`, the latter of `length = 0`. The corpus's `varBlobEncoding` takes a `uint32` length for payloads beyond 64 KiB, which sbe-tool accepts only with a `maxValue` no greater than `Integer.MAX_VALUE`:

```java
@SbeComposite(name = "varBlobEncoding")
record VarBlobEncoding(
        @SbeType(primitiveType = UINT32, maxValue = "1073741824") long length,
        @SbeType(primitiveType = UINT8, length = 0) byte[] varData) {}
```

The component is the face of the encoding's `varData`: a `String` for a `char`, a `byte[]` for anything else. Any other component is a compile error, `String is not the face of varDataEncoding, which is byte[]`.

## Text

Text is written in its encoding and read back as sbe-tool's flyweight reads it. The codec covers two encodings:

* **UTF-8.** The codec counts the bytes without encoding, so `encodedLength` costs a pass over the characters and no allocation. A lone surrogate, which the JDK would silently write as `?`, is an `IllegalArgumentException`: `note has a lone surrogate at index 1`.
* **ASCII.** A character above 127, which the flyweight would silently write as `?`, is an `IllegalArgumentException`: `symbol is not ASCII: café`.
* **Any other encoding the JDK knows.** The codec encodes the text itself, refusing a character the encoding cannot hold, and a lone surrogate, which the flyweight would write as `?`: `wide cannot be written in UTF-16: a\uD834`. Counting the bytes takes encoding them, so `encodedLength` allocates for such text, and `encode` encodes it again.

An encoding the JDK does not know has no codec; the compiler says so, naming the message, and `codecs = false` on `@SbeSchema` keeps the flyweights.

Decoding is the flyweight's: bytes that are not valid in the encoding decode as the JDK decodes them, U+FFFD for malformed UTF-8, never an exception.

## Length

The length type bounds the data: 254 bytes for a `uint8`, 65534 for a `uint16`, the `maxValue` for a `uint32`. The flyweight refuses more with its own `IllegalStateException`; the codec refuses it first, as the contract's `IllegalArgumentException`, from `encodedLength` and `encode` alike: `signature is longer than 254 bytes: 255`, or for text `note is longer than 65534 bytes in UTF-8: 65536`.

## What data costs

`encodedLength` counts each data member as its length's size plus its bytes, in a message and in every entry of a group, without encoding. `decodedLength` walks the data on the wire without decoding it, through the flyweight's `sbeDecodedLength`, as it walks groups.

## Absence and the empty value

Data has no presence: it cannot be optional, and an empty string or an empty array is a value, a length of zero on the wire. Encoding `null` is an `IllegalArgumentException`, `remark is required`, from `encodedLength` and `encode` alike.

Data appended above the baseline, as `remark` was in version 8, is absent from every earlier message: the codec decodes it to `null`, decided on the acting version, and the component is nullable. A current message always carries it, so encoding still requires it.

An older reader that does not know the data reads the message up to it and stops: it cannot skip data it has no schema for, which is SBE's limit, not sbe-buddy's. The quotes example's version 7 reader reads a current message's contributors and stops before the remark.

## What the compiler and the codec refuse

* A component that is not the face of its encoding's `varData`.
* A field or a group after data in the same body: `a field must come before every group and data`, `a group must come before every data`.
* The codec refuses, naming the message, text in an encoding the JDK does not know. `codecs = false` on `@SbeSchema` keeps the flyweights.

## Coverage

This page covers `data` in messages and in groups' entries, with the api's encodings and your own. `@SbeData(binding = …)` names a [binding](bindings.md) over the `String` or `byte[]`.
