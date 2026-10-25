# pgvector in local-env's PostgreSQL (ETP-5136)

Goal: a `local-env` environment whose PostgreSQL has the `vector` extension, so the Java Copilot knowledge base
(`etcop_kb_chunk`, see [design-kb.md](parity/design-kb.md)), `MemoryTool` and Etendo's own vector search
(`com.etendoerp.db.extended`, `etarc_vsrc_*`) can be developed and exercised locally. Today they cannot: the KB
module script logs `pgvector not available` and skips, and agents with a knowledge base report that on every sync.

This plan targets the `etendo-localenv` repo (`bin/local-env.sh`, `docs/design.md`). It is written here, in the
copilot module, because the finding came from this ticket; move it to the local-env repo when the work starts.

## 1. Why it is missing

`bin/local-env.sh` downloads the **zonky** portable build `embedded-postgres-binaries-darwin-arm64v8:16.15.0`
(62 MB download, 297 MB unpacked in `~/.cache/etendo-localenv/bin/pg-16.15.0-darwin-arm64v8`). It contains
`initdb`, `pg_ctl`, `postgres` and the stock contrib modules only. There is no `pg_config`, no `include/`, no
`psql`, and no `vector` extension. Nothing in the tool installs extensions, and nothing could build one: pgvector
is a C extension and needs the server headers plus PGXS.

## 2. Findings (verified on this machine, 2026-10-24)

| Fact | Evidence |
|---|---|
| Etendo supports PostgreSQL **16.x and 17.x**, not 18 | docs.etendo.software requirements page ("PostgreSQL 16.x, 17.x, Amazon RDS"); the API-changes page lists 17 as the newly supported version |
| The cached zonky tree has no headers and no `pg_config` | `ls ~/.cache/etendo-localenv/bin/pg-16.15.0-darwin-arm64v8` shows `bin lib share`; `bin` has 3 programs |
| The `pgx` engine already in local-env ships pgvector | `~/.cache/etendo-localenv/bin/pgx-0.1.0-beta.1/share/extension/vector--0.8.5.sql`; `docs/design.md` §6.10 says so; the PGX README says "only the built-in contrib modules exist, pgvector among them" |
| PGX is PostgreSQL 18 compatible, beta, AGPL-3.0, "for disposable databases" | PGX README; local-env design §6.10 ("Not yet tried: Tomcat and the SPA against PGX, update.database on a live PGX DB") |
| theseus-rs publishes full relocatable PG 16/17 builds for `aarch64-apple-darwin` (~89 MB) that DO contain `bin/pg_config`, `bin/psql`, `include/server/postgres.h` and `lib/pgxs` | downloaded `postgresql-16.8.1-aarch64-apple-darwin.tar.gz` and listed it |
| pgvector 0.8.1 compiles against that tree and loads into the **zonky 16.15** server unchanged | built it, copied `vector.dylib`, `vector.control`, `vector--*.sql` into a copy of the zonky tree, started that server, `CREATE EXTENSION vector` and `'[1,2,3]'::vector <=> '[3,2,1]'::vector` returned `0.2857` |
| Homebrew's `pgvector` formula only builds for `postgresql@17` and `@18` | formulae.brew.sh |
| Other pgvector-bundled engines are PG 18: `pg0` (PG 18 + pgvector 0.8.5), `embedded-postgres` on PyPI (PG 18.6 + pgvector 0.8.6, macOS arm64 wheels) | their READMEs |
| zonky has no pgvector variant | `zonkyio/embedded-postgres-binaries` build files; pg-embed's README says third-party extensions are not in the precompiled binaries |

Two build gotchas found while compiling (they matter for the CI script):

1. The theseus `pg_config` bakes in the CI machine's SDK: `-isysroot /Applications/Xcode_16.4.app/.../MacOSX15.5.sdk`
   and `/opt/homebrew/opt/icu4c@78/include`. Fix: `make PG_SYSROOT=$(xcrun --show-sdk-path)`.
2. That build was configured `--with-llvm`, so PGXS tries `/opt/homebrew/opt/llvm@16/bin/clang` for JIT bitcode.
   Fix: `make with_llvm=no`. The server does not need the bitcode (JIT is not used).

The working build line (`vector.dylib` is 183 KB):

```bash
make PG_CONFIG=$THESEUS/bin/pg_config PG_SYSROOT=$(xcrun --show-sdk-path) with_llvm=no
make PG_CONFIG=$THESEUS/bin/pg_config PG_SYSROOT=$(xcrun --show-sdk-path) with_llvm=no install
```

### Finding for the Copilot KB code (fixed in code; not yet verified live)

The original code created `embedding vector NOT NULL` without a dimension and then attempted a
dimensionless HNSW index, which pgvector rejects with `column does not have dimensions`. Mixed
dimensions also fail at search with `different vector dimensions 2 and 3`. The code now keeps the
unconstrained vector column, records model identity and dimensions per row, drops the broken
index, and creates best-effort partial expression HNSW indexes for dimensions up to 2000. Search
preflights stored model/dimension counts, filters/casts to matching vectors, and reports
incompatible or partially re-indexed data clearly. The local PostgreSQL used here does not have
pgvector, so these code changes have not been exercised against a live extension yet.

