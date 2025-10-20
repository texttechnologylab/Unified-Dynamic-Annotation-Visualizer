import {
  createElement,
  getElementDimensions,
} from "../../../../../shared/modules/utils.js";
import FormHandler from "../../FormHandler.js";
import LineChart from "../../../../view/widgets/charts/LineChart.js";

export default class LineChartHandler extends FormHandler {
  static defaults = {
    type: "LineChart",
    title: "Line Chart",
    generator: { id: "" },
    options: {
      line: true,
      dots: true,
    },
    icon: "bi bi-graph-up",
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
    this.initButtons(modal, "Line Chart Options", grid);

    this.element._chart = new LineChart(this.element, "", {
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
    const lineInput = this.createSelect(
      "line",
      "Draw lines",
      ["yes", "no"],
      this.item.options.line ? "yes" : "no"
    );
    const dotsInput = this.createSelect(
      "dots",
      "Draw dots",
      ["yes", "no"],
      this.item.options.dots ? "yes" : "no"
    );

    return createElement("form", { className: "dv-form-column" }, [
      titleInput,
      generatorInput,
      lineInput,
      dotsInput,
    ]);
  }

  saveForm(form) {
    // Save form input
    const data = Object.fromEntries(new FormData(form));
    this.item.title = data.title;
    this.item.generator.id = data.generator;
    this.item.options.line = data.line === "yes";
    this.item.options.dots = data.dots === "yes";

    // Update title
    this.span.textContent = data.title;

    // Update chart
    this.element._chart.line = this.item.options.line;
    this.element._chart.dots = this.item.options.dots;
    this.element._chart.render(this.element._data);
  }
}

const data = [
  {
    name: "Dataset",
    color: "#00618f",
    coordinates: [
      {
        y: 5,
        x: 0,
      },
      {
        y: 20,
        x: 20,
      },
      {
        y: 10,
        x: 40,
      },
      {
        y: 40,
        x: 60,
      },
      {
        y: 5,
        x: 80,
      },
      {
        y: 60,
        x: 100,
      },
    ],
  },
];
