import getter from "../../widgets/widgets.js";
import sidepanels from "../../shared/modules/sidepanels.js";
import accordions from "../../shared/modules/accordions.js";
import ChatBot from "../../shared/classes/ChatBot.js";
import state from "./utils/viewState.js";
import { createTemplateElement } from "../../shared/modules/utils.js";
import { instruction } from "../../shared/modules/instruction.js";

export default class View {
  constructor(pipeline) {
    this.pipeline = pipeline;
  }

  init(widgets) {
    this.initGrid(widgets);
    this.initSwitcher();
    this.initButtons();

    state.corpusFilter.init();
    sidepanels.init();
    accordions.init();

    // if (document.querySelector(".dv-chat-bot")) {
    //   const chatbot = new ChatBot(instruction);
    //   chatbot.init();
    // }
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

    grid.on("added", (_, items) => {
      items.forEach((item) => {
        const template = createTemplateElement(
          item.src ? "#static-widget-template" : "#chart-widget-template",
        );

        const Widget = getter[item.type];

        const root = item.el.querySelector(".grid-stack-item-content");
        root.replaceChildren(...template.childNodes);
        root.className = template.className;

        const widget = new Widget(root, { pipeline: this.pipeline, ...item });
        widget.init();

        if (!item.type.startsWith("Static")) {
          state.charts.push(widget);
        }
      });
    });

    grid.load(widgets);
  }
}