## 3. Options

| # | Option | Etendo-supported PG | Effort | Risk | Verdict |
|---|---|---|---|---|---|
| A | **Keep zonky PG 16.15 and overlay a prebuilt pgvector** (3 file groups into the cached tree), built once in CI from the theseus PG16 headers | yes (16) | small | ABI across 16.x builds (verified once; keep a smoke test) | **Recommended** |
| B | Switch the engine to theseus-rs PG 16/17 full builds and build pgvector in place | yes (16/17) | medium (new binary source, `psql` and headers come for free, 89 MB vs 62 MB, changes every snapshot key) | larger blast radius than A | Good second step: gives `psql` too (today a libpq install is required) |
| C | Use the existing `LOCALENV_PG_ENGINE=pgx` | **no** (PG 18 compatible, beta) | none to enable | Tomcat, SPA and `update.database` never tried on it; AGPL; "disposable databases" | Useful as an experiment, not as the default |
| D | `pg0` / `embedded-postgres` | no (PG 18) | medium | outside Etendo support | No |
| E | Homebrew `postgresql@17` + `brew install pgvector` | yes (17) | small per dev | machine-specific, not reproducible, bypasses the cache and snapshots | No |
| F | Docker `pgvector/pgvector:pg16` | yes | small | local-env avoids Docker on purpose (Docker holds 8080/8081); not portable to the snapshot model | No |

## 4. Recommended design (option A)

1. **Build script** `scripts/build-pgvector.sh <pg-major> <pgvector-version> <target>` in `etendo-localenv`:
   download the theseus PG tarball for the major and target (checksum verified), download pgvector's release
   tarball (checksum pinned), build with the flags above, and package `vector.dylib|so`, `vector.control`,
   `vector--*.sql` as `pgvector-<ver>-pg<major>-<target>.tar.gz` plus a `.sha256`. Targets: `darwin-arm64`,
   `darwin-amd64`, `linux-amd64`, `linux-arm64` (Linux needs the matching theseus gnu target and `make` with the
   distro toolchain; the build runs in CI, not on developer machines).
2. **Publish** the artifact next to the existing release assets, and let the cache server mirror it like it does the
   PostgreSQL and Tomcat downloads.
3. **`local-env.sh`**: `pgvector_ensure()` next to `pgx_ensure_binaries()`: download and checksum-verify the
   artifact, copy the three file groups into `$PG_HOME/lib/postgresql` and `$PG_HOME/share/postgresql/extension`.
   Idempotent, runs before the first `initdb`/start of an environment, skipped for the `pgx` engine (it has its own).
4. **Snapshot key**: today the key carries `pg-16.15.0`. Add the pgvector version (`pg-16.15.0+vector0.8.7`).
   A snapshot taken with the extension installed cannot start on a tree without `vector.dylib`; the new key
   keeps those apart exactly as `pg pgx-<version>` already does.
5. **No change to Etendo's flow**: `com.etendoerp.db.extended` and the Copilot module script run `CREATE EXTENSION`
   themselves when pgvector is available (`VectorCapabilityService` / `KnowledgeBaseSchemaProvisioning.hasPgVector`).
   Local-env only has to make the files available.
6. **Pin policy**: pgvector latest per Homebrew is 0.8.7; pin it, bump with PostgreSQL minor bumps, never float.

## 5. Acceptance (what proves it works)

1. `local-env up --fresh` from an empty cache: `select extname, extversion from pg_extension` is empty at first,
   `CREATE EXTENSION vector` succeeds, version equals the pin.
2. `update.database` with the Copilot module: `etcop_kb_chunk` exists with the fixed-dimension column and both
   indexes (after the KB fix in §2).
3. Index a small `ETCOP_APP_SOURCE` through `SyncAssistant` and search with `KnowledgeBaseSearch`; the result
   ranks the right chunk; startup sync marks KB agents synchronized.
4. Etendo's `etarc_vsrc_*` vector search functions exist (they are only created when pgvector is present).
5. Restore a snapshot made with the extension on the next `up`, and confirm a snapshot from before (older key) is
   not reused.
6. The same smoke on `darwin-amd64` and `linux-amd64` in CI (the Linux runners are the proof for the shared cache).

## 6. Risks and open points

- ABI: pgvector built on PG 16.8.1 headers loaded fine into 16.15 (verified). The minor gap is small, but the CI
  build should use the zonky minor's matching theseus build when one exists, and the smoke test above is the guard.
- theseus' macOS builds are configured with LLVM/ICU from Homebrew paths, hence the two overrides; a future
  release may change that, the script must fail loudly if `vector.dylib` is not produced.
- Linux artifacts were not built here (no Linux machine); the plan assumes the same PGXS route works with the
  theseus gnu targets.
- PGX stays an experiment. If Etendo moves its supported matrix to PG 18, option C or the theseus PG 18 builds
  become the natural base and this plan collapses to "bump the engine".

