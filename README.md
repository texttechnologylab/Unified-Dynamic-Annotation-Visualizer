<div align="center">
  <a href="/LICENSE"> <img src="https://img.shields.io/github/license/Texttechnologylab/Unified-Dynamic-Annotation-Visualizer"></a>
  <a href="https://github.com/texttechnologylab/Unified-Dynamic-Annotation-Visualizer/releases"> <img src="https://img.shields.io/github/v/release/Texttechnologylab/Unified-Dynamic-Annotation-Visualizer"></a>
  <a href="https://lrec2026.info/"> <img src="https://img.shields.io/badge/conference-LREC--2026-4b44ce.svg"></a>
  <a href="https://lrec2026.info/"> <img src="https://img.shields.io/badge/paper-LREC--2026-fb44ce.svg"></a>
</div>

<div align="center">
  <h1>Unified Dynamic Annotation Visualizer (UDAV)</h1>
  <img height="200px" src="/src/main/resources/static/img/logo.png"/>
  <h3>A tool for generating dynamic and interactive annotation visualizations.</h3>
  <hr/>
</div>

UDAV is designed to enable different disciplines to display their automatic pre-processing results in a schema-based and reproducible, dynamic and interactive way without the need to hard-code manual and user-defined visualizations for each new project.

## Features

- Dynamic and interactive charts on a drag-and-drop grid, described by a JSON pipeline definition
- Visual pipeline editor
- Sources: UIMA annotation types imported with DUUI, or your own JSON/XML files
- Generator groups: one widget page per top-level key of a JSON/XML file
- Export options: svg, png, tex (through the VecTikZ SVG-to-TikZ converter), csv, json
- Headless batch export API for single widgets and whole pipelines
- ChartBot, an LLM assistant for the charts of a view

### Widgets

UDAV currently contains the following widgets:

- Text (static)
- Image (static)
- Video (static)
- Inline Frame (static)
- Table
- Bar Chart
- Pie Chart
- Line Chart
- Highlight Text
- Simple Map
- Network Graph
- Voronoi Diagram
- Medial Axis
- Boundary Approximation

### Headless Batch Export

UDAV also exposes a headless batch export API that reuses the same browser-side export logic as the UI.

- `POST /api/batch/export/{format}` for a single widget
- `POST /api/batch/export/pipeline/{format}` for all generator-backed widgets in a pipeline

Supported formats: `svg`, `png`, `tex`, `csv`, `json`.

The batch exporter uses a headless Chromium/Edge executable on the machine running the Java process. Configure the browser path explicitly with `BROWSER_EXECUTABLE_PATH` if automatic detection does not find it.

Browsers are pooled and reused across requests rather than launched per request, and the widgets of a
pipeline are exported concurrently inside a single page. Both are tunable:

| Variable | Default | Meaning |
| --- | --- | --- |
| `UDAV_BASE_URL` | `http://localhost:8080` | Origin the headless browser uses to reach this application; change it together with `server.port`. |
| `EXPORT_CONCURRENCY` | `4` | Widgets exported in parallel within one page. `1` restores sequential export. |
| `EXPORT_POOL_MAX_SESSIONS` | `2` | Browsers kept alive for reuse (~150-300 MB RSS each). Created lazily. |
| `EXPORT_POOL_MAX_WAITERS` | `8` | Requests allowed to queue before the API sheds load with `503`. |
| `EXPORT_POOL_BORROW_TIMEOUT_MS` | `120000` | How long a request waits for a free browser session before failing. |
| `EXPORT_POOL_IDLE_TIMEOUT_MS` | `300000` | Idle time after which a pooled browser is closed. `0` disables eviction. |
| `EXPORT_WIDGET_TIMEOUT_MS` | `30000` | Safety net for a widget export that never settles. |
| `EXPORT_READY_TIMEOUT_MS` | `30000` | How long to wait for the view to signal readiness. |
| `EXPORT_VIEWPORT_WIDTH` / `EXPORT_VIEWPORT_HEIGHT` | `1600` / `1000` | Viewport used for rendering; affects exported artefact dimensions. |
| `EXPORT_NO_SANDBOX` | `false` | Passes `--no-sandbox`. Often required in containers; see the note below. |
| `EXPORT_DISABLE_GPU` | `true` | Passes `--disable-gpu`; headless Chromium rasterises on the CPU anyway. |
| `EXPORT_METRICS` | `false` | Per-request measurements: a block in the log and the `X-UDAV-Export-Metrics` response header. The evaluation harness needs them. |
| `EXPORT_CPU_SAMPLE_MS` | `250` | Sampling interval of the browser-process CPU tracker used by the metrics. |
| `PIPELINE_CACHE_TTL_MS` | `5000` | Short-TTL cache for pipeline JSON. `0` disables. |
| `TOMCAT_MAX_THREADS` | `200` | Servlet worker pool that the budget in the note below is expressed against. |

