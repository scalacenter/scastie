<!-- version: 2026-02-18 -->

### Japanese translation ([#1243](https://github.com/scalacenter/scastie/pull/1243))

Scastie now supports Japanese as a UI language. Thanks to [@windymelt](https://github.com/windymelt) for this contribution!

---

### Actionable diagnostics ([#1238](https://github.com/scalacenter/scastie/pull/1238))

Scastie now supports actionable diagnostics — click on suggested fixes to apply them directly to your code.

![Actionable diagnostics demo](https://github.com/user-attachments/assets/d9a896eb-123c-45d9-be5a-5795245c62ff)

---

### Better build error reporting ([#1235](https://github.com/scalacenter/scastie/pull/1235))

All BSP log messages are now captured and forwarded to the console, so you can see full output. This enables using flags like `-Vprint` to inspect intermediate compilation phases.

![Vprint flag demo](https://github.com/user-attachments/assets/79eb7491-715e-475c-8358-e12f746221c0)

---

### Bug Fixes
- Fix signature help spam by caching active parameter ([#1220](https://github.com/scalacenter/scastie/pull/1220))
- Fix libraries not loading in Build Settings ([#1240](https://github.com/scalacenter/scastie/pull/1240))
- Fix download button ([#1231](https://github.com/scalacenter/scastie/pull/1231))
- Fix nightly version resolution ([#1245](https://github.com/scalacenter/scastie/pull/1245))
- Prevent duplicate snippet URLs when saving without changes ([#1242](https://github.com/scalacenter/scastie/pull/1242))
- Prevent stale diagnostics after version change ([#1247](https://github.com/scalacenter/scastie/pull/1247))
- Fix language in embed mode ([#1233](https://github.com/scalacenter/scastie/pull/1233))

### Improvements
- Per-user presentation compiler caching ([#1224](https://github.com/scalacenter/scastie/pull/1224))
- Consistent warning display with compilation info cache ([#1169](https://github.com/scalacenter/scastie/pull/1169))

### Version Bumps
- Scala 3.8.2-RC3 ([#1253](https://github.com/scalacenter/scastie/pull/1253))
