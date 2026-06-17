# CLAUDE.md — springboot-rules-demo

## Authoritative implementation spec

**Build against [`IMPLEMENTATION_CONTEXT_V2.md`](IMPLEMENTATION_CONTEXT_V2.md).**
It is the current source of truth for decisions, package/class layout, and build
order.

`IMPLEMENTATION_CONTEXT.md` (v1) is retained **for history only**. Do not implement
from it — v2 supersedes it (corrected SpEL `Map` root, source-deserialization
handling, synchronous Mongo + virtual threads, single-`process()` topology, locked
value format). See the v2 changelog at the top of that file for the full diff.

When v2 and v1 conflict, v2 wins. If you change a locked decision, update v2 (not
v1) and note it in the v2 changelog.

## Model routing (strict order — every task)

1. **Local LLM does ALL doing.** All tooling, coding, file generation, edits,
   refactors, and test writing route to the local LLM (Unsloth Studio,
   `http://localhost:8888`, check `/api/health`) FIRST. Needs `UNSLOTH_API_KEY`
   from the user per session; never store it.
2. **The orchestrating Opus session ONLY reviews and decides** — planning,
   architecture/design decisions, writing specs, and reviewing local-LLM output.
   Opus does not hand-write implementation when the local LLM is available.
3. **Fallback when the local LLM is unavailable** (down / no key / `chat_only`
   can't serve it): route doing to Sonnet subagents, Haiku for cheap high-volume
   work, Opus only as a last resort — and say so explicitly.

Execution loop: **Opus decides/specs → local LLM does → Opus reviews.** Always
review local-LLM output before trusting it (hallucinated imports, redefined
dependencies, unhandled checked exceptions are the common failures). See global
`~/.claude/CLAUDE.md` §12 for the full policy and the loop tooling.
