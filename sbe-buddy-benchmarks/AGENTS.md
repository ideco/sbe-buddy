# sbe-buddy-benchmarks

JMH benchmarks of the example's codecs. Measurement, not proof: nothing
here asserts, and it is never deployed.

```
TradingBenchmark   decode and encode of Samples.FILL and Samples.LIMIT_ORDER through their codecs
```

- Run by hand, never by `verify`, which only builds it:
  `./mvnw -pl sbe-buddy-benchmarks -am package && java -jar sbe-buddy-benchmarks/target/benchmarks.jar`.
- It reads the example's codecs and its `Samples`, from the example's test
  jar; a value worth measuring is a sample there first, so the tests hold it
  too.
- A change that claims a speed-up or risks a slowdown in the codecs or the
  flyweights runs it on `main` and on the branch, back to back on one
  otherwise idle machine, and puts both tables in the pull request.
- JMH's annotation processor sits on the compiler's processor path beside
  the parent's; the shade plugin makes `benchmarks.jar` with JMH's `Main`.