> [!NOTE]
> The headless page calls back into this same application for `/api/data` and `/api/convertions/*`.
> Batch-attributable worker usage is bounded by roughly
> `(max-sessions + max-waiters) + 6 * max-sessions` (Chromium caps connections per origin at ~6),
> so keep that under `server.tomcat.threads.max` (pinned to `200` by default).

> [!NOTE]
> The Docker image ships Alpine's Chromium package (plus Node.js for the Playwright driver), so the
> batch export API works out of the box in `docker compose up`. Chromium's sandbox needs unprivileged
> user namespaces, which Docker's default seccomp profile blocks, so the image sets
> `EXPORT_NO_SANDBOX=true`. That is a real security trade-off: the browser only ever loads this
> application's own pages, but if you want the sandbox back, set `EXPORT_NO_SANDBOX=false` and run
> the container with a seccomp profile that allows user namespaces.

#### Export capability matrix

| Channel | Endpoint / Action | Typical Scope | Formats | Output Shape |
| --- | --- | --- | --- | --- |
| Web UI | Toolbar export | Single widget | svg, png, tex, csv, json | Direct file |
| Web UI | Toolbar **bulk** export | Widget pages/parts | svg, png, tex, csv, json | ZIP |
| API | `POST /api/batch/export/{format}` | Single widget selector | svg, png, tex, csv, json | Direct file or ZIP |
| API | `POST /api/batch/export/pipeline/{format}` / `GET /api/batch/export/pipeline/{pipelineId}/{format}` | All generator-backed widgets in pipeline | svg, png, tex, csv, json | ZIP (+ summary/errors) |
| API | `POST /api/data/export?format=...` | Data-oriented group export | json, csv, tex | ZIP |

#### TeX export: VecTikZ

The `tex` format of the toolbar and of the batch API is produced by `POST /api/convertions/tikz`.
Widgets with a native LaTeX representation (Table, Highlight Text) generate it directly; every
SVG-drawing widget is converted by **VecTikZ** (`org.texttechnologylab.udav.widgets.svgtolatex`),
an SVG-to-TikZ converter that turns shapes, paths, text, gradients, patterns, markers, clip paths
and CSS-styled elements into a standalone TikZ document. Text metrics come from a checked-in table,
so the output is identical on every machine.

### JSON and XML sources for generators

Besides UIMA annotation types, every generator can read a JSON file that was imported into the
`json_data` table: drop it into `sourcefilesJSON/` (`JSON_IMPORTER_FOLDER`) and it is imported at
startup; `.xml` files are converted to JSON on import (`org.json`). The `uri` of the source is the
file name. Files that already exist in the table are skipped unless
`JSON_IMPORTER_REPLACE_IF_DIFFERENT=true`; `JSON_IMPORTER=false` disables the importer. Field names
can be renamed: `keys` maps generator field names to source field names and works on list-shaped
documents, `keysMap` follows the nesting of the document and maps source keys to target keys, and
`fixedKeys` injects constants into every row.

`"generatorGroup": true` turns the generator into a template that is instantiated once per
top-level key of the JSON/XML document. Put `@ID@` into the generator id (`CategoryNumber-@ID@`);
it is replaced by the key. Widgets reference the template id and get one page per sub-generator:
the view shows a pager, the data API reports the group as `meta.total` / `meta.ids`, and the
"Export all" toolbar entry and the batch API's `bulk=true` export every page. Only JSON/XML
sources can be grouped.

`CategoryNumber` and `TextFormatting` evaluate the filter lists `filesWhitelist` / `filesBlacklist`
and `categoriesWhitelist` / `categoriesBlacklist`, set on the generator or on its source.

