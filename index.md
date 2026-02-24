# Unified Dynamic Annotation Visualizer

UDAV is designed to enable different disciplines to display their automatic pre-processing results in a schema-based and reproducible, dynamic and interactive way without the need to hard-code manual and user-defined visualizations for each new project.

## Features

- Feature 1
- Feature 2
- Feature 3
- Feature 4

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

![architecture](architecture.png)

# Getting Started

### Requirements

- Java version 21 or higher
- [svg2tikz](https://github.com/xyz2tex/svg2tikz)

### Usage

1. Clone the repository:

   ```
   git clone https://github.com/texttechnologylab/Unified-Dynamic-Annotation-Visualizer.git
   ```

2. In the root folder, create an `.env` file that holds the following environment variables:

   ```
   DB_URL=
   DB_USER=
   DB_PASS=
   DB_SCHEMA=
   DB_BATCH_SIZE=
   DB_MAX_IDENT=
   DB_DIALECT=
   SROUCE_BUILDER=
   DUUI_IMPORTER=
   PIPELINE_IMPORTER_REPLACE_IF_DIFFERENT=
   LLM_BASE_URL=
   LLM_API_TOKEN=
   ```

3. Run the File Importer to import the annotation data

4. Start the `App.java` file

# File Importer

Work in progress...

# Source Build and Generators

Work in progress...

# Data API

Work in progress...

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

Work in progress...

## License

This project is published under the AGPL-3.0 license.
