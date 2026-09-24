# sbe-buddy-api

What users compile against: the annotations, `PrimitiveType`, `Presence`,
`ByteOrder`, SBE's framing composites, `Codec`, `TypeBinding` and
`BindingContext`. Everything here is public API, so a change updates
`type-mappings.md` and the guide in the same commit.

- No built-in bindings and no wire types for JDK types: whatever the api
  picked, a unit, an epoch, a layout, would be someone's wrong choice. The
  framing composites are here because every schema needs them in some form.
- Annotation members are typed as the XSD types them: enumerations are enums,
  type references are classes, required attributes are required members. Most
  of sbe-tool's name-resolution errors then cannot be written.
- A `String` member is absent only when empty. An enum or number left at the
  XSD's default reaches the model as absent and is omitted from the XML.
- Annotations are retained at `CLASS`, so a declared type in a library jar
  still resolves, and nothing exists at runtime to reflect over.
- Only generated code implements `Codec<T, H>`, so it may grow. `H` is the
  schema's header, a record implementing `MessageHeader`.
- Agrona is the only runtime dependency. JSpecify is `optional`, so users do
  not inherit it.
