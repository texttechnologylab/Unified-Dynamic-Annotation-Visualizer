import { createElement } from "../../../../../shared/modules/utils.js";
import FormHandler from "../../FormHandler.js";
import Map2D from "../../../../view/widgets/maps/Map2D.js";
import { prepareGenerators, safeValue } from "../../../utils/actions.js";
import state from "../../../utils/state.js";

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

  init() {
    this.span.textContent = this.item.title;
    this.initButtons("Map 2D Options", () => {
      state.grid.removeWidget(this.item.el);
    });

    this.showAlert(!this.item.generator.id);

    this.element._chart = new Map2D(this.element, "", this.item.options);
    this.element._data = data;
    this.element._chart.render(this.element._data);
  }

  createForm() {
    const generatorOptions = prepareGenerators([]);

    const titleInput = this.createTextInput("title", "Title", this.item.title);
    const generatorInput = this.createSelect(
      "generator",
      "Generator",
      generatorOptions,
      safeValue(generatorOptions, this.item.generator.id)
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
