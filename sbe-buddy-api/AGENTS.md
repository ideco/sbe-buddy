# sbe-buddy-api

What users compile against: the annotations, `PrimitiveType`, `Presence`,
`ByteOrder`, the built-in composites, `Codec` and `TypeBinding`. Everything
here is public API, so a change updates `type-mappings.md` and the guide in
the same commit.

- Annotation members are typed as the XSD types them: enumerations are enums,
  type references are classes, required attributes are required members. Most
  of sbe-tool's name-resolution errors then cannot be written.
- A `String` member is absent only when empty. An enum or number left at the
  XSD's default reaches the model as absent and is omitted from the XML.
- Annotations are retained at `CLASS`, so a declared type in a library jar
  still resolves, and nothing exists at runtime to reflect over.
- Only generated code implements `Codec<T>`, so it may grow.
- Agrona is the only runtime dependency. JSpecify is `optional`, so users do
  not inherit it.
