import accordions from "../../shared/modules/accordions.js";
import { widgetsValid, sourcesValid } from "./utils/editorValidations.js";
import state from "./utils/editorState.js";
import {
  createSource,
  createWidget,
  loadSources,
} from "./utils/editorActions.js";
import { debounce, randomId } from "../../shared/modules/utils.js";
import {
  createPipeline,
  getPipelines,
  updatePipeline,
} from "../../api/pipelines.api.js";
import widgets from "../../widgets/widgets.js";
import Source from "./configs/Source.js";

export default class Editor {
  constructor() {
    this.showWarning = true;
    this.widgetDefaults = Object.values(widgets).map(
      (Widget) => Widget.defaultConfig,
    );
  }

  init(config) {
    accordions.init();

    this.initAvailableWidgets();
    this.initGrid();

    const safeArray = (value) => (Array.isArray(value) ? value : []);

    // Load existing data
    state.id = config.id || randomId("pipeline");
    loadSources(safeArray(config.sources), safeArray(config.generators));
    state.grid.load(safeArray(config.widgets));

    // Warn on leaving
    window.addEventListener("beforeunload", (event) => {
      if (this.showWarning) {
        event.preventDefault();
        return "";
      }
    });

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
        const controller = createSource(Source.defaultConfig);

        // TODO: api.createSource(state.id, controller.item);
        container.append(controller.root);
        controller.init();
      });

    document.querySelector("#discard-button").addEventListener("click", () => {
      state.modal.confirm("Discard Changes", "Are you sure?", async () => {
        const pipelines = await getPipelines();
        this.showWarning = false;

        // Delete temp pipeline
        // TODO: api.deletePipeline(state.id);

        if (pipelines.find((p) => p.id === state.id)) {
          window.open("/view/" + state.id, "_self");
        } else {
          window.open("/", "_self");
        }
      });
    });

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
        const controller = createWidget(item);

        const content = item.el.querySelector(".grid-stack-item-content");
        if (content) {
          content.replaceChildren(...controller.root.childNodes);
          content.className = controller.root.className;
          controller.root = content;
        } else {
          item.el.prepend(controller.root);
        }
        controller.init();
      });
    });
  }

  initAvailableWidgets() {
    const staticCont = document.querySelector(".dv-static-widgets-container");
    const dynamicCont = document.querySelector(".dv-dynamic-widgets-container");
    const template = document.querySelector("#available-widget-template");

    this.widgetDefaults.forEach((widget) => {
      const element = template.content.cloneNode(true);

      element.querySelector("i").className = widget.icon;
      element.querySelector(".dv-available-widget").title = widget.title;
      element.querySelector("span").textContent = widget.title;
      delete widget.icon;

      if (widget.type.startsWith("Static")) {
        staticCont.append(element);
      } else {
        dynamicCont.append(element);
      }
    });
  }

  async validate(name) {
    const pipelines = await getPipelines();
    const config = {
      id: state.id,
      name: name,
      sources: state.sources,
      generators: state.generators,
      widgets: state.grid.save(false),
    };

    const ok = widgetsValid(config) && sourcesValid(config);

    if (ok && pipelines.find((p) => p.id === config.id)) {
      state.modal.confirm(
        `Overwrite "${config.name}"`,
        "This pipeline already exists. Do you want to overwrite it?",
        () => {
          state.modal.loading("Updating pipeline, please wait...");

          updatePipeline(config)
            .then(() => {
              this.showWarning = false;
              window.open("/view/" + config.id, "_self");
            })
            .catch(() => {
              state.modal.alert(
                "Internal Server Error",
                "An error occurred while updating the pipeline.",
              );
            });
        },
      );
    } else if (ok) {
      state.modal.loading("Creating pipeline, please wait...");

      // Promote temp pipeline
      // TODO: api.promotePipeline(state.id, config.name, config.widgets);
      createPipeline(config)
        .then(() => {
          this.showWarning = false;
          window.open("/view/" + config.id, "_self");
        })
        .catch(() => {
          state.modal.alert(
            "Internal Server Error",
            "An error occurred while creating the pipeline.",
          );
        });
    }
  }
}
