import getter from "./getter.js";
import { corpusFilter } from "./filter/CorpusFilter.js";
import sidepanels from "../../shared/modules/sidepanels.js";
import accordions from "../../shared/modules/accordions.js";
import dropdowns from "../../shared/modules/dropdowns.js";
import { getData } from "../../api/data.api.js";

export default class View {
  constructor(pipeline) {
    corpusFilter.init();
    corpusFilter.apply();
    sidepanels.init();
    accordions.init();
    dropdowns.init();

    // Initialize pipeline switcher
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

    // Initialize corpus filter apply button
    document.querySelector("#apply-button").addEventListener("click", () => {
      corpusFilter.apply();

      for (const chart of this.charts) {
        chart.fetch().then((data) => chart.render(data));
      }
    });

    this.pipeline = pipeline;
    this.charts = [];
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

  initWidgets(widgets) {
    document.querySelectorAll("[data-dv-widget]").forEach((node) => {
      const id = node.dataset.dvWidget;
      const config = widgets.find((conf) => conf.id === id);

      if (getter._dynamic[config.type]) {
        const WidgetClass = getter._dynamic[config.type];

        const chart = new WidgetClass(
          node,
          (filters) => getData(this.pipeline, id, filters),
          config.options,
        );
        chart.init();

        this.charts.push(chart);
      } else if (getter._static[config.type]) {
        const WidgetClass = getter._static[config.type];

        new WidgetClass(node, config.src, config.options).init();
      }
    });
  }
}
