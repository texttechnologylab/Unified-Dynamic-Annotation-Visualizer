---
layout: default
title: DUUI Importer — Configuration Reference
---

# DUUI Importer — Configuration Reference

The DUUI Importer reads UIMA CAS files (XMI or gzip-compressed XMI) and writes their annotation data into the UDAV PostgreSQL database using bulk COPY operations.

All settings are passed via environment variables (`.env` file or Docker environment).

---

## Table of Contents

- [Core Importer Settings](#core-importer-settings)
- [Input & TypeSystem](#input--typesystem)
- [Parallelism & Performance](#parallelism--performance)
- [Database Settings](#database-settings)
- [Advanced / Debug](#advanced--debug)
- [Pipeline Hash](#pipeline-hash)
- [Configuration Reference Table](#configuration-reference-table)
- [Example Configurations](#example-configurations)

---

## Core Importer Settings

### `DUUI_IMPORTER`

| | |
|---|---|
| **Type** | boolean |
| **Default** | `false` |
| **Required** | Yes (to activate the importer) |

Enables or disables the DUUI importer on application startup. When `false`, the importer is completely inactive and adds no overhead.

```env
DUUI_IMPORTER=true
```

---

## Input & TypeSystem

### `DUUI_IMPORTER_PATH`

| | |
|---|---|
| **Type** | string (path) |
| **Default** | `src/main/resources/input` |
| **Required** | Yes |

Path to the directory containing the XMI or GZ input files.

**Docker Compose:** This must be an **absolute path on the host machine**. Docker Compose mounts it into the container at `/app/data/input` (read-only).

```env
DUUI_IMPORTER_PATH=/data/my-corpus/xmi
```

---

### `DUUI_IMPORTER_FILE_ENDING`

| | |
|---|---|
| **Type** | string |
| **Default** | `.xmi` |
| **Allowed values** | `.xmi`, `.gz` |

File extension used to discover input files in `DUUI_IMPORTER_PATH`. Only files matching this suffix are processed.

- `.xmi` — uncompressed UIMA XMI serialization
- `.gz` — gzip-compressed XMI (produced by DKPro/DUUI pipelines with `XmiWriter PARAM_COMPRESSION=GZIP`)

```env
DUUI_IMPORTER_FILE_ENDING=.gz
```

---

### `DUUI_IMPORTER_TYPE_SYSTEM_PATH`

| | |
|---|---|
| **Type** | string (path) |
| **Default** | `src/main/resources/types/PlenumTypeSystem.xml` (bare JAR) / auto-detected (Docker) |
| **Required** | No |

Path to an external TypeSystem XML file (or the directory containing it). When set, UDAV loads this type system instead of auto-detecting it from the XMI files.

**When to set:**
- Your corpus uses a custom type system that is not embedded in the XMI files.
- You want deterministic type resolution independent of file order.

**Docker Compose:** Set this to an **absolute host path** pointing to the folder or file. The path is mounted into the container at `/app/data/types` (read-only).

```env
# Leave blank for auto-detection (recommended for most setups)
DUUI_IMPORTER_TYPE_SYSTEM_PATH=

# Or point to your type system file
DUUI_IMPORTER_TYPE_SYSTEM_PATH=/data/my-corpus/typesystem
```

> **Note:** If the path is set but the file does not exist, startup will fail with an explicit error.

---

## Parallelism & Performance

### `DUUI_IMPORTER_WORKERS`

| | |
|---|---|
| **Type** | integer |
| **Default** | `4` |
| **Min** | `1` |

Number of parallel UIMA worker threads that process CAS objects. Each worker holds a CAS from the pool.

**Rule of thumb:** set equal to the number of available CPU cores. Higher values can improve throughput on I/O-bound corpora but increase memory usage.

```env
DUUI_IMPORTER_WORKERS=8
```

---

### `DUUI_IMPORTER_CAS_POOL_SIZE`

| | |
|---|---|
| **Type** | integer |
| **Default** | `workers × 2` (minimum 1) |

Size of the UIMA CAS object pool. A larger pool allows more documents to be in-flight simultaneously but increases heap memory usage.

**Rule of thumb:** `workers × 2` is a safe starting point. Increase if workers are often idle waiting for a CAS, decrease if memory is tight.

```env
DUUI_IMPORTER_CAS_POOL_SIZE=16
```

---

### `DUUI_IMPORTER_READER_BATCH_SIZE`

| | |
|---|---|
| **Type** | integer |
| **Default** | `10` |

Number of documents read per batch by `DUUIFileReaderLazy`. Larger batches reduce file-system overhead but increase per-batch memory usage.

```env
DUUI_IMPORTER_READER_BATCH_SIZE=20
```

---

### `DUUI_IMPORTER_DB_WORKERS`

| | |
|---|---|
| **Type** | integer |
| **Default** | `1` |

Number of parallel `JooqDatabaseWriter` instances (COPY writer stage). Each writer opens its own database connection and writes independently.

Increasing this can improve throughput when the database is the bottleneck, but adds connection overhead and may cause contention on the same tables.

```env
DUUI_IMPORTER_DB_WORKERS=2
```

---

### `DUUI_IMPORTER_PREPARE_DB_SCHEMA`

| | |
|---|---|
| **Type** | boolean |
| **Default** | `true` |

When `true`, a dedicated single-threaded schema-preparation stage runs **before** the parallel COPY writers. This stage creates all necessary tables and columns based on the type system but does not insert any data.

**Why this matters:** DDL operations (CREATE TABLE, ALTER TABLE) are not safe to run from multiple parallel workers simultaneously. By separating schema setup from data writing, the parallel COPY stage can run with `allowDdl=false`, avoiding lock contention.

Set to `false` only if you are certain the schema already exists and is complete.

```env
DUUI_IMPORTER_PREPARE_DB_SCHEMA=true
```

---

### `DUUI_IMPORTER_STORE_COVERED_TEXT`

| | |
|---|---|
| **Type** | boolean |
| **Default** | `false` |

When `true`, the text span covered by each annotation (`begin`–`end`) is extracted from the sofa and stored in a `covered_text` column in every annotation table.

**Trade-offs:**
- Enables text-based queries without joining the `sofas` table.
- Significantly increases database storage size for large corpora.
- Slightly increases import time and memory usage.

> **Note:** Changing this setting on an existing import changes the pipeline hash and causes all documents to be re-imported.

```env
DUUI_IMPORTER_STORE_COVERED_TEXT=false
```

---

## Database Settings

These settings configure the database connection used by the DUUI importer's `JooqDatabaseWriter`. They are shared with the main UDAV application.

### `DB_URL`

| | |
|---|---|
| **Type** | string (JDBC URL) |
| **Default** | `jdbc:postgresql://localhost:5432/postgres` |

JDBC connection URL. In Docker Compose, the host is the service name (`postgres`).

```env
DB_URL=jdbc:postgresql://postgres:5432/udav
```

---

### `DB_USER` / `DB_PASS`

| | |
|---|---|
| **Type** | string |
| **Default** | `postgres` / `postgres` |

Database username and password.

```env
DB_USER=postgres
DB_PASS=postgres
```

---

### `DB_SCHEMA`

| | |
|---|---|
| **Type** | string |
| **Default** | `public` |

PostgreSQL schema in which UDAV creates all its tables (`documents`, `sofas`, `uima_type_registry`, and all annotation tables). The schema is created automatically if it does not exist.

```env
DB_SCHEMA=public
```

---

### `DB_BATCH_SIZE`

| | |
|---|---|
| **Type** | integer |
| **Default** | `3000` (application default) / `10000` (writer default) |
| **Range** | `1000` – `15000` recommended |

Number of rows accumulated in a COPY buffer before flushing to PostgreSQL. The buffer also flushes automatically when it exceeds 16 MB regardless of row count.

- Higher values → fewer network round-trips, higher per-flush memory usage.
- Lower values → more frequent flushes, lower peak memory.

```env
DB_BATCH_SIZE=10000
```

---

### `DB_MAX_IDENT`

| | |
|---|---|
| **Type** | integer |
| **Default** | `255` (application) / `63` (writer hard-cap for PostgreSQL) |

Maximum length for generated SQL identifiers (table and column names). For PostgreSQL, the hard limit is 63 bytes regardless of this setting. The writer enforces `max(16, min(configured, dialect_hard_limit))`.

| Database | Hard limit |
|---|---|
| PostgreSQL | 63 |
| MySQL / MariaDB | 64 |

```env
DB_MAX_IDENT=63
```

---

### `DB_DIALECT`

| | |
|---|---|
| **Type** | string |
| **Default** | `POSTGRES` |

SQL dialect for jOOQ. Currently only `POSTGRES` is fully supported by the DUUI importer (the writer validates this on startup and will fail for other dialects).

```env
DB_DIALECT=POSTGRES
```

---

## Advanced / Debug

### `DUUI_IMPORTER_SKIP_VERIFICATION`

| | |
|---|---|
| **Type** | boolean |
| **Default** | `false` |

Passes `withSkipVerification(true)` to the `DUUIComposer`. Skips component readiness checks during initialization, which can speed up startup for known-good setups.

```env
DUUI_IMPORTER_SKIP_VERIFICATION=false
```

---

### `DUUI_IMPORTER_DEBUG_XMI`

| | |
|---|---|
| **Type** | boolean |
| **Default** | `false` |

When `true`, adds an `XmiWriter` stage to the pipeline that writes each processed CAS back to disk as a gzip-compressed XMI file. Useful for inspecting what the importer sees after `RemoveMetaInformation` has run.

> This adds significant I/O overhead. Only enable for debugging.

```env
DUUI_IMPORTER_DEBUG_XMI=false
```

---

### `DUUI_IMPORTER_DEBUG_XMI_PATH`

| | |
|---|---|
| **Type** | string (path) |
| **Default** | `/tmp/export` |
| **Only used when** | `DUUI_IMPORTER_DEBUG_XMI=true` |

Output directory for debug XMI files written by the `XmiWriter` stage. The directory must be writable by the application process.

```env
DUUI_IMPORTER_DEBUG_XMI_PATH=/tmp/udav-debug-xmi
```

---

## Pipeline Hash

### `DUUI_IMPORTER_PIPELINE_HASH_EXTRA`

| | |
|---|---|
| **Type** | string |
| **Default** | _(empty)_ |

An optional string appended as `extra=<value>` when computing the pipeline hash. The pipeline hash is a SHA-256 digest derived from:

- Writer schema version
- `DUUI_IMPORTER_FILE_ENDING`
- `DUUI_IMPORTER_TYPE_SYSTEM_PATH`
- `DUUI_IMPORTER_DEBUG_XMI`
- `DUUI_IMPORTER_STORE_COVERED_TEXT`
- This extra value (if set)

The pipeline hash is stored alongside each document in the `documents` table. When the hash changes, documents are considered outdated and are re-imported on the next run. Use this setting to force a full re-import without changing any other option.

```env
DUUI_IMPORTER_PIPELINE_HASH_EXTRA=reimport-2026-05-11
```

---

## Configuration Reference Table

| Environment Variable | Type | Default | Description |
|---|---|---|---|
| `DUUI_IMPORTER` | boolean | `false` | Enable the importer |
| `DUUI_IMPORTER_PATH` | path | `src/main/resources/input` | Host path to input XMI/GZ files |
| `DUUI_IMPORTER_FILE_ENDING` | string | `.xmi` | File extension: `.xmi` or `.gz` |
| `DUUI_IMPORTER_TYPE_SYSTEM_PATH` | path | _(auto-detect)_ | Path to external TypeSystem XML |
| `DUUI_IMPORTER_WORKERS` | integer | `4` | Parallel UIMA processing workers |
| `DUUI_IMPORTER_CAS_POOL_SIZE` | integer | `workers × 2` | UIMA CAS object pool size |
| `DUUI_IMPORTER_READER_BATCH_SIZE` | integer | `10` | Documents per reader batch |
| `DUUI_IMPORTER_DB_WORKERS` | integer | `1` | Parallel DB COPY writer instances |
| `DUUI_IMPORTER_PREPARE_DB_SCHEMA` | boolean | `true` | Run schema-prep stage before COPY writers |
| `DUUI_IMPORTER_STORE_COVERED_TEXT` | boolean | `false` | Store annotation covered text in DB |
| `DUUI_IMPORTER_SKIP_VERIFICATION` | boolean | `false` | Skip DUUI composer verification |
| `DUUI_IMPORTER_DEBUG_XMI` | boolean | `false` | Write processed CAS to disk as XMI |
| `DUUI_IMPORTER_DEBUG_XMI_PATH` | path | `/tmp/export` | Output dir for debug XMI files |
| `DUUI_IMPORTER_PIPELINE_HASH_EXTRA` | string | _(empty)_ | Extra value appended to pipeline hash |
| `DB_URL` | string | `jdbc:postgresql://localhost:5432/postgres` | JDBC connection URL |
| `DB_USER` | string | `postgres` | Database username |
| `DB_PASS` | string | `postgres` | Database password |
| `DB_SCHEMA` | string | `public` | PostgreSQL schema |
| `DB_BATCH_SIZE` | integer | `3000` | COPY buffer row limit |
| `DB_MAX_IDENT` | integer | `255` (capped at 63 for PostgreSQL) | Max SQL identifier length |
| `DB_DIALECT` | string | `POSTGRES` | SQL dialect (only `POSTGRES` supported) |

---

## Example Configurations

### Minimal — browse existing data, no import

```env
DUUI_IMPORTER=false
DB_URL=jdbc:postgresql://postgres:5432/udav
DB_USER=postgres
DB_PASS=postgres
```

---

### Standard import — uncompressed XMI, auto-detected type system

```env
DUUI_IMPORTER=true
DUUI_IMPORTER_PATH=/data/my-corpus/xmi
DUUI_IMPORTER_FILE_ENDING=.xmi
DUUI_IMPORTER_WORKERS=4
DUUI_IMPORTER_CAS_POOL_SIZE=8
DUUI_IMPORTER_DB_WORKERS=1
DUUI_IMPORTER_PREPARE_DB_SCHEMA=true

DB_URL=jdbc:postgresql://postgres:5432/udav
DB_USER=postgres
DB_PASS=postgres
DB_BATCH_SIZE=10000
JAVA_OPTS=-Xmx10G -Xms1024m
```

---

### High-throughput import — gzip corpus, explicit type system, text storage enabled

```env
DUUI_IMPORTER=true
DUUI_IMPORTER_PATH=/data/my-corpus/gz
DUUI_IMPORTER_FILE_ENDING=.gz
DUUI_IMPORTER_TYPE_SYSTEM_PATH=/data/my-corpus/typesystem
DUUI_IMPORTER_WORKERS=8
DUUI_IMPORTER_CAS_POOL_SIZE=16
DUUI_IMPORTER_READER_BATCH_SIZE=20
DUUI_IMPORTER_DB_WORKERS=2
DUUI_IMPORTER_PREPARE_DB_SCHEMA=true
DUUI_IMPORTER_STORE_COVERED_TEXT=true

DB_URL=jdbc:postgresql://postgres:5432/udav
DB_USER=postgres
DB_PASS=postgres
DB_BATCH_SIZE=10000
JAVA_OPTS=-Xmx20G -Xms2G
```

---

### Force full re-import of an existing corpus

Change only `DUUI_IMPORTER_PIPELINE_HASH_EXTRA` — this invalidates the stored pipeline hash for every document, causing all documents to be re-processed on the next run.

```env
DUUI_IMPORTER_PIPELINE_HASH_EXTRA=reimport-2026-05-11
```
