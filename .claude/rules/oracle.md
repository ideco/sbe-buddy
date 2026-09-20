---
paths:
  - "**/src/main/sbe/*.xml"
---

The XML schema next to the example records is the oracle: it is the hand-written SBE equivalent the records are proven against, and it grows only together with the record change that needs it. Never edit it to make a failing test pass; a mismatch means the records or the generator are wrong. A frozen earlier version (`*-v0.xml`, `*-v1.xml`, ...) is never edited at all.
