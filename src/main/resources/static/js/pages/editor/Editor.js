import accordions from "../../shared/modules/accordions.js";
import { identifierValid, widgetsValid } from "./utils/validate.js";
import state from "./utils/state.js";
import {
  createSource,
  createWidget,
  loadSources,
  saveSources,
} from "./utils/actions.js";
import { debounce } from "../../shared/modules/utils.js";
import {
  createPipeline,
  getPipelines,
  updatePipeline,
} from "../../api/pipelines.api.js";
import widgets from "../../widgets/widgets.js";
import Source from "./configs/Source.js";

export default class Editor {
  constructor() {
    this.widgetDefaults = Object.values(widgets).map(
      (Widget) => Widget.defaultConfig,
    );
  }

  init(config) {
    accordions.init();

    this.initAvailableWidgets();
    this.initGrid();

    // Load existing data
    loadSources(config.sources || []);
    state.grid.load(config.widgets || []);

    // Replace whitespaces in the id with dashes
    const input = document.querySelector("#identifier-input");
    input.addEventListener(
      "input",
      ({ target }) => (input.value = target.value.replaceAll(" ", "-")),
    );

    // Initialize buttons
    const container = document.querySelector(".dv-sources-container");
    document
      .querySelector(".dv-add-source-button")
      .addEventListener("click", () => {
        const source = createSource(Source.defaultConfig);

        container.prepend(source.root);
        source.init();
      });

    document
      .querySelector("#discard-button")
      .addEventListener("click", () =>
        state.modal.confirm("Discard Changes", "Are you sure?", () =>
          history.back(),
        ),
      );

    document
      .querySelector("#save-button")
      .addEventListener("click", () => this.validate(input.value));
  }

  initGrid() {
    state.grid = GridStack.init({
      column: 24,
      minRow: 12,
      float: true,
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
      this.widgetDefaults,
    );

    // Append and initialize added widgets to item content
    state.grid.on("added", (_, items) => {
      items.forEach((item) => {
        const widget = createWidget(item);

        const content = item.el.querySelector(".grid-stack-item-content");
        if (content) {
          content.replaceChildren(...widget.root.childNodes);
          content.className = widget.root.className;
          widget.root = content;
        } else {
          item.el.prepend(widget.root);
        }
        widget.init();
      });
    });
  }

  initAvailableWidgets() {
    const container = document.querySelector(".dv-available-widgets-container");
    const template = document.querySelector("#available-widget-template");

    this.widgetDefaults.forEach((widget) => {
      const element = template.content.cloneNode(true);

      element.querySelector("i").className = widget.icon;
      element.querySelector("span").title = widget.title;
      element.querySelector("span").textContent = widget.title;
      delete widget.icon;

      container.append(element);
    });
  }

  async validate(id) {
    const pipelines = await getPipelines();
    const config = {
      id: id,
      sources: saveSources(),
      widgets: state.grid.save(false),
    };

    const ok = identifierValid(config) && widgetsValid(config);

    if (ok && pipelines.includes(config.id)) {
      state.modal.confirm(
        `Overwrite "${config.id}"`,
        "This pipeline already exists. Do you want to overwrite it?",
        async () => {
          await updatePipeline(config);
          window.open("/view/" + config.id, "_self");
        },
      );
    } else if (ok) {
      await createPipeline(config);
      window.open("/view/" + config.id, "_self");
    }
  }
}
