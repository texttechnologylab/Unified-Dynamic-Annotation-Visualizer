import { createElement } from "../../../../../shared/modules/utils.js";
import FormHandler from "../../FormHandler.js";
import PieChart from "../../../../view/widgets/charts/PieChart.js";
import { prepareGenerators, safeValue } from "../../../utils/actions.js";
import state from "../../../utils/state.js";

export default class PieChartHandler extends FormHandler {
  static defaults = {
    type: "PieChart",
    title: "Pie Chart",
    generator: { id: "" },
    options: {
      hole: 0,
    },
    icon: "bi bi-pie-chart",
    w: 6,
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
    this.initButtons("Pie Chart Options", () => {
      state.grid.removeWidget(this.item.el);
    });

    this.showAlert(!this.item.generator.id);

    this.element._chart = new PieChart(this.element, "", this.item.options);
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
    const holeInput = this.createRangeSlider(
      "hole",
      "Hole (Doughnut)",
      this.item.options.hole,
      0,
      500,
    );

    return createElement("form", { className: "dv-form-column" }, [
      titleInput,
      generatorInput,
      holeInput,
    ]);
  }

  saveForm(form) {
    // Save form input
    this.item.title = form.title;
    this.item.generator.id = form.generator;
    this.item.options.hole = parseFloat(form.hole) || 0;

    this.showAlert(!form.generator);

    // Update title
    this.span.textContent = form.title;

    // Update chart
    this.element._chart.hole = this.item.options.hole;
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
