# Notes

Verified facts about sbe-tool, javac, Agrona and the build that the
implementation relies on, especially the surprising ones. Each entry says how it was
verified. Nothing else goes here: no design discussion, no to-dos, no
alternatives considered.

## sbe-tool 1.40.2

- `JavaGenerator` calls `setPackageName` on its output manager only for the
  types-package override. The caller must call
  `setPackageName(ir.applicableNamespace())` before `generate()`, or every
  file is named `null.<Class>` although its `package` line is right.
  (Spike experiment, 2026-09; re-check when increment 3 relies on it.)
- `JavaGenerator.generate()` opens `MessageHeaderEncoder` and
  `MessageHeaderDecoder` twice, once from `Ir.types()` and once as the
  header stub, with identical content. An output manager over `Filer` must
  treat the second open as a no-op, because `Filer` cannot recreate a file.
  (Spike experiment, 2026-09.)
- `XmlSchemaParser` with `stopOnError`, `warningsFatal` and
  `suppressOutput` throws `IllegalArgumentException` from a schema warning
  and prints nothing. Parse the oracle with those options. (Spike
  experiment, 2026-09.)
- `IrGenerator` names an `ENCODING` token after its `<type>`: a field of a
  named type and a field of the bare primitive give different IR for
  identical bytes. (Spike experiment, 2026-09.)
- Byte order is set only on `ENCODING` tokens; every `BEGIN_*` and `END_*`
  token keeps `Encoding`'s default. (Spike experiment, 2026-09.)
- The decoder's `wrap(buffer, offset, actingBlockLength, actingVersion)`
  sets `limit = offset + actingBlockLength`, so an older decoder skips
  fields a newer version appended to the block. (`JavaGenerator.java`,
  message `wrap`, read 2026-09-20.)
- Below the acting version, a guarded getter returns the encoding's
  applicable null value for a primitive, `NULL_VAL` for an enum, count zero
  for a group, and wraps var-data with length zero. Encoders have no
  version guard. (`JavaGenerator.java`, `generateFieldNotPresentCondition`
  and the group and var-data guards, read 2026-09-20.)
- The parser checks `sinceVersion <= messageSchema.version` on messages,
  types and valid values (`MessageSchema.java`) but does not check that
  later-version fields follow earlier ones; offsets are computed in
  declaration order (`Message.computeAndValidateOffsets`). (Read
  2026-09-20.)
- `semanticVersion` is a free string emitted as the `SEMANTIC_VERSION`
  constant; `deprecated` marks the generated member `@Deprecated`.
  (`JavaGenerator.java`, read 2026-09-20.)

## javac

- No annotation processor runs after an error: a negative compile test sees
  only the diagnostics of the round that failed. (Spike, 2026-09.)
- `Filer.createSourceFile` throws `FilerException` for a name already
  created in the same compilation. (Spike, 2026-09.)
- `Messager` cannot position an annotation *value* on a record component;
  the diagnostic lands on the annotation. (Spike, 2026-09.)

## Agrona 2.6.1

- `UnsafeApi` in the sources jar is a stub; the shipped bytecode calls
  `jdk.internal.misc.Unsafe` directly, so any JVM that loads an Agrona
  buffer class needs `--add-opens java.base/jdk.internal.misc=ALL-UNNAMED`.
  javac needs it only if the processor touches a buffer. (Sources jar,
  read 2026-09-20.)

## Maven 4.0.0-rc-6

- `.mvn/jvm.config` is parsed by `bin/JvmConfigParser.java`, which strips
  everything from a `#` to the end of the line and expands
  `${MAVEN_PROJECTBASEDIR}`. The `only-script` wrapper execs
  `$MAVEN_HOME/bin/mvn`, so `./mvnw` inherits that; the classic wrapper
  joins the file with `tr` and passes the comment to the JVM, which fails
  with `Could not find or load main class #`. (`bin/mvn`,
  `bin/JvmConfigParser.java` and both `mvnw` variants, read 2026-09-20.)
- `bin/mvn` captures that parser's stderr into `MAVEN_OPTS` and `eval`s it,
  so a JVM that prints a banner, as any JDK does when `JAVA_TOOL_OPTIONS` is
  set, breaks the launcher before Maven starts. CI and a plain workstation
  are unaffected; a container that sets it must pass those flags in
  `MAVEN_OPTS` instead. (Spike experiment, 2026-09-20.)
