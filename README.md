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
- Different export options

## Getting Started

> [!TIP]
> Please consult the [documentation](https://texttechnologylab.github.io/Unified-Dynamic-Annotation-Visualizer/) page for a more detailled and customizable setup documentation.

### Requirements

- Java version 21 or higher
  
### Usage

1. Clone the repository:

   ```
   git clone https://github.com/texttechnologylab/Unified-Dynamic-Annotation-Visualizer.git
   ```

2. In the root folder, create an `.env` file that holds the following environment variables:

   ```env
   DB_URL=jdbc:postgresql://postgres:5432/udav
   DB_USER=postgres
   DB_PASS=postgres
   DB_SCHEMA=public
   DB_DIALECT=POSTGRES
   # Batch size for database inserts (default: 5000)
   # Higher = fewer DB roundtrips, more memory. Range: 1000-15000
   DB_BATCH_SIZE=5000
   # Max identifier length (PostgreSQL: 63, MySQL: 64, MSSQL: 128)
   DB_MAX_IDENT=255
   # Enable/disable DUUI importer
   DUUI_IMPORTER=false
   # Path to input files
   DUUI_IMPORTER_PATH=/app/data/input
   # File extension: .xmi (uncompressed) or .gz (gzip compressed)
   DUUI_IMPORTER_FILE_ENDING=.xmi
   # Number of parallel workers (default: 4, rule: 1 per CPU core)
   DUUI_IMPORTER_WORKERS=4
   # UIMA CAS pool size (default: 2×workers)
   DUUI_IMPORTER_CAS_POOL_SIZE=8
   # Optional: External TypeSystem XML file path (auto-detected from XMI if not set)
   DUUI_IMPORTER_TYPE_SYSTEM_PATH=
   PIPELINE_IMPORTER=true
   PIPELINE_IMPORTER_FOLDER=/app/data/pipelines
   PIPELINE_IMPORTER_REPLACE_IF_DIFFERENT=false
   SROUCE_BUILDER=false
   JAVA_OPTS=-Xmx2048m -Xms1024m
   ```

3. Run the File Importer to import the annotation data

4. Start the `App.java` file

> [!NOTE]
> The webpage, by deafult, is reachable under: [http://localhost:8080](http://localhost:8080/). If you're looking for a small demo without creating it yourself, please check our [open demo](udav/demo).

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
