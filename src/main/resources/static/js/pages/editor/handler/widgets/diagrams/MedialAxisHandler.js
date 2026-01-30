import { createElement } from "../../../../../shared/modules/utils.js";
import FormHandler from "../../FormHandler.js";
import { prepareGenerators, safeValue } from "../../../utils/actions.js";
import state from "../../../utils/state.js";
import MedialAxis from "../../../../view/widgets/dynamic/MedialAxis.js";

export default class MedialAxisHandler extends FormHandler {
  static defaults = {
    type: "MedialAxis",
    title: "Medial Axis",
    generator: { id: "" },
    options: {},
    icon: "bi bi-slash-square",
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
    this.initButtons("Options", () => {
      state.grid.removeWidget(this.item.el);
    });

    this.showAlert(!this.item.generator.id);

    this.element._chart = new MedialAxis(this.element, "", this.item.options);
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
  },
  {
    x: 12,
    y: 32,
  },
  {
    x: 23,
    y: 23,
  },
];
