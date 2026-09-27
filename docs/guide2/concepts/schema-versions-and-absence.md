# Schema versions and absence

Applications rarely move to a new schema version at exactly the same time.
A reader may receive messages from older writers, newer writers, or stored
data. sbe-buddy uses SBE's version and layout information to interpret those
messages and represents missing values as Java `null`.

For a refresher on headers, blocks and groups, start with
[How an SBE message is laid out](message-layout.md).

## Three version numbers with different jobs

| Version | What it tells you |
| --- | --- |
| Current schema version | The version declared by `@SbeSchema.version`. Generated codecs encode messages at this version. |
| Acting version | The version in the header of the message being decoded: the writer's schema version. |
| Baseline version | The oldest version this application's codecs accept, selected by `@SbeSchema.baselineVersion`. |

For example, a codec generated at version 2 with baseline 0 can read
version 0 messages. Their acting version is 0, so members introduced in
versions 1 and 2 are absent. Encoding the resulting record still writes
version 2; decoding an older message does not select an older output format.

The baseline is a sbe-buddy setting and does not appear in the XML schema.
It defaults to 0. Raising it is a decision to stop accepting older messages,
including those retained in logs or queues. The codec rejects a message
whose header version is below the baseline.

SBE's optional `semanticVersion` label is separate from these numbers. It
can describe a release, but it does not control encoding or decoding.

## A missing field and an optional value

The [field-addition how-to](../how-to/add-a-field.md) adds an optional
`clientOrderId` at version 1. There are two ways its record component can be
`null`:

- A version 0 writer has no such field. There are no bytes for it in that
  message's fixed block.
- A version 1 writer includes the field but writes its null sentinel. The
  field occupies its usual eight bytes.

Both decode to the same Java value. The record alone does not distinguish
the causes; the message header tells you which version was encoded.

A required field added in version 1 can also be missing from a version 0
message. Its Java component therefore needs to accept `null` while the
baseline is 0. For a numeric field, that means using a boxed type such as
`Long`. The field is still required when encoding a current-version message,
so passing `null` to encode fails. Application code that reads an older
record and writes it again must supply the newly required value.

Raising the baseline to 1 removes this version-based reason for absence,
so a required numeric field introduced at version 1 can use a primitive
again. An optional field still needs a nullable component. Boxing by itself
does not make a field optional.

Constants are a separate case: the schema supplies their values, so the
codec supplies the constant even when decoding an older message.

## Why appending a field works

An SBE message header carries the length of the writer's fixed-length block.
An older reader knows the offsets of its existing fields and can use that
length to get past fields added at the end. A newer reader uses the acting
version to avoid reading fields the older writer did not encode.

This depends on keeping the existing layout intact. A field ID identifies
a field; it does not fix its offset. Reordering fields, changing their
widths or deleting their storage can break existing readers. Incrementing
the version number does not make those changes compatible.

The codec can accept a message whose version is newer than its own schema.
Successful decoding still depends on the changes that version made. For
example, a new enum value needs an unknown-value policy, and the codec
rejects unknown set bits.

## Other constructs have different limits

Repeating group entries also have fixed-length blocks. The group's
dimension header carries their block length, allowing an older reader to
step past fields appended to each entry.

Appending groups or variable-length data needs a separate compatibility
review. An older reader does not know how to skip an unknown variable-length
section. At the message's end, it can stop before newly appended sections
and report a length that excludes them. Inside a group entry, an unknown
section can prevent it from locating the next entry correctly. A transport
that needs to skip an entire unknown message must supply its framing.

For a newer reader, a group or data member missing from an older message
decodes to `null`. A present empty group, string or byte array is a value.
Groups and variable-length data cannot have optional presence, and current
writers must supply non-null values for them.

A composite has a fixed layout and cannot grow in place through
`sinceVersion`. To carry more information, declare a new composite and
append a field of that type, or introduce a new message template. An
optional composite also needs a convention for representing null;
[bindings](application-types-and-wire-representations.md#absence-needs-a-representation-too)
can implement that convention.

## Retiring data while preserving the layout

Deprecation records that a field is no longer intended for use; it does not
remove the field. An `unmapped` declaration keeps the field in the schema
while removing its component from the Java record. The codec skips the
field on decode and writes its null value on encode.

This preserves the field's storage, but a decode-encode cycle loses its
original value. An application that forwards messages must account for
that difference. See [Retire a field from a record](../how-to/retire-a-field.md)
for the steps and compatibility checks.