| Generator | JSON document | Settings |
| --- | --- | --- |
| `MapCoordinates` | list of points `[{"x": 1, "y": 2}]`, points with nested `edges`, or edge pairs (`"inputFormat": "edgePairs"`) | `keys`, `keysMap`, `fixedKeys`, `scale` |
| `CategoryNumber` | `{"NOUN": 12, "VERB": 7}` (one file), `{"doc-1": {"NOUN": 12}, ...}` (per file), or rows `[{"category": "NOUN", "number": 12, "file": "doc-1", "color": "#hex"}]` | `keys`, `fixedKeys`, `colors` (`{"NOUN": "#4e79a7"}`), `color`, `file`, `categoriesWhitelist/Blacklist`, `filesWhitelist/Blacklist` |
| `TextFormatting` | `{"text": "...", "segments": [{"begin": 0, "end": 4, "category": "NOUN", "type": "POS"}]}`; each distinct `type` becomes one annotation layer | `keys`, `textKey`, `segmentsKey`, `type` (default layer), `style`, `styles` (`{"POS": "highlight"}`), `colors` (`{"NOUN": "#hex"}` or `{"POS": {"NOUN": "#hex"}}`), `categoriesWhitelist/Blacklist` |

The evaluation pipelines (`pipelines/pipeline_eval-*.json`) are a complete example: their
HighlightText, BarChart and PieChart widgets sit on JSON-backed `TextFormatting` and
`CategoryNumber` generators over `sourcefilesJSON/eval-annotations-*.json`.

### Running the batch export evaluation

The evaluation of the batch export API (latency, resource use, correctness; see the paper) is a JUnit
harness, `BatchExportEvaluationIT`, that drives a running UDAV instance over HTTP. The thirteen
evaluation pipelines and their JSON sources are part of the repository and of the Docker image and
import at startup, so the evaluation runs on any machine with one command:

```bash
tools/run-evaluation.sh --docker --smoke   # start the Docker stack, verify every export once (minutes)
tools/run-evaluation.sh --docker           # same, then the full measurement campaign (hours)
tools/run-evaluation.sh --url http://localhost:8080   # against an instance started with EXPORT_METRICS=true
```

On Windows run the script from Git Bash or WSL, or do its two steps by hand: start the stack with
`docker compose -f docker-compose.yml -f tools/docker-compose.evaluation.yml up -d --build` (it
publishes the app on port 18080 with measurements switched on) and run
`mvn test -Dtest=BatchExportEvaluationIT -DUDAV_BASE_URL=http://localhost:18080`, adding
`-Dudav.eval.bugHunt=true` for the smoke variant.

The report of the campaign the paper is based on is `evaluation/evaluation-report-2026-08-26.txt`.
The script writes `evaluation-report.txt`, whose first section states whether the numbers are usable;
section 5 holds the LaTeX rows of the paper's tables and section 7 the raw per-run data. Harness
knobs (`-Dudav.eval.analysisRuns=15`, `-Dudav.eval.pipelines=P1,P5`, ...) are passed through.
`tools/make_eval_pipelines.py` regenerates the pipelines and their JSON sources.

`mvn test` runs the unit tests. The tests tagged `browser` launch Playwright's own Chromium (downloaded
on the first run, about 1 GB together with Firefox and WebKit) and are opt-in: `mvn test -Pbrowser-tests`.

### ChartBot (LLM assistant)

