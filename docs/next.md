# Increment 1: skeleton and the oracle wiring

## Goal

A fresh clone builds green on JDK 25 with the three modules in place, the
example schema exists as both records and XML, sbe-tool generates the
reference flyweights from the XML during the build, and one test proves the
reference produces the bytes we expect. The processor compiles and is wired
in but generates nothing yet. Increments 2 and 3 build on this.

## What gets built

**Repository root**
- `pom.xml`: groupId `net.concini`, artifactId `sbe-buddy`, version
  `0.1.0-SNAPSHOT`, packaging `pom`, modules in the order `sbe-buddy-api`,
  `sbe-buddy-processor`, `sbe-buddy-example` (the example reaches the
  processor only through `annotationProcessorPaths`, so declaration order is
  what orders the reactor). Properties: `maven.compiler.release` 25, UTF-8,
  versions for agrona 2.6.1, sbe-tool 1.40.2, junit 5.14.4, assertj 3.27.7.
  `dependencyManagement` for those plus the two library modules.
  `pluginManagement`: maven-compiler-plugin 3.16.0 with
  `-Xlint:all,-processing -Werror`; maven-surefire-plugin 3.6.0 with
  `argLine` `--add-opens java.base/jdk.internal.misc=ALL-UNNAMED
  --sun-misc-unsafe-memory-access=allow`; spotless-maven-plugin 3.10.2 with
  google-java-format 1.36.1 over `src/*/java/**/*.java` and `check` bound
  to verify; exec-maven-plugin 3.6.4; build-helper-maven-plugin 3.6.2;
  maven-jar-plugin 3.5.1. No other plugin.
- `.mvn/wrapper/maven-wrapper.properties` for Maven 3.9.16, `mvnw`,
  `mvnw.cmd`; `.gitignore` (`target/`, `.idea/`, `.claude/settings.local.json`);
  `.gitattributes` (`mvnw` LF, `*.cmd` CRLF).
- `.github/workflows/verify.yml`: on pull request and push to `main`, one
  job, `actions/checkout@v7`, `actions/setup-java@v6` with Temurin 25 and
  Maven cache, `./mvnw -B -ntp verify`.
- `README.md`: the first paragraph of `intent.md` and the build command.

**`sbe-buddy-api`** (`net.concini.sbebuddy`, `Automatic-Module-Name` the same)
- `@SbeSchema(int id, int version)` on `PACKAGE`; `@SbeMessage(int id)` on
  `TYPE`; `@SbeField(int id)` on `RECORD_COMPONENT`. All `@Retention(CLASS)`,
  `@Documented`. Javadoc on each id: hand-assigned, never reused.
- `PrimitiveType` enum: `CHAR, INT8, INT16, INT32, INT64, UINT8, UINT16,
  UINT32, UINT64, FLOAT, DOUBLE`.
- `Codec<T>` exactly as in `intent.md`.
- Dependency: agrona. Test dependency: none yet.

**`sbe-buddy-processor`** (`net.concini.sbebuddy.processor`)
- `BuddyProcessor extends AbstractProcessor`: supports the three annotations,
  `SourceVersion.latestSupported()`, `process` returns `false` and does
  nothing. Registered in `META-INF/services/javax.annotation.processing.Processor`.
  Compiled with `<proc>none</proc>` so its own registration is not picked up.
- Package `net.concini.sbebuddy.generator` with `Output` only:
  `Writer source(String pkg, String simpleName) throws IOException`.
- Dependencies: `sbe-buddy-api`, sbe-tool. Test: junit, assertj.

**`sbe-buddy-example`** (not deployed: `maven.deploy.skip`)
- `src/main/java/com/example/trading/package-info.java` with
  `@SbeSchema(id = 1, version = 0)`; `PlaceOrder(long accountId, int quantity)`
  with `@SbeMessage(id = 1)` and field ids 1 and 2.
- `src/main/sbe/trading.xml`: `package="com.example.trading.sbe"`, id 1,
  version 0, `byteOrder="littleEndian"`, the standard `messageHeader`
  composite, message `PlaceOrder` id 1 with `accountId` `int64` id 1 and
  `quantity` `int32` id 2.
- exec-maven-plugin at `generate-sources`, main class
  `uk.co.real_logic.sbe.SbeTool`, argument the XML path, system properties
  `sbe.output.dir=${project.build.directory}/generated-sources/sbe-xml` and
  `sbe.target.namespace=com.example.trading.xmlref`, sbe-tool as a plugin
  dependency only, `includeProjectDependencies` false. build-helper adds that
  directory as a source root.
- maven-compiler-plugin with `annotationProcessorPaths` naming
  `sbe-buddy-processor` `${project.version}`.
- Dependencies: `sbe-buddy-api`; test: junit, assertj.
- One test, `ReferenceBytesTest`: encode `PlaceOrder(0x0102030405060708L,
  0x11121314)` with the xmlref `MessageHeaderEncoder` and
  `PlaceOrderEncoder` at offset 0 of an `ExpandableArrayBuffer` and assert
  the 20 bytes are exactly
  `0C 00 01 00 01 00 00 00` (blockLength 12, templateId 1, schemaId 1, version 0)
  followed by `08 07 06 05 04 03 02 01` and `14 13 12 11`.

## Criteria

- `./mvnw verify` is green on a fresh clone with JDK 25 and nothing else
  installed; CI runs the same command and passes.
- `sbe-buddy-example/target/generated-sources/sbe-xml/com/example/trading/xmlref/PlaceOrderEncoder.java`
  exists after the build, and `ReferenceBytesTest` passes.
- `sbe-buddy-example` compiles with `BuddyProcessor` on its processor path
  (visible in `./mvnw -X compile` as `-processorpath`), and sbe-tool is
  absent from its compile and test classpath (`./mvnw dependency:tree`).
- No warnings: the build runs with `-Werror` and Spotless `check`.
- Root pom under 130 lines; each module pom under 80.

## Out of scope

The model, the IR builder, any generated output, any validation, ArchUnit,
`sinceVersion` members on the annotations, `TypeBinding`, built-in bindings,
a `RoundTrip` helper. Increment 2 starts the generator; increment 3 makes the
mapper work.
