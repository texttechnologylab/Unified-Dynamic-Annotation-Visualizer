import getter from "../../widgets/widgets.js";
import sidepanels from "../../shared/modules/sidepanels.js";
import accordions from "../../shared/modules/accordions.js";
import ChatBot from "../../shared/classes/ChatBot.js";
import state from "./utils/viewState.js";
import { createTemplateElement } from "../../shared/modules/utils.js";
import { instruction } from "../../shared/modules/instruction.js";

/**
 * Headless export mode (`/view/{id}?export=1`).
 *
 * Only skips work that cannot affect an exported artefact: the chatbot, and the content of
 * Static* widgets (external image/video/iframe URLs that are never exported and are not part
 * of `state.charts`). It does NOT skip `state.corpusFilter.init()`: that call
 * populates `state.corpusFilter.filter`, which is embedded verbatim as `meta.corpus` in every
 * export, so skipping it would change every artefact.
 */
const exportMode =
  new URLSearchParams(
    (globalThis.location && globalThis.location.search) || "",
  ).get("export") === "1";

export default class View {
  constructor(pipeline) {
    this.pipeline = pipeline;
  }

  init(widgets) {
    // Expose runtime state for automation (headless API export) without changing export logic.
    globalThis.__UDAV_VIEW_STATE__ = state;
    globalThis.__UDAV_EXPORT_MODE__ = exportMode;
    globalThis.__UDAV_EXPORT_READY = false;

    this.initGrid(widgets);
    this.initSwitcher();
    this.initButtons();

    state.corpusFilter.init();
    sidepanels.init();
    accordions.init();

    if (!exportMode && document.querySelector(".dv-chat-bot")) {
      const chatbot = new ChatBot(instruction);
      chatbot.init();
    }
  }

  initSwitcher() {
    const dropdown = document.querySelector(".dv-dropdown");
    const trigger = document.querySelector(".dv-pipeline-switcher-trigger");
    trigger.addEventListener("click", () => {
      dropdown.classList.toggle("show");
    });
    document.addEventListener("click", (event) => {
      if (!dropdown.contains(event.target) && !trigger.contains(event.target)) {
        dropdown.classList.remove("show");
      }
    });
  }

  initButtons() {
    const resetButton = document.querySelector("#reset-button");
    const applyButton = document.querySelector("#apply-button");

    resetButton.addEventListener("click", () => {
      state.corpusFilter.reset();
      resetButton.classList.add("dv-hidden");

      for (const chart of state.charts) {
        chart.rerender(true);
      }
    });

    applyButton.addEventListener("click", () => {
      state.corpusFilter.apply();
      resetButton.classList.remove("dv-hidden");

      for (const chart of state.charts) {
        chart.rerender(true);
      }
    });
  }

  initGrid(widgets) {
    const grid = GridStack.init({
      column: 24,
      animate: false,
      float: true,
      disableDrag: true,
      disableResize: true,
    });

    const initPromises = [];

    grid.on("added", (_, items) => {
      items.forEach((item) => {
        const template = createTemplateElement(
          item.src ? "#static-widget-template" : "#chart-widget-template",
        );

        const Widget = getter[item.type];

        const root = item.el.querySelector(".grid-stack-item-content");
        root.replaceChildren(...template.childNodes);
        root.className = template.className;

        const isStatic = item.type.startsWith("Static");
        const widget = new Widget(root, { pipeline: this.pipeline, ...item });

        // Static widgets are never exported and are not part of state.charts, so in export
        // mode skip render(): it is the only thing that loads the external image/video/iframe
        // URLs they point at. Their grid cell is still laid out, so no chart is repositioned.
        if (!(exportMode && isStatic)) {
          const initResult = widget.init();
          if (initResult && typeof initResult.then === "function") {
            initPromises.push(initResult);
          }
        }

        if (!isStatic) {
          state.charts.push(widget);
        }
      });
    });

    // GridStack dispatches 'added' synchronously from within load() (it uses
    // EventTarget.dispatchEvent), so initPromises is fully populated once load() returns.
    grid.load(widgets);

    globalThis.__UDAV_READY__ = Promise.all(initPromises)
      // Text metrics in exported SVGs depend on webfonts having landed. Waiting for them here
      // makes readiness explicit instead of relying on a fixed post-load delay.
      .then(() => (document.fonts ? document.fonts.ready : undefined))
      .then(() => {
        globalThis.__UDAV_EXPORT_READY = true;
      })
      .catch((error) => {
        globalThis.__UDAV_EXPORT_ERROR = String((error && error.message) || error);
        globalThis.__UDAV_EXPORT_READY = true;
      });
  }
}