Every pipeline view can show **ChartBot**, a chat panel that explains and interprets the charts of
the view. A chart can be attached to a question as an image (the widget's SVG is rasterised in the
browser), the model is chosen per message from the server's model list, and answers are rendered as
Markdown. The panel appears as soon as `LLM_BASE_URL` and `LLM_API_TOKEN` are set; requests are
proxied server-side to an [Open WebUI](https://github.com/open-webui/open-webui)-style API
(`GET {LLM_BASE_URL}/api/models`, `POST {LLM_BASE_URL}/api/chat/completions`, bearer token), so
the token never reaches the browser.

```env
LLM_BASE_URL=https://llm.example.org
LLM_API_TOKEN=...
```

## Getting Started

> [!TIP]
> Please consult the [documentation](https://texttechnologylab.github.io/Unified-Dynamic-Annotation-Visualizer/) page for a more detailed and customizable setup documentation.

There are two ways to run UDAV: with Docker Compose (nothing to install besides Docker) or from
source against a PostgreSQL of your own. Both import the bundled demo and evaluation pipelines at
startup, so the pipeline list is populated right away.

### Requirements

- Docker route: [Docker](https://docs.docker.com/get-docker/) and [Docker Compose](https://docs.docker.com/compose/install/) (v2.x or later)
- Source route: JDK 21, Maven 3.9, PostgreSQL (the Docker stack uses 17), and for the batch export API a Chromium, Chrome or Edge on the machine

### Quick Start (Docker Compose)

1. **Clone the repository:**

   ```bash
   git clone https://github.com/texttechnologylab/Unified-Dynamic-Annotation-Visualizer.git
   cd Unified-Dynamic-Annotation-Visualizer
   ```

2. **Create your `.env` file** by copying the provided example:

   ```bash
   cp .env.example .env
   ```

3. **Start the application:**

   ```bash
   docker compose up -d
   ```

   This starts PostgreSQL and the UDAV application. The web UI is available at [http://localhost:8080](http://localhost:8080) once the container is healthy (usually within 30-60 seconds). The image ships the demo and evaluation pipelines with their JSON sources and imports them at startup; set `PIPELINE_IMPORTER=false` or point `PIPELINE_IMPORTER_FOLDER` / `JSON_IMPORTER_FOLDER` at mounted folders to import your own instead. The default memory settings suit a laptop; `.env.example` lists the values for large corpus imports.

> [!NOTE]
> If you're looking for a small demo without any setup, check our [open demo](https://demo.udav.texttechnologylab.org/).

---

### Quick Start (from source, with your own PostgreSQL)

1. **Create an empty database.** UDAV creates every table and schema itself, so the database user
   only needs to be allowed to create schemas in it (the `postgres` superuser is).

   ```bash
   createdb udav
   ```

2. **Tell UDAV where the database is.** Create a file named `.env` in the repository root:

   ```env
   DB_URL=jdbc:postgresql://localhost:5432/udav
   DB_USER=postgres
   DB_PASS=postgres
   ```

   The application reads this file at startup. Do not copy `.env.example` for this: its paths
   describe the Docker layout. If port 8080 is taken, add `server.port=8081` and, for the batch
   export API, `UDAV_BASE_URL=http://localhost:8081`.

3. **Start it from the repository root:**

   ```bash
   mvn spring-boot:run
   ```

   The first build downloads the dependencies from Maven Central and JitPack. The UI is available at
   [http://localhost:8080](http://localhost:8080) once the log says `Started App`. Because the
   application runs from the repository root, the pipelines in `src/main/resources/pipelines` and
   the JSON sources in `src/main/resources/sourcefilesJSON` are imported automatically.

Pipelines whose sources are UIMA annotation types (`DEMO`, `simple-demo`, the `*Test*` pipelines)
show no data until a corpus has been imported; the log warns about each of them at startup.
The geometry demos and the evaluation pipelines read JSON sources and work immediately.

The batch export API looks for a Chromium, Chrome or Edge in the usual places on Linux, macOS and
Windows; set `BROWSER_EXECUTABLE_PATH` in `.env` if yours lives elsewhere. If none is found,
Playwright downloads its own browsers (about 1 GB, into `~/.cache/ms-playwright`) on the first
export, which needs internet access and a few minutes. The evaluation can then be run against this instance with
`tools/run-evaluation.sh --url http://localhost:8080 --smoke` after adding `EXPORT_METRICS=true`
to `.env`.

### Importing DUUI Annotation Data

To import XMI/GZ annotation files produced by DUUI pipelines, you need to configure the importer in your `.env` before starting the containers.

> [!IMPORTANT]
> `DUUI_IMPORTER_PATH` and `DUUI_IMPORTER_TYPE_SYSTEM_PATH` are **paths on your host machine**, absolute or relative to the repository (they default to the empty `data/input` and `data/types` folders). Docker Compose mounts them into the container.

**1. Set the path to your annotation files:**

```env
DUUI_IMPORTER_PATH=/absolute/path/to/your/xmi/files
```

**2. Set the file extension** matching your corpus (`.xmi` for uncompressed, `.gz` for gzip-compressed):

```env
DUUI_IMPORTER_FILE_ENDING=.xmi
# or
DUUI_IMPORTER_FILE_ENDING=.gz
```

**3. Set the path to your TypeSystem XML file:**

```env
DUUI_IMPORTER_TYPE_SYSTEM_PATH=/absolute/path/to/your/TypeSystem.xml
```

> [!NOTE]
> The importer expects `DUUI_IMPORTER_TYPE_SYSTEM_PATH` to name the TypeSystem XML **file** and refuses to start
> unless the path is an existing file. Running from source, the bundled
> `src/main/resources/types/PlenumTypeSystem.xml` is used by default, and an empty value
> (`DUUI_IMPORTER_TYPE_SYSTEM_PATH=` in `.env`) auto-detects the type system from the XMI files.
> With Docker Compose the variable names the host path that is mounted into the container at
> `/app/data/types`; point it at your type system file (the default `./data/types` is an empty folder).

**4. Enable the importer and start:**

```env
DUUI_IMPORTER=true
```

```bash
docker compose up -d
```

The importer runs on startup and processes all matching files in the configured directory. Import progress is logged and visible via:

```bash
docker compose logs -f udav
```

#### Example `.env` for DUUI import

```env
# Database
DB_USER=postgres
DB_PASS=postgres
POSTGRES_DB=udav

# DUUI Importer
DUUI_IMPORTER=true
DUUI_IMPORTER_PATH=/data/my-corpus/xmi-files
DUUI_IMPORTER_FILE_ENDING=.gz
DUUI_IMPORTER_WORKERS=4
DUUI_IMPORTER_CAS_POOL_SIZE=12
DUUI_IMPORTER_TYPE_SYSTEM_PATH=/data/my-corpus/TypeSystem.xml

# Java memory (adjust to your system)
JAVA_OPTS=-Xmx10G -Xms1024m
```

## License

This project is published under the AGPL-3.0 [license](/LICENSE).

# Cite
If you want to use the project please quote this as follows:

Thiemo Dahmann, Julian Schneider, Philipp Stephan, Giuseppe Abrami and Alexander Mehler. 2026. "Towards the Generation and Application of Dynamic Web-Based Visualization of UIMA-based Annotations for Big-Data Corpora with the Help of Unified Dynamic Annotation Visualizer". In *Proceedings of the Fifteenth Language Resources and Evaluation Conference (LREC 2026)*, pages 6695–6705, Palma de Mallorca, Spain. ELRA Language Resource Association. [DOI 10.63317/5ce2aaity4yz](https://doi.org/10.63317/5ce2aaity4yz), [PDF](https://aclanthology.org/2026.lrec-1.533.pdf).

## BibTeX
```bib
@inproceedings{Dahmann:et:al:2026,
  title     = {Towards the Generation and Application of Dynamic Web-Based Visualization
               of {UIMA}-based Annotations for Big-Data Corpora with the Help of
               Unified Dynamic Annotation Visualizer},
  booktitle = {Proceedings of the Fifteenth Language Resources and Evaluation
               Conference (LREC 2026)},
  year      = {2026},
  pages     = {6695--6705},
  author    = {Dahmann, Thiemo and Schneider, Julian and Stephan, Philipp and Abrami, Giuseppe
               and Mehler, Alexander},
  month     = may,
  address   = {Palma de Mallorca, Spain},
  publisher = {ELRA Language Resource Association},
  editor    = {Piperidis, Stelios and Bel, N{\'u}ria and van den Heuvel, Henk and Ide, Nancy
               and Krek, Simon and Toral, Antonio},
  doi       = {10.63317/5ce2aaity4yz},
  url       = {https://aclanthology.org/2026.lrec-1.533/},
  keywords  = {NLP, UIMA, Annotations, dynamic visualization, uce},
  abstract  = {The automatic and manual annotation of unstructured corpora is a routine
               task in many scientific fields and is supported by a variety of existing
               software solutions. Despite this variety, few solutions currently support
               annotation visualization, especially for dynamic generation and interaction.
               To bridge this gap and visualize annotated corpora based on user-, project-,
               or corpus-specific aspects, we developed Unified Dynamic Annotation
               Visualizer (UDAV). UDAV is a web-based solution that implements features
               not supported by comparable tools, enabling a customizable and extensible
               toolbox for interacting with annotations and allowing integration into
               existing big-data frameworks. We exemplify UDAV through a range of
               visualizations and also provide an evaluation of corpus import and
               processing performance.},
  pdf       = {https://aclanthology.org/2026.lrec-1.533.pdf}
}
```
