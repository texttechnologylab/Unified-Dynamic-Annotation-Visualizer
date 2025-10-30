import {
  createElement,
  getElementDimensions,
} from "../../../../../shared/modules/utils.js";
import FormHandler from "../../FormHandler.js";
import LineChart from "../../../../view/widgets/charts/LineChart.js";
import { prepareGenerators, safeValue } from "../../../utils/helper.js";

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

  constructor(item, generators) {
    const template = document.querySelector("#default-chart-template");
    super(template.content.cloneNode(true).children[0]);

    this.item = item;
    this.generators = generators;
    this.span = this.element.querySelector("span");
  }

  init(grid) {
    this.span.textContent = this.item.title;
    this.initButtons("Line Chart Options", () =>
      grid.removeWidget(this.item.el)
    );

    this.showAlert(!this.item.generator.id);

    this.element._chart = new LineChart(this.element, "", {
      ...getElementDimensions(this.element),
      ...this.item.options,
    });
    this.element._data = data;
    this.element._chart.render(this.element._data);
  }

  createForm() {
    const generatorOptions = prepareGenerators(this.generators, []);

    const titleInput = this.createTextInput("title", "Title", this.item.title);
    const generatorInput = this.createSelect(
      "generator",
      "Generator",
      generatorOptions,
      safeValue(generatorOptions, this.item.generator.id)
    );
    const lineInput = this.createSwitch(
      "line",
      "Draw lines",
      this.item.options.line
    );
    const dotsInput = this.createSwitch(
      "dots",
      "Draw dots",
      this.item.options.dots
    );
    lineInput.querySelector("input").addEventListener("change", (event) => {
      if (!event.target.checked) {
        dotsInput.querySelector("input").checked = true;
      }
    });
    dotsInput.querySelector("input").addEventListener("change", (event) => {
      if (!event.target.checked) {
        lineInput.querySelector("input").checked = true;
      }
    });

    return createElement("form", { className: "dv-form-column" }, [
      titleInput,
      generatorInput,
      lineInput,
      dotsInput,
    ]);
  }

  saveForm(form) {
    // Save form input
    this.item.title = form.title;
    this.item.generator.id = form.generator;
    this.item.options.line = form.line === "on";
    this.item.options.dots = form.dots === "on";

    this.showAlert(!form.generator);

    // Update title
    this.span.textContent = form.title;

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
