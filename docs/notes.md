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
  and prints nothing. (Spike experiment, 2026-09.)
- `XmlSchemaParser.parse` never validates against `sbe.xsd`; that is a
  separate `validate(xsdFilename, InputSource, options)`, which wants the
  XSD as a file path and sets no error handler, so the JDK's default prints
  each violation to stderr and carries on. Validate with
  `javax.xml.validation` and `fpl/sbe.xsd` from the jar instead.
  (`XmlSchemaParser.java` and `SbeTool.java`, read 2026-09-20.)
- Every parser error goes through `ErrorHandler.error(String)`, a public
  method of a public non-final class with a public `(PrintStream,
  ParserOptions)` constructor, and `ParserOptions` carries an
  `errorPrintStream`. The node never reaches the handler: `handleError`
  prefixes the message with `at <parent name="P"> <node name="N"> ` from a
  private `formatLocationInfo`. 57 `handleError` sites, about 40 distinct
  rules, in `Message`, `Field`, `CompositeType`, `EncodedDataType`,
  `EnumType` and `XmlSchemaParser`; `MessageSchema.validate` adds
  `sinceVersion` above the schema version. (Read 2026-09-20.)
- `MessageSchema`'s constructors are package-private and `Message`,
  `CompositeType`, `EnumType` and `SetType` are built from DOM nodes, so
  `parse(InputSource)` is the only way to obtain a `MessageSchema`.
  (Read 2026-09-20.)
- `uk.co.real_logic.sbe.ir.GenerationUtil` (`collectFields`,
  `collectGroups`, `collectVarData`) and
  `uk.co.real_logic.sbe.generation.java.JavaUtil` (`formatPropertyName`,
  `formatClassName`, `formatGetterName`) are public; `JavaGenerator`
  derives every flyweight member name through them. (Read 2026-09-20.)
- `IrEncoder` and `IrDecoder` read and write the `.sbeir` file `SbeTool`
  accepts in place of XML and emits under `-Dsbe.generate.ir=true`.
  `IrEncoder` imports `org.agrona.concurrent.UnsafeBuffer`, so writing an
  IR file inside javac needs the Agrona JVM flag. `IrGenerator`, `Ir`,
  `Token` and `Encoding` import only `org.agrona.Verify`; `JavaGenerator`
  refers to the `DirectBuffer` and `MutableDirectBuffer` interfaces by
  class literal, and whether loading those touches `UnsafeApi` is
  unverified. (Read 2026-09-20.)
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
- From JDK 23 javac performs no annotation processing unless `-processor`,
  `--processor-path` or `--processor-module-path` is set, or `-proc` is
  `only` or `full`; discovery from the compile classpath is gone. Reaching
  the processor through `annotationProcessorPaths`, as the example does,
  sets `--processor-path` and is unaffected.
  (`maven-compiler-plugin:4.0.0-beta-5` plugin descriptor, `proc` and
  `annotationProcessorPaths`, read 2026-09-20.)

## Error Prone 2.50.0

- On JDK 21 Error Prone refuses to run without
  `-XDaddTypeAnnotationsToSymbol=true` and fails the compilation with that
  sentence as the whole message. (Spike experiment, 2026-09-20.)

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
