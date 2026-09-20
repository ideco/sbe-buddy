---
paths:
  - "**/src/main/sbe/*.xml"
  - "sbe-buddy-generator/src/test/java/**/corpus/*.java"
---

An XML schema next to the example records, and the `XML` text block in each corpus case, is hand-written SBE that the code is proven against: it grows only together with the change that needs it. Never edit one to make a failing test pass; a mismatch means the model, the writer or the records are wrong. A frozen earlier version (`*-v0.xml`, `*-v1.xml`, ...) is never edited at all.
