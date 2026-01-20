import { createElement } from "../../../../../shared/modules/utils.js";
import FormHandler from "../../FormHandler.js";
import BarChart from "../../../../view/widgets/charts/BarChart.js";
import { prepareGenerators, safeValue } from "../../../utils/actions.js";
import state from "../../../utils/state.js";

export default class BarChartHandler extends FormHandler {
  static defaults = {
    type: "BarChart",
    title: "Bar Chart",
    generator: { id: "" },
    options: {
      horizontal: false,
    },
    icon: "bi bi-bar-chart",
    w: 8,
    h: 6,
  };

  constructor(item) {
    const template = document.querySelector("#default-chart-template");
    super(template.content.cloneNode(true).children[0]);

    this.item = item;
    this.span = this.element.querySelector("span");
  }

  init() {
    this.span.textContent = this.item.title;
    this.initButtons("Bar Chart Options", () => {
      state.grid.removeWidget(this.item.el);
    });

    this.showAlert(!this.item.generator.id);

    this.element._chart = new BarChart(this.element, "", this.item.options);
    this.element._data = data;
    this.element._chart.render(this.element._data);
  }

  createForm() {
    const generatorOptions = prepareGenerators(["CategoryNumber"]);

    const titleInput = this.createTextInput("title", "Title", this.item.title);
    const generatorInput = this.createSelect(
      "generator",
      "Generator",
      generatorOptions,
      safeValue(generatorOptions, this.item.generator.id),
    );
    const orientationInput = this.createSelect(
      "orientation",
      "Orientation",
      ["horizontal", "vertical"],
      this.item.options.horizontal ? "horizontal" : "vertical",
    );

    return createElement("form", { className: "dv-form-column" }, [
      titleInput,
      generatorInput,
      orientationInput,
    ]);
  }

  saveForm(form) {
    // Save form input
    this.item.title = form.title;
    this.item.generator.id = form.generator;
    this.item.options.horizontal = form.orientation === "horizontal";

    this.showAlert(!form.generator);

    // Update title
    this.span.textContent = form.title;

    // Update chart
    this.element._chart.horizontal = this.item.options.horizontal;
    this.element._chart.render(this.element._data);
  }
}

const data = [
  {
    label: "Label 1",
    value: 140,
    color: "#00618f",
  },
  {
    label: "Label 2",
    value: 73,
    color: "#3a4856",
  },
  {
    label: "Label 3",
    value: 56,
    color: "#9eadbd",
  },
];
