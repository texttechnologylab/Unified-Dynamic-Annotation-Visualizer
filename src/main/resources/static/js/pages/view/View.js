import widgets from "../../widgets/widgets.js";
import sidepanels from "../../shared/modules/sidepanels.js";
import accordions from "../../shared/modules/accordions.js";
import dropdowns from "../../shared/modules/dropdowns.js";
import { getData } from "../../api/data.api.js";
import ChartGPT from "../../shared/classes/ChartGPT.js";
import state from "./utils/viewState.js";

export default class View {
  constructor(pipeline) {
    this.pipeline = pipeline;
  }

  init(widgets) {
    state.corpusFilter.init();
    sidepanels.init();
    accordions.init();
    dropdowns.init();

    const chatBot = new ChartGPT(
      "You are an assistant called ChartGPT. Do NOT use markdown in your answers.",
    );
    chatBot.init();

    this.initSwitcher();
    this.initButtons();
    this.initGrid(widgets);
    this.initWidgets(widgets);
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
        chart.fetch().then((data) => chart.render(data));
      }
    });

    applyButton.addEventListener("click", () => {
      state.corpusFilter.apply();
      resetButton.classList.remove("dv-hidden");

      for (const chart of state.charts) {
        chart.fetch().then((data) => chart.render(data));
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

    grid.load(widgets);
  }

  initWidgets(configs) {
    document.querySelectorAll("[data-dv-widget]").forEach((node) => {
      const id = node.dataset.dvWidget;
      const config = configs.find((conf) => conf.id === id);

      const Widget = widgets[config.type];

      const widget = new Widget(
        node,
        config.src || ((filters) => getData(this.pipeline, id, filters)),
        config.options,
      );
      widget.init();

      if (!config.src) {
        state.charts.push(widget);
      }
    });
  }
}
