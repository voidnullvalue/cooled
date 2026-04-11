# Next Pass 2 Plan (April 11, 2026)

## Audit snapshot (current repo before this pass)

- LZSS implementation already tokenized but kept unresolved `FlagBitOrder` enum ambiguity.
- Start/chunk transfer path existed, but scripted end-to-end behavior coverage was shallow.
- Family-specific advanced program/content workflows were still mostly header-level.
- CoolLEDX/CoolLEDS-specific handling parity was partial.
- Debug feed existed for parsed packets, but raw TX/RX timeline and unknown-frame surfacing were limited.

## Concrete checklist for this pass

### Phase 1: audit and planning
- [x] Re-read protocol reverse-engineering artifacts in `base_apk_protocol_sources.zip` and protocol docs.
- [x] Re-audit unresolved markers/TODO notes.
- [x] Write this `NEXT_PASS_2_PLAN.md` with pass-only scope.

### Phase 2: LZSS ambiguity closure
- [x] Inspect decompiled LZSS implementation for flag-bit shift behavior.
- [x] Lock implementation to confirmed bit ordering if proven.
- [x] Add tests/vectors proving exact literal-flag semantics.
- [ ] Keep explicit note of any remaining codec ambiguity (if unresolved).

### Phase 3: advanced content/program parity
- [x] Add centralized content composer and metadata model.
- [x] Add text/drawing/preset content payload encoders with auditable layouts.
- [x] Wire compression + start header + chunk framing end-to-end in one compose path.
- [x] Expose composed-program upload from repository.

### Phase 4: family-specific closure
- [x] Add explicit advanced color-mode command path (`13 03 <mode>`).
- [x] Add advanced clock reset subcommands for countdown/stopwatch (`0F 02`, `10 02`).
- [x] Keep alternate start-opcode handling available for U-family.
- [ ] Expand deeper CoolLEDX/CoolLEDS content-type semantics once additional vectors are recovered.

### Phase 5: transfer realism
- [x] Extend fake transport scripting beyond loopback.
- [x] Add scripted scenarios: happy, delayed/garbage, nack-then-success, retry exhaustion.
- [x] Surface transfer script loading and transfer-state transitions in debug feed.
- [x] Ensure disconnect/cancel path forces transfer cleanup state.

### Phase 6: observability
- [x] Add timestamped raw TX/RX event stream in transport.
- [x] Surface raw timeline + parsed timeline + unknown/fallthrough distinction in ViewModel events.
- [x] Expose copy/export-ready debug log string from ViewModel.
- [x] Surface family + capability summary in debug event feed.

### Phase 7: tests + docs
- [x] Add focused tests for new composer/builders and transfer scripts.
- [ ] Run unit tests and fix failures. _(Blocked in this environment when Google Maven access is restricted.)_
- [x] Update README / ARCHITECTURE / PROTOCOL_IMPLEMENTATION_NOTES / TESTING.
- [x] Add REAL_DEVICE_VALIDATION.md with exact high-risk hardware checks.
