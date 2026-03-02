### Scala-CLI runners status ([#1221](https://github.com/scalacenter/scastie/pull/1221))

Scala-CLI runners now report their status alongside SBT runners, with real-time execution indicators and a task queue visible to all users.

### Faster Scala-CLI execution ([#1258](https://github.com/scalacenter/scastie/pull/1258))

Scala-CLI runner now skips unnecessary reloads when directives haven't changed between runs, significantly reducing code execution time.

### Multi-line error diagnostics ([#1227](https://github.com/scalacenter/scastie/pull/1227))

Error diagnostics now support multi-line spans, allowing the editor to accurately highlight errors that cover more than one line.

---

### Improvements

- Enable capture checking syntax support in worksheet mode ([#1268](https://github.com/scalacenter/scastie/pull/1268))

### Bug Fixes

- Fix autocompletion condition bug ([#1257](https://github.com/scalacenter/scastie/pull/1257))
- Fix worksheet mode propagation in Scala-CLI runner ([#1269](https://github.com/scalacenter/scastie/pull/1269))

### Version Bumps

- Scala 3.8.2 ([#1262](https://github.com/scalacenter/scastie/pull/1262))
