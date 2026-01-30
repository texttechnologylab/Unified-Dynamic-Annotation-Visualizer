import { createElement } from "../../../../../shared/modules/utils.js";
import FormHandler from "../../FormHandler.js";
import { prepareGenerators, safeValue } from "../../../utils/actions.js";
import state from "../../../utils/state.js";
import VoronoiDiagram from "../../../../view/widgets/diagrams/VoronoiDiagram.js";

export default class VoronoiDiagramHandler extends FormHandler {
  static defaults = {
    type: "VoronoiDiagram",
    title: "Voronoi Diagram",
    generator: { id: "" },
    options: {},
    icon: "bi bi-columns",
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
    this.initButtons("Voronoi Diagram Options", () => {
      state.grid.removeWidget(this.item.el);
    });

    this.showAlert(!this.item.generator.id);

    this.element._chart = new VoronoiDiagram(
      this.element,
      "",
      this.item.options,
    );
    this.element._data = data;
    this.element._chart.render(this.element._data);
  }

  createForm() {
    const generatorOptions = prepareGenerators(["MapCoordinates"]);

    const titleInput = this.createTextInput("title", "Title", this.item.title);
    const generatorInput = this.createSelect(
      "generator",
      "Generator",
      generatorOptions,
      safeValue(generatorOptions, this.item.generator.id),
    );

    return createElement("form", { className: "dv-form-column" }, [
      titleInput,
      generatorInput,
    ]);
  }

  saveForm(form) {
    // Save form input
    this.item.title = form.title;
    this.item.generator.id = form.generator;

    this.showAlert(!form.generator);

    // Update title
    this.span.textContent = form.title;

    // Update chart
    this.element._chart.render(this.element._data);
  }
}

const data = [
  {
    x: 10,
    y: 10,
    cell: "#00618f",
    fill: "#00618f",
    stroke: "#555555",
    label: "Cell 1",
    abs: 0.5,
  },
  {
    x: 12,
    y: 32,
    cell: "#3a4856",
    fill: "#3a4856",
    stroke: "#555555",
    label: "Cell 7",
    abs: 0.2,
  },
  {
    x: 23,
    y: 23,
    cell: "#9eadbd",
    fill: "#9eadbd",
    stroke: "#555555",
    label: "Cell 10",
    abs: 0.1,
  },
];
