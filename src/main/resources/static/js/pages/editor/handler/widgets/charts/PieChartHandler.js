import {
  createElement,
  getElementDimensions,
} from "../../../../../shared/modules/utils.js";
import FormHandler from "../../FormHandler.js";
import PieChart from "../../../../view/widgets/charts/PieChart.js";

export default class PieChartHandler extends FormHandler {
  static defaults = {
    type: "PieChart",
    title: "Pie Chart",
    generator: { id: "" },
    options: {
      hole: 0,
    },
    icon: "bi bi-pie-chart",
    w: 3,
    h: 3,
  };

  constructor(item) {
    const template = document.querySelector("#default-chart-template");
    super(template.content.cloneNode(true).children[0]);

    this.item = item;
    this.span = this.element.querySelector("span");
  }

  init(modal, grid) {
    this.span.textContent = this.item.title;
    this.initButtons(modal, "Pie Chart Options", grid);

    this.element._chart = new PieChart(this.element, "", {
      ...getElementDimensions(this.element),
      ...this.item.options,
    });
    this.element._data = data;
    this.element._chart.render(this.element._data);
  }

  createForm() {
    const titleInput = this.createTextInput("title", "Title", this.item.title);
    const generatorInput = this.createTextInput(
      "generator",
      "Generator",
      this.item.generator.id
    );
    const holeInput = this.createNumberInput(
      "hole",
      "Hole (Doughnut)",
      this.item.options.hole,
      0,
      1000
    );

    return createElement("form", { className: "dv-form-column" }, [
      titleInput,
      generatorInput,
      holeInput,
    ]);
  }

  saveForm(form) {
    // Save form input
    const data = Object.fromEntries(new FormData(form));
    this.item.title = data.title;
    this.item.generator.id = data.generator;
    this.item.options.hole = data.hole || 0;

    // Update title
    this.span.textContent = data.title;

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
