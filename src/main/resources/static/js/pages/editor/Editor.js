import { modal } from "../../shared/classes/Modal.js";
import accordions from "../../shared/modules/accordions.js";
import getter from "./handler/getter.js";
import { identifierValid, widgetsValid } from "./utils/validate.js";
import state from "./utils/state.js";
import {
  createSource,
  createWidget,
  loadSources,
  saveSources,
} from "./utils/actions.js";
import SourceHandler from "./handler/generators/SourceHandler.js";
import { debounce } from "../../shared/modules/utils.js";
import {
  createPipeline,
  getPipelines,
  updatePipeline,
} from "../../api/pipelines.api.js";

export default class Editor {
  constructor() {
    this.input = document.querySelector("#identifier-input");
    this.defaults = {
      widgets: Object.values(getter.widgets).map((Handler) => Handler.defaults),
      generators: Object.values(getter.generators).map(
        (Handler) => Handler.defaults,
      ),
    };
  }

  init(config) {
    const container = document.querySelector(".dv-sources-container");

    accordions.init();

    this.initAvailableWidgets();
    this.initGrid();

    // Load existing data
    loadSources(config.sources || [], this.defaults.generators, container);
    state.grid.load(config.widgets || []);

    // Replace whitespaces in the id with dashes
    this.input.addEventListener(
      "input",
      ({ target }) => (this.input.value = target.value.replaceAll(" ", "-")),
    );

    // Initialize buttons
    document
      .querySelector(".dv-add-source-button")
      .addEventListener("click", () => {
        const handler = createSource(SourceHandler.defaults);

        container.prepend(handler.element);
        handler.init(this.defaults.generators);
      });

    document
      .querySelector("#discard-button")
      .addEventListener("click", () =>
        modal.confirm("Discard Changes", "Are you sure?", () => history.back()),
      );

    document
      .querySelector("#save-button")
      .addEventListener("click", () => this.validate());
  }

  initGrid() {
    state.grid = GridStack.init({
      column: 24,
      minRow: 12,
      float: true,
      alwaysShowResizeHandle: false,
      acceptWidgets: ".dv-available-widget-draggable",
    });

    // Update grid lines on resize
    const main = document.querySelector(".dv-main");
    const observer = new ResizeObserver(
      debounce(() => {
        main.style.setProperty(
          "--grid-size",
          state.grid.getCellHeight() + "px",
        );
      }, 10),
    );
    observer.observe(main);

    GridStack.setupDragIn(
      ".dv-available-widget-draggable",
      { helper: "clone" },
      this.defaults.widgets,
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
    const pipelines = await getPipelines();
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
        async () => await updatePipeline(config),
      );
    } else if (ok) {
      await createPipeline(config);
    }

    window.open("/view/" + config.id, "_self");
  }
}
