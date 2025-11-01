# Unified Dynamic Annotation Visualizer
Short introduction

## Features
- Feature 1
- Feature 2

## Getting Started
Instructions to get started

# Frontend

The frontend uses [Freemarker](https://freemarker.apache.org/) to render the html templates, [d3.js](https://d3js.org/) for some of the visualization components, [gridstack.js](https://gridstackjs.com/) for the draggable grid in the editor and [Bootstrap](https://getbootstrap.com/) for icons and styling.

## Structure

The frontend is located in the `resources` folder of the Java project. This directory contains all static assets and server-rendered templates, following a clear separation between static resources and template logic.

The `static` directory includes stylesheets, images, and JavaScript logic, while `templates` contains the FreeMarker templates used to render the HTML pages. Both directories are structured into `pages` and `shared` subdirectories: `pages` contains page-specific content for each page of the application, while `shared` provides reusable code and resources used across multiple pages.

Global CSS variables are defined in `variables.css`. The visualization components are registered in the `getter.js` files within the `editor` and `view` directories . The `packages` directory contains third-party dependencies.

Each template page includes a main file and may also have a `components` subdirectory for modular, page-specific templates.

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
│  │  ├─ 📁 pages
│  │  │  ├─ 📁 editor
│  │  │  │  ├─ 📁 handler
│  │  │  │  ├─ 📄 Editor.js
│  │  │  │  └─ 📄 getter.js
│  │  │  ├─ 📁 index
│  │  │  └─ 📁 view
│  │  │     ├─ 📁 filter
│  │  │     ├─ 📁 toolbar
│  │  │     ├─ 📁 widgets
│  │  │     ├─ 📄 getter.js
│  │  │     └─ 📄 View.js
│  │  └─ 📁 shared
│  │     ├─ 📁 classes
│  │     └─ 📁 modules
│  ├─ 📁 packages
│  └─ 📄 favicon.ico
└─ 📁 templates
   ├─ 📁 pages
   │  ├─ 📁 editor
   │  │  ├─ 📁 components
   │  │  └─ 📄 editor.ftl
   │  ├─ 📁 error
   │  ├─ 📁 index
   │  └─ 📁 view
   └─ 📁 shared
```

## Tutorials

The following are some instructions for adding new components to the pipeline view or the editor.

### Adding a new widget to the pipeline view

Create a new JavaScript class in the widgets folder of the view page. This class will get the html element of the chart area as `root`, the data api endpoint as `endpoint` and some `options`. This options could for example be the width or height of the chart area or custom settings for the new chart.

The new class can optionally extend the `D3Visualization` class, which already has methods for fetching the data, creating and clearing the chart svg and the tooltip event handlers. To support the controls and export functionality of the toolbar, a `ControlsHandler` and an `ExportHandler` should be created and updated.

The new class also needs to provide an `init` and a `render` method. The init method will be called once after creation of the widget and should contain the first data fetch and rendering as well as the configuration of the controls. The render method will be called every time the chart data changes, for example after a filter is applied.

You can use the following code as a starting point:

```js
export default class NewChart extends D3Visualization {
  constructor(root, endpoint, options) {
    super(root, endpoint, margin, width, height);

    this.controls = new ControlsHandler();
    this.exports = new ExportHandler();
  }

  async init() {
    const data = await this.fetch();
    this.render(data);

    // Add controls ...
  }

  render(data) {
    this.clear();

    // Render chart data ...

    // Pass data to export handler
    this.exports.update();
  }
}
```

Finally the new widget class has to be registered in the `getter.js` to be available in the pipeline view page:

```js
const _dynamic = {
  BarChart,
  LineChart,
  // ...
  NewChart,
};
```

### Adding a new widget to the editor

Create a new JavaScript class in the `handler/widgets` folder of the editor page. This class will get the gridstack item which contains the widget configuration.

The new handler can optionally extend the `FormHandler` class, which already has some methods for creating formular fields like text inputs and selects and initialization of the buttons.

A static field with default values for the widget configuration must be set.

The handler must define three methods: `init`, `createForm`, and `saveForm`. The `init` method is called once when the handler is created and should contain the initialization logic, such as setting up buttons or event listeners. The `createForm` method is executed each time the settings modal is opened. It must return a DOM element that contains the form fields used to modify the configuration. The `saveForm` method is triggered whenever the user saves changes in the modal. It should apply those changes to both the internal state and the DOM.

You can use the following code as a starting point:

```js
export default class NewChartHandler extends FormHandler {
  static defaults = {
    type: "NewChart",
    title: "New Chart",
    generator: { id: "" },
    options: {},
    icon: "",
    w: 4,
    h: 3,
  };

  constructor(item) {
    const template = document.querySelector("#default-chart-template");
    super(template.content.cloneNode(true).children[0]);

    this.item = item;
  }

  init() {
    this.initButtons("Chart Options", () => {
      state.grid.removeWidget(this.item.el);
    });

    // Set title, render chart preview ...
  }

  createForm() {
    // Create and return input fields ...
  }

  saveForm(form) {
    // Save input to this.item ...
    // Update title, chart preview ...
  }
}
```

Finally the new widget handler has to be registered in the `getter.js` to be available in the editor page:

```js
const widgets = {
  BarChart: BarChartHandler,
  PieChart: PieChartHandler,
  // ...
  NewChart: NewChartHandler,
};
```

### Adding a new generator to the editor

Create a new JavaScript class in the `handler/generators` folder of the editor page. This class will get the generator configuration.

The new handler can optionally extend the `FormHandler` class, which already has some methods for creating formular fields like text inputs and selects and initialization of the buttons.

Static fields for the generator token (will be displayed as an icon for the generator), a short description of the generator (will be displayed in a tooltip) and the default values for the generator configuration must be set.

The handler must define three methods: `init`, `createForm`, and `saveForm`. The `init` method is called once when the handler is created and should contain the initialization logic, such as setting up buttons or event listeners. The `createForm` method is executed each time the settings modal is opened. It must return a DOM element that contains the form fields used to modify the configuration. The `saveForm` method is triggered whenever the user saves changes in the modal. It should apply those changes to both the internal state and the DOM.

You can use the following code as a starting point:

```js
export default class NewGeneratorHandler extends FormHandler {
  static token = "NG";
  static description = "Description of the NewGenerator generator.";
  static defaults = {
    name: "NewGenerator",
    type: "NewGenerator",
    source: "",
    settings: {},
  };

  constructor(generator) {
    const template = document.querySelector("#added-generator-template");
    super(template.content.cloneNode(true).children[0]);

    this.generator = generator;
  }

  init() {
    this.initButtons("Generator Options", () => {
      // Remove generator from the dom
      this.element.remove();

      // Remove generator from the state list
      removeGenerator(this.generator);
    });

    // Set title ...
  }

  createForm() {
    // Create and return input fields ...
  }

  saveForm(form) {
    // Save input to this.generator ...
    // Update title ...
  }
}
```

Finally the new generator handler has to be registered in the `getter.js` to be available in the editor page:

```js
const generators = {
  TextFormatting: TextFormattingHandler,
  // ...
  NewGenerator: NewGeneratorHandler,
};
```


## Contributing
Guidelines for contributing

## Contributors
List of contributors
- Contributor 1 
- Contributor 2

## License
License information
