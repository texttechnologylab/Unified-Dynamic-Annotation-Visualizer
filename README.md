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

- Dynamic and interactive charts
- Visual editor
- Different export options: svg, png, tex, csv, json
- Widget pagination
- LLM ChatBot

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

#### Export capability matrix

| Channel | Endpoint / Action | Typical Scope | Formats | Output Shape |
| --- | --- | --- | --- | --- |
| Web UI | Toolbar export | Single widget | svg, png, tex, csv, json | Direct file |
| Web UI | Toolbar **bulk** export | Widget pages/parts | svg, png, tex, csv, json | ZIP |
| API | `POST /api/batch/export/{format}` | Single widget selector | svg, png, tex, csv, json | Direct file or ZIP |
| API | `POST /api/batch/export/pipeline/{format}` / `GET /api/batch/export/pipeline/{pipelineId}/{format}` | All generator-backed widgets in pipeline | svg, png, tex, csv, json | ZIP (+ summary/errors) |
| API | `POST /api/data/export?format=...` | Data-oriented group export | json, csv, tex | ZIP |

## Getting Started

> [!TIP]
> Please consult the [documentation](https://texttechnologylab.github.io/Unified-Dynamic-Annotation-Visualizer/) page for a more detailed and customizable setup documentation.

### Requirements

- [Docker](https://docs.docker.com/get-docker/) and [Docker Compose](https://docs.docker.com/compose/install/) (v2.x or later)

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

   This starts PostgreSQL and the UDAV application. The web UI is available at [http://localhost:8080](http://localhost:8080) once the container is healthy (usually within ~30–60 seconds).

> [!NOTE]
> If you're looking for a small demo without any setup, check our [open demo](https://demo.udav.texttechnologylab.org/).

---

### Importing DUUI Annotation Data

To import XMI/GZ annotation files produced by DUUI pipelines, you need to configure the importer in your `.env` before starting the containers.

> [!IMPORTANT]
> `DUUI_IMPORTER_PATH` and `DUUI_IMPORTER_TYPE_SYSTEM_PATH` must be **absolute paths on your host machine** — Docker Compose mounts them into the container automatically.

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

**3. (Optional) Set the path to an external TypeSystem XML** if you want to use a custom type system instead of letting UDAV auto-detect it from the XMI files:

```env
DUUI_IMPORTER_TYPE_SYSTEM_PATH=/absolute/path/to/your/typesystem
```

> [!NOTE]
> If `DUUI_IMPORTER_TYPE_SYSTEM_PATH` is left empty, the type system is auto-detected from the XMI files. If you set it, point it to the **folder** containing your TypeSystem XML file.

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
DUUI_IMPORTER_TYPE_SYSTEM_PATH=

# Java memory (adjust to your system)
JAVA_OPTS=-Xmx10G -Xms1024m
```

## License

This project is published under the AGPL-3.0 [license](/LICENSE).

# Cite
If you want to use the project please quote this as follows:

Thiemo Dahmann, Julian Schneider, Philipp Stephan, Giuseppe Abrami and Alexander Mehler. 2026. "Towards the Generation and Application of Dynamic Web-Based Visualization of UIMA-based Annotations for Big-Data Corpora with the Help of Unified Dynamic Annotation Visualizer". Proceedings of the 15th International Conference on Language Resources and Evaluation (LREC 2026). _accepted_.

## BibTeX
```bib
@inproceedings{Dahmann:et:al:2026,
  title     = {Towards the Generation and Application of Dynamic Web-Based Visualization
               of UIMA-based Annotations for Big-Data Corpora with the Help of
               Unified Dynamic Annotation Visualizer},
  booktitle = {Proceedings of the 15th International Conference on Language Resources
               and Evaluation (LREC 2026)},
  year      = {2026},
  author    = {Dahmann, Thiemo and Schneider, Julian and Stephan, Philipp and Abrami, Giuseppe
               and Mehler, Alexander},
  keywords  = {NLP, UIMA, Annotations, dynamic visualization, uce},
  abstract  = {The automatic and manual annotation of unstructured corpora is
               a daily task in various scientific fields, which is supported
               by a variety of existing software solutions. Despite this variety,
               there are currently only limited solutions for visualizing annotations,
               especially with regard to dynamic generation and interaction.
               To bridge this gap and to visualize and provide annotated corpora
               based on user-, project- or corpus-specific aspects, Unified Dynamic
               Annotation Visualizer (UDAV) was developed. UDAV is designed as
               a web-based solution that implements a number of essential features
               which comparable tools do not support to enable a customizable
               and extensible toolbox for interacting with annotations, allowing
               the integration into existing big data frameworks.},
  note      = {accepted}
}
```
