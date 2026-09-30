# Domain Docs

How the engineering skills should consume this repo's domain documentation when exploring the codebase.

## Before exploring, read these

- **`CONTEXT.md`** at the repo root: the glossary.
- **`docs/adr/`**: this repo's ADRs. Start from `index.md` and read the decisions that touch the area you're about to work in.
- Domain rules (주문 상태, 쿠폰, 재고, etc.) live in **`docs/business-rules.md`**, not in the glossary.

If any of these files don't exist, **proceed silently**. Don't flag their absence; don't suggest creating them upfront.

## File structure

Single-context repo:

```
/
├── CONTEXT.md
├── docs/adr/
│   ├── index.md
│   ├── 0001-order-idempotency.md
│   └── 0002-order-item-optimistic-lock.md
└── api/
```

New decisions follow the existing `NNNN-slug.md` numbering and get a row in `docs/adr/index.md`.

## Use the glossary's vocabulary

When your output names a domain concept (in an issue title, a refactor proposal, a hypothesis, a test name), use the term as defined in `CONTEXT.md`. Don't drift to synonyms the glossary explicitly avoids.

If the concept you need isn't in the glossary yet, that's a signal: either you're inventing language the project doesn't use (reconsider) or there's a real gap (note it for `/domain-modeling`).

## Flag ADR conflicts

If your output contradicts an existing decision, surface it explicitly rather than silently overriding:

> _Contradicts ADR-0004 (horizontal scale-out), but worth reopening because…_
