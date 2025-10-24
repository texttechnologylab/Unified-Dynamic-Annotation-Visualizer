import {
  createElement,
  getElementDimensions,
} from "../../../../../shared/modules/utils.js";
import FormHandler from "../../FormHandler.js";
import BarChart from "../../../../view/widgets/charts/BarChart.js";

export default class BarChartHandler extends FormHandler {
  static defaults = {
    type: "BarChart",
    title: "Bar Chart",
    generator: { id: "" },
    options: {
      horizontal: false,
    },
    icon: "bi bi-bar-chart",
    w: 4,
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
    this.initButtons(modal, "Bar Chart Options", () =>
      grid.removeWidget(this.item.el)
    );

    this.element._chart = new BarChart(this.element, "", {
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
    const orientationInput = this.createSelect(
      "orientation",
      "Orientation",
      ["horizontal", "vertical"],
      this.item.options.horizontal ? "horizontal" : "vertical"
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
