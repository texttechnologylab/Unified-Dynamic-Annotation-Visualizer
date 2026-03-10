# Unified Dynamic Annotation Visualizer

UDAV is designed to enable different disciplines to display their automatic pre-processing results in a schema-based and reproducible, dynamic and interactive way without the need to hard-code manual and user-defined visualizations for each new project.

## Features

- Dynamic and interactive charts
- Visual editor
- Different export options

### Demo

You can find a demo [here](udav/demo) where you can play around a little.

## Architecture

The architecture of UDAV includes a
backend and frontend component that can interact
with each other via an API layer, both instantiated
within a Docker image. Nevertheless, it can be
used on any system, as all system components are
encapsulated in a Docker container. Importing data
is performed using a standardized procedure as
part of an existing DUUI pipeline. This involves a
set of documents being read by the reader and auto-
matically pre-processed by a set of NLP processes
through a pipeline. At the end of this pipeline is a
Java-based component that serves as an importer
for UDAV and ingests all annotated documents into
the integrated database.

![architecture](/img/architecture.png)

# Getting Started

### Requirements

- Java version 21 or higher
<!-- - [svg2tikz](https://github.com/xyz2tex/svg2tikz) -->

### Usage

1. Clone the repository:

   ```
   git clone https://github.com/texttechnologylab/Unified-Dynamic-Annotation-Visualizer.git
   ```

2. In the root folder, create an `.env` file that holds the following environment variables:

   ```
   # UDAV Environment Configuration Example
   # Copy this file to .env and modify values as needed
   
   # ============================================
   # Database Configuration
   # ============================================
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
   
   # ============================================
   # DUUI Importer Configuration
   # ============================================
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
   
   # ============================================
   # Pipeline Configuration
   # ============================================
   PIPELINE_IMPORTER=true
   PIPELINE_IMPORTER_FOLDER=/app/data/pipelines
   PIPELINE_IMPORTER_REPLACE_IF_DIFFERENT=false
   
   # ============================================
   # Source Builder
   # ============================================
   SROUCE_BUILDER=false
   
   # ============================================
   # LLM API Configuration (optional)
   # ============================================
   LLM_BASE_URL=http://your-llm-server:8000
   LLM_API_TOKEN=your-api-token-here
   
   # ============================================
   # Java Options
   # ============================================
   # Adjust memory based on your system and data size
   JAVA_OPTS=-Xmx2048m -Xms1024m
   ```

3. Run the File Importer to import the annotation data

4. Start the `App.java` file

# File Importer

UDAV is using DUUI for importing the annotated documents. To do so, you can use the integrated DUUIImporter which is a Java-based component that serves as an importer for UDAV and ingests all annotated documents into the integrated database. You  need to configure the importer by setting environment variables in the `.env` file.

| Environmentvariable              | Default   | Description                                                                          |
|----------------------------------|-----------|--------------------------------------------------------------------------------------|
| `DUUI_IMPORTER`                  | false     | activate the importer                                                                |
| `DUUI_IMPORTER_PATH`             | -         | path to the DUUI pipeline descriptor file(s)                                         |
| `DUUI_IMPORTER_FILE_ENDING`      | .xmi      | specifies the file ending of the document(s) to be imported                          |
| `DUUI_IMPORTER_WORKERS`          | 4         | Number of parallel workers (rule: 1 per CPU core)                                    |
| `DUUI_IMPORTER_CAS_POOL_SIZE`    | 2*workers | UIMA CAS pool size                                                                   |
| `DUUI_IMPORTER_TYPE_SYSTEM_PATH` | -         | Optional: External TypeSystem XML file path (auto-detected from XMI/DUUI if not set) |

# Source Build and Generators

Work in progress...

# Data API

UDAV provides a RESTful API for fetching the data to be visualized on the frontend. The API is built using Java and Spring Boot, and it interacts with the integrated database to retrieve the necessary data based on the user's requests. The API endpoints are designed to support various types of queries, allowing for flexible data retrieval that can be easily consumed by the frontend components.
The API is constructed in a modular way. With Controller, Service and Repository layers, it follows the common best practices for building maintainable and scalable APIs. The Controller layer handles incoming HTTP requests and maps them to the appropriate Service methods. The Service layer contains the business logic for processing the requests and interacting with the Repository layer, which is responsible for database operations.
This makes it easy to extend the API with new endpoints or modify existing ones as needed, without affecting other parts of the application. The API also includes error handling and validation to ensure that requests are processed correctly and that any issues are communicated back to the client in a clear and consistent manner.

The only exception is the data API, which uses a custom ChartHandler to support different types of charts in a modular way. Each chart type has its own handler.

# Webpage

The frontend uses [Freemarker](https://freemarker.apache.org/) to render the html templates, [d3.js](https://d3js.org/) for some of the visualization components, [gridstack.js](https://gridstackjs.com/) for the draggable grid in the editor and [Bootstrap](https://getbootstrap.com/) for icons and styling.

## Structure

The frontend is located in the `resources` folder of the Java project. This directory contains all static assets and server-rendered templates, following a clear separation between static resources and template logic.

The `static` directory includes stylesheets, images, and JavaScript logic, while `templates` contains the FreeMarker templates used to render the HTML pages. Both directories are structured into `pages` and `shared` subdirectories: `pages` contains page-specific content for each page of the application, while `shared` provides reusable code and resources used across multiple pages. The `api` directory holds the api communication and the `widgets` directory contains the available widgets divided into `charts` and `static` widgets.

Global CSS variables are defined in `variables.css`. The widgets are registered in the `widgets.js` file. The `packages` directory contains third-party dependencies.

### Overview of the folder structure:

```
📁 resources
├─ 📁 static
│  ├─ 📁 css
│  │  ├─ 📁 pages
│  │  ├─ 📁 shared
│  │  └─ 📄 variables.css
│  ├─ 📁 img
│  ├─ 📁 js
│  │  ├─ 📁 api
│  │  ├─ 📁 pages
│  │  │  ├─ 📁 editor
│  │  │  │  ├─ 📁 configs
│  │  │  │  ├─ 📁 controller
│  │  │  │  ├─ 📁 utils
│  │  │  │  ├─ 📄 Editor.js
│  │  │  ├─ 📁 index
│  │  │  └─ 📁 view
│  │  │     ├─ 📁 filter
│  │  │     ├─ 📁 toolbar
│  │  │     ├─ 📁 utils
│  │  │     └─ 📄 View.js
│  │  ├─ 📁 shared
│  │  │  ├─ 📁 classes
│  │  │  └─ 📁 modules
│  │  ├─ 📁 widgets
│  │  │  ├─ 📁 charts
│  │  │  ├─ 📁 static
│  │  │  ├─ 📄 D3Visualization.js
│  │  │  └─ 📄 widgets.js
│  ├─ 📁 packages
│  └─ 📄 favicon.ico
└─ 📁 templates
   ├─ 📁 pages
   │  ├─ 📁 editor
   │  │  ├─ 📄 editor.ftl
   │  │  ├─ 📄 editorGrid.ftl
   │  │  └─ 📄 editorSidebar.ftl
   │  ├─ 📁 error
   │  ├─ 📁 index
   │  └─ 📁 view
   └─ 📁 shared
```

## Tutorials

If you want to change the primary color of the application or other general properties you can simply change them in the `variables.css` file.

Below are some instructions for adding new components to the webpage.

### Adding a new chart widget

To add a new chart widget, follow these steps:

1. Create a new JavaScript class in the `widgets/charts` folder. This class will define the widget's configuration and rendering.

2. Define the defaultConfig object. This is the initial configuration for the widget after creation. Include:
   - title: The display title of the chart.
   - type: The chart's type (must match the class name).
   - generator: Will be set by the user later.
   - options: Chart-specific options.
   - icon: The icon will be displayed in the editor.
   - w: The initial width of the widget in grid cells.
   - h: The initial height of the widget in grid cells.

3. Define the formConfig object. This configures the modal form where users can edit the chart's settings. Use the property paths in defaultConfig for the keys. Each field requires:
   - type: The input type (see `inputFactories.js` for available types).
   - label: The label displayed to the user.
   - options (optional): Additional configuration for the input.

4. To support the controls sidepanel and export functionality of the chart's toolbar, create a ControlsHandler and an ExportHandler in the constructor.

5. Provide a `data` property, an `init` and a `render` method.
   - The data property is used to cache the latest data which allows re-rendering the chart without fetching the data again.
   - The init method will be called once after creation of the widget and should contain the first data fetch and rendering as well as the configuration of the controls.
   - The render method will be called every time the chart data changes, for example after a filter is applied.

6. You can use this template as a starting point:

   ```js
   export default class NewChart {
     static defaultConfig = {
       type: "NewChart",
       title: "New Chart",
       generator: { id: "" },
       options: {},
       icon: "bi bi-chart",
       w: 6,
       h: 6,
     };
     static formConfig = {
       title: {
         type: "text",
         label: "Title",
       },
       "generator.id": {
         type: "select",
         label: "Generator",
         options: () => getGeneratorOptions("CategoryNumber"),
       },
     };

     constructor(root, getData, options) {
       this.controls = new ControlsHandler(this);
       this.exports = new ExportHandler(this);

       this.data;
     }

     init() {
       const data = this.getData();
       this.render(data);

       // Add controls ...
     }

     render(data) {
       this.clear();

       // Append svg to root
       // Render chart data ...

       this.data = data;
     }
   }
   ```

7. Finally, register the new widget by adding it to the `widgets.js` file so it's available in the editor:

   ```js
   export default {
     BarChart,
     LineChart,
     // ...
     NewChart,
   };
   ```

### Adding a new generator

To add a new generator, follow these steps:

1. Create a new JavaScript class in the `configs` folder of the editor page. This class will define the generator's configuration.

2. Define the generator's token. The token is a short string that will be displayed as an icon in the editor.

3. Define the defaultConfig object. This is the initial configuration for the generator after creation. Include:
   - name: The display name of the generator.
   - type: The generator's type (must match the class name).
   - settings: Generator-specific settings.
   - extends: An array of other generators this one extends (optional).

4. Define the formConfig object. This configures the modal form where users can edit the generator's settings. Use the property paths in defaultConfig for the keys. Each field requires:
   - type: The input type (see `inputFactories.js` for available types).
   - label: The label displayed to the user.
   - options (optional): Additional configuration for the input.

5. You can use this template as a starting point:

   ```js
   export default class NewGenerator {
     static token = "NG";
     static defaultConfig = {
       name: "New Generator",
       type: "NewGenerator",
       settings: {},
       extends: [],
     };
     static formConfig = {
       name: {
         type: "text",
         label: "Name",
       },
       extends: {
         type: "multiselect",
         label: "Extends (optional)",
         options: () => getGeneratorOptions("NewGenerator"),
       },
     };
   }
   ```

6. Finally, register the new generator by adding it to the `configs.js` file so it's available in the editor:

   ```js
   export default {
     TextFormatting,
     CategoryNumber,
     // ...
     NewGenerator,
   };
   ```

## Screenshots

![menu](/img/menu.png)
The main menu in UDAV for selecting and managing different pipeline views.

![example4](/img/example4.png)
This pipeline view shows diagrams with annotations for part-of-speech. Filters can be set at the corpus level in the sidebar on the left.

![example3](/img/example3.png)
This pipeline view provides some diagrams with annotations for part-of-speech and named entities as well as static text and image to provide a title (top) and captions below each diagram. In the middle, a text selected via UDAV is visualized, where the highlighting corresponds to the colors of the other diagrams due to a shared Generator. The control panel for filtering the displayed data of the bar chart on the right is opened.

![editor](/img/editor.png)
Example of the Editor in UDAV for creating and modifying Pipelines as well as for arranging the resulting widgets. The sources and generators can be modified in the sidebar on the left.

## Authors / Contributors
* Thiemo Dahmann
* Julian Schneider
* Philipp Stephan [<img src="./img/github.svg" height="20px">](https://github.com/phpp28) [<img src="./img/rg.svg" height="20px">](https://www.researchgate.net/profile/Philipp-Stephan-4)
* Giuseppe Abrami (Supervision) [<img src="./img/github.svg" height="20px">](https://github.com/abrami) [<img src="./img/rg.svg" height="20px">](https://www.researchgate.net/profile/Giuseppe-Abrami)
* Prof. Dr. Alexander Mehler (Supervision) [<img src="./img/rg.svg" height="20px">](https://www.researchgate.net/profile/Alexander-Mehler-2)

## Publications

Here we list all publications related to UDAV, which you can also refer to when citing UDAV in your work:

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

## License

This project is published under the AGPL-3.0 license.
