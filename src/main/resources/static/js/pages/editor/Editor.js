import { modal } from "../../shared/classes/Modal.js";
import { getElementDimensions } from "../../shared/modules/utils.js";
import accordions from "../../shared/modules/accordions.js";
import getter from "./getter.js";
import { identifierValid, widgetsValid } from "./utils/validate.js";
import state from "./utils/state.js";
import {
  createSource,
  createWidget,
  loadSources,
  saveSources,
} from "./utils/actions.js";

export default class Editor {
  constructor() {
    this.input = document.querySelector("#identifier-input");
    this.defaults = {
      widgets: Object.values(getter.widgets).map((Handler) => Handler.defaults),
      generators: Object.values(getter.generators).map(
        (Handler) => Handler.defaults
      ),
    };
  }

  init(config) {
    accordions.init();

    this.initAvailableWidgets();
    this.initGrid();

    // Load existing data
    loadSources(config.sources || []);
    state.grid.load(config.widgets || []);

    // Replace whitespaces in the id with dashes
    this.input.addEventListener(
      "input",
      ({ target }) => (this.input.value = target.value.replaceAll(" ", "-"))
    );

    const container = document.querySelector(".dv-sources-container");

    // Initialize buttons
    document
      .querySelector(".dv-add-source-button")
      .addEventListener("click", () => {
        const handler = createSource();

        container.prepend(handler.element);
        handler.init(this.defaults.generators);
      });

    document
      .querySelector("#discard-button")
      .addEventListener("click", () =>
        modal.confirm("Discard Changes", "Are you sure?", () =>
          window.open("/", "_self")
        )
      );

    document
      .querySelector("#save-button")
      .addEventListener("click", () => this.validate());
  }

  initGrid() {
    state.grid = GridStack.init({
      minRow: 6,
      float: true,
      acceptWidgets: ".dv-available-widget-draggable",
    });

    GridStack.setupDragIn(
      ".dv-available-widget-draggable",
      { helper: "clone" },
      this.defaults.widgets
    );

    // Append and initialize added widgets to item content
    state.grid.on("added", (_, items) => {
      items.forEach((item) => {
        const handler = createWidget(item);

        const content = item.el.querySelector(".grid-stack-item-content");
        if (content) {
          content.replaceChildren(...handler.element.childNodes);
          content.className = handler.element.className;
          handler.element = content;
        } else {
          item.el.prepend(handler.element);
        }
        handler.init();
      });
    });

    // Re-render chart with new dimensions after resize
    state.grid.on("resizestop", (_, el) => {
      const content = el.querySelector(".grid-stack-item-content");

      if (content._chart) {
        const dimensions = getElementDimensions(content);
        content._chart.setDimensions(dimensions.width, dimensions.height);
        content._chart.render(content._data);
      }
    });
  }

  initAvailableWidgets() {
    const container = document.querySelector(".dv-available-widgets-container");
    const template = document.querySelector("#available-widget-template");

    this.defaults.widgets.forEach((widget) => {
      const element = template.content.cloneNode(true);

      element.querySelector("i").className = widget.icon;
      element.querySelector("span").title = widget.title;
      element.querySelector("span").textContent = widget.title;
      delete widget.icon;

      container.append(element);
    });
  }

  async validate() {
    const pipelines = await fetch("/api/pipelines").then((response) =>
      response.json()
    );
    const config = {
      id: this.input.value,
      sources: saveSources(),
      widgets: state.grid.save(false),
    };

    const ok = identifierValid(config) && widgetsValid(config);

    if (ok && pipelines.includes(config.id)) {
      modal.confirm(
        `Overwrite "${config.id}"`,
        "This pipeline already exists. Do you want to overwrite it?",
        () => this.sendConfig("PUT", config)
      );
    } else if (ok) {
      this.sendConfig("POST", config);
    }
  }

  sendConfig(method, config) {
    const options = {
      method,
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(config),
    };

    fetch("/api/pipelines", options).then(() => window.open("/", "_self"));
  }
}
