> **Status: Historical/superseded.**
>
> This file is retained for traceability only. It was superseded by the follow-up pass and subsequent repo audit. Do **not** use it as the active execution plan.

# Next Pass Plan (historical)

## Audit outcome against this historical checklist

### Items that were later completed
- Parser moved from generic-only responses to typed models for documented core/clock/transfer families.
- LZSS moved from unresolved/pass-through planning state to implemented tokenized codec.
- Family-aware start-header/chunk builders and advanced clock/mode commands were added.
- Transfer session state machine and fake transport scripting were added.
- Unit coverage expanded beyond frame/CRC/chunks to include parser/LZSS/builders/transfer/fake-transport paths.

### Items still genuinely unresolved
- Full semantic naming and behavioral parity for every advanced OEM `programType` branch.
- Broad hardware validation/performance tuning across CoolLEDX/CoolLEDS/iLedClock and unstable BLE conditions.

## Pointer to active status docs
Use these instead:
- `docs/IMPLEMENTATION_PLAN.md` (current implementation-status summary)
- `docs/REAL_DEVICE_VALIDATION.md` (current hardware validation checklist)
- `docs/TESTING.md` (current test coverage and environment caveats)
