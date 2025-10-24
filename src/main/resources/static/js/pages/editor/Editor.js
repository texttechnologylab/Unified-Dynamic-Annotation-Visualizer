import Modal from "../../shared/classes/Modal.js";
import { getElementDimensions, randomId } from "../../shared/modules/utils.js";
import accordions from "../../shared/modules/accordions.js";
import getter from "./getter.js";

const defaults = {
  widgets: Object.values(getter.widgets).map((Handler) => Handler.defaults),
  generators: Object.values(getter.generators).map(
    (Handler) => Handler.defaults
  ),
};

export default class Editor {
  constructor() {
    this.modal = new Modal(document.querySelector(".dv-modal").parentElement);

    this.input = document.querySelector("#identifier-input");
    this.sources = [];
    this.derivedGenerators = [];
    this.generators = [];
    this.grid = null;
  }

  init(config) {
    accordions.init();

    this.initAvailableGenerators();
    this.initAvailableWidgets();
    this.initGrid();

    // Load existing data
    this.sources = config.sources || [];
    this.derivedGenerators = config.derivedGenerators || [];
    this.grid.load(config.widgets || []);

    // Replace whitespaces in the id with dashes
    this.input.addEventListener(
      "input",
      ({ target }) => (this.input.value = target.value.replaceAll(" ", "-"))
    );

    // Initialize buttons
    document
      .querySelector("#discard-button")
      .addEventListener("click", () => window.open("/", "_self"));

    document
      .querySelector("#save-button")
      .addEventListener("click", () => this.validate());
  }

  initGrid() {
    this.grid = GridStack.init({
      minRow: 6,
      float: true,
      acceptWidgets: ".dv-available-widget-draggable",
    });

    GridStack.setupDragIn(
      ".dv-available-widget-draggable",
      { helper: "clone" },
      defaults.widgets
    );

    // Append and initialize added widgets to item content
    this.grid.on("added", (_, items) => {
      items.forEach((item) => {
        item.id = item.id || randomId(item.type);

        const HandlerClass = getter.widgets[item.type];
        const handler = new HandlerClass(item);

        item.el.classList.remove("dv-available-widget-draggable");
        item.el.querySelector("i")?.remove();

        const content = item.el.querySelector(".grid-stack-item-content");
        if (content) {
          content.replaceChildren(...handler.element.childNodes);
          content.className = handler.element.className;
          handler.element = content;
        } else {
          item.el.prepend(handler.element);
        }

        handler.init(this.modal, this.grid);
      });
    });

    // Re-render chart with new dimensions after resize
    this.grid.on("resizestop", (_, el) => {
      const content = el.querySelector(".grid-stack-item-content");

      if (content._chart) {
        const dimensions = getElementDimensions(content);
        content._chart.setDimensions(dimensions.width, dimensions.height);
        content._chart.render(content._data);
      }
    });
  }

  initAvailableGenerators() {
    const added = document.querySelector("#added-generators");
    const available = document.querySelector("#available-generators");
    const template = document.querySelector("#available-generator-template");

    defaults.generators.forEach((generator) => {
      const element = template.content.cloneNode(true);

      element.querySelectorAll("span")[0].textContent = generator.short;
      element.querySelectorAll("span")[1].textContent = generator.name;
      element.querySelector("button").addEventListener("click", () => {
        generator.id = generator.id || randomId(generator.type);
        generator.name = "New " + generator.name;
        this.generators.push(generator);

        // Create generator
        const HandlerClass = getter.generators[generator.type];
        const handler = new HandlerClass(generator);

        added.append(handler.element);

        handler.init(this.modal, this.generators);
      });

      available.append(element);
    });
  }

  initAvailableWidgets() {
    const container = document.querySelector(".dv-available-widgets-container");
    const template = document.querySelector("#available-widget-template");

    defaults.widgets.forEach((widget) => {
      const element = template.content.cloneNode(true);

      element.querySelector("i").className = widget.icon;
      element.querySelector("span").title = widget.title;
      element.querySelector("span").textContent = widget.title;

      container.append(element);
    });
  }

  async validate() {
    const pipelines = await fetch("/api/pipelines").then((response) =>
      response.json()
    );
    const config = {
      id: this.input.value,
      sources: this.sources,
      derivedGenerators: this.derivedGenerators,
      widgets: this.grid.save(false),
    };
    const missing = config.widgets.filter(
      (widget) => widget?.generator?.id.trim() === ""
    );

    if (config.id.trim() === "") {
      this.modal.alert(
        "Missing Identifier",
        "Please provide an identifier for the pipeline."
      );
    } else if (missing.length > 0) {
      this.modal.alert(
        "Missing Generators",
        "The following widgets have no generator assigned: " +
          missing.map((w) => w.title).join(", ")
      );
    } else if (pipelines.includes(this.input.value)) {
      this.modal.confirm(
        `Overwrite "${this.input.value}"`,
        "This pipeline already exists. Do you want to overwrite it?",
        () => this.saveConfig("PUT", config)
      );
    } else {
      this.saveConfig("POST", config);
    }
  }

  saveConfig(method, config) {
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