## 7. Verified live on 2026-10-24 (overlay applied to the shared cache tree)

The theseus-built pgvector 0.8.7 (`vector.dylib` 203 KB, `vector.control`, `vector--*.sql`) was added to
`~/.cache/etendo-localenv/bin/pg-16.15.0-darwin-arm64v8/{lib/postgresql,share/postgresql/extension}` (additive; no
existing file touched). The environment `ETP-5136-min` (core develop, copilot + toolpack + devassistant +
webhookevents) then ran the Java knowledge base against a mock OpenAI-compatible embeddings server
(`parity/fixtures/kb_mock_embeddings.py`, dimension switchable at runtime) linked through the new
`ETCOP_API_KEY` -> `etendo-embeddings` model structure. Seed: `parity/fixtures/kb-live-seed.sql`, documents under
`parity/fixtures/docs/`.

| Check | Result |
|---|---|
| Extension created by the Copilot module script (system user), table and scope indexes created by `update.database` | OK (0.8.7) |
| Startup sync indexes two sources through the real indexer | OK: 2 chunks, model `etendo/etendo-embeddings`, 16 dims |
| Partial HNSW index created per dimension | OK: `etcop_kb_hnsw_16`, later `_32` |
| Search through the real `KnowledgeBaseSearch` tool over MCP direct mode | OK: exact text distance 0, other source behind |
| Embedding dimension changed 16 -> 32 without re-index | OK: readable message `stored etendo/etendo-embeddings/16=2; current .../32. Re-index required`, no SQL error |
| Partial state (one stale 16-dim chunk among 32-dim ones) | OK: matching rows searched, warning `1 chunks ... are ignored until re-indexed` |
| Full re-index at another dimension | OK: old rows replaced, no mixed state |
| 2048 dimensions (> 2000 HNSW limit) | OK: indexed, no HNSW index created, search works sequentially |
| `MemoryTool` add / search / update / delete, and the incompatible case | OK (after the `source_id` fix below) |
| Python service wait at startup in `copilot.engine=java` | Removed: startup sync no longer waits 5 x 30 s |

Bugs found only by running it live (unit tests could not see them):

1. `ProviderCredentials` compared the `char(1)` `isactive` column as a String; Hibernate returns a `Character`, so
   every linked API key was treated as inactive.
2. Memory ids are 36-char UUIDs but `source_id` was `varchar(32)`: `MemoryTool` add failed with an opaque message.
   The column is now `varchar(64)` and existing tables are widened by the module script.
3. Embedding resolution masked the real error: when `etendo-embeddings` failed, the legacy fallback model was tried
   and its "No active model configuration" error was reported instead.
4. `KnowledgeBaseSearch` was missing in MCP direct mode for KB agents.
5. Tool failures were opaque (`The server tool execution failed.`) and left no cause in the log; the log now carries
   the cause class and message and `OBException`/`IllegalArgumentException` messages reach the caller.
6. `CopilotSyncStartup.waitForCopilotService` and `CopilotUtils.getSourcesPath` called the Python container even in
   `java` mode.
7. The module script change that creates the extension was missing an import (`java.util.Properties`), and
   `local-env up` does not recompile module scripts: run `./gradlew compile.modulescript -Dmodule=<module>` after
   editing `src-util/modulescript`.

### Finding that changes the plan: pgvector breaks `update.database` without `db.extended`

With the extension installed and `com.etendoerp.db.extended` absent, `update.database` fails with a
`NullPointerException` in DBSM (`PostgrePLSQLFunctionConcurrentStandardization`, `LiteralFilter.removeLiterals`).
`db.extended` avoids it with its `CreateExcludeFilter` build validation (it excludes the extension's functions from
the model). Consequences:

- Providing pgvector in local-env is not enough for a plain Copilot environment: the environment also needs
  `db.extended` and its `exclude.filter` module, or the extension must stay absent. GO instances are fine
  (`com.etendoerp.go` depends on `db.extended`).
- The Copilot module script now enables the extension only when `db.extended` is installed (WARN otherwise).
  Verified live: on a database where pgvector is available and `db.extended` is absent it logs the WARN and
  `update.database` succeeds.
- Adding `db.extended` to an existing environment is not a one-liner: its generated entities need its own AD
  tables, so `compile.complete` fails until the database has them. It must be part of the environment from
  `install`, i.e. a new environment created with it, not added afterwards.
- Open question for the product: should the Copilot KB table also be listed in an exclude filter so DBSM never
  treats `etcop_kb_chunk` as foreign? During the live run DBSM did not touch it, but this was not checked across
  several `update.database` runs with `db.extended` installed.

## 8. Next actions

1. Decide A (overlay) vs A then B (also move to theseus for `psql`).
2. KB dimension issue of §2: fixed in code (per-chunk model identity and dimension, partial expression HNSW index,
   compatibility check before search) and verified live, see §7.
3. Implement the build script and `pgvector_ensure()` in `etendo-localenv` on a branch.
4. Run §5 and record the results in `docs/design.md` §6.10-style notes.
