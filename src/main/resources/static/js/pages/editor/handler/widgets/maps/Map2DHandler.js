import {
  createElement,
  getElementDimensions,
} from "../../../../../shared/modules/utils.js";
import FormHandler from "../../FormHandler.js";
import Map2D from "../../../../view/widgets/maps/Map2D.js";

export default class Map2DHandler extends FormHandler {
  static defaults = {
    type: "Map2D",
    title: "Map 2D",
    generator: { id: "" },
    options: {},
    icon: "bi bi-map",
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
    this.initButtons(modal, "Map 2D Options", grid);

    this.element._chart = new Map2D(this.element, "", {
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
    const data = Object.fromEntries(new FormData(form));
    this.item.title = data.title;
    this.item.generator.id = data.generator;

    // Update title
    this.span.textContent = data.title;
  }
}

const data = [
  {
    type: "LineString",
    label: "Flight 1",
    color: "#00618f",
    coordinates: [
      [100, 60],
      [-60, -30],
    ],
  },
];
