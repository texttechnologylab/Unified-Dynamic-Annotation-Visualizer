import {
  createElement,
  getElementDimensions,
} from "../../../../../shared/modules/utils.js";
import FormHandler from "../../FormHandler.js";
import Network2D from "../../../../view/widgets/networks/Network2D.js";

export default class Network2DHandler extends FormHandler {
  static defaults = {
    type: "Network2D",
    title: "Network 2D",
    generator: { id: "" },
    options: {},
    icon: "bi bi-diagram-3",
    w: 4,
    h: 3,
  };

  constructor(item) {
    const template = document.querySelector("#default-chart-template");
    super(template.content.cloneNode(true).children[0]);

    this.item = item;
    this.span = this.element.querySelector("span");
  }

  init(grid) {
    this.span.textContent = this.item.title;
    this.initButtons("Network 2D Options", () =>
      grid.removeWidget(this.item.el)
    );

    this.showAlert(!this.item.generator.id);

    this.element._chart = new Network2D(this.element, "", {
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
  }
}

const data = {
  nodes: [
    {
      id: 1,
      name: "A",
      color: "#00618f",
    },
    {
      id: 2,
      name: "B",
      color: "#00618f",
    },
    {
      id: 3,
      name: "C",
      color: "#00618f",
    },
  ],
  links: [
    {
      source: 1,
      target: 2,
      color: "#9eadbd",
    },
    {
      source: 1,
      target: 3,
      color: "#9eadbd",
    },
  ],
};
