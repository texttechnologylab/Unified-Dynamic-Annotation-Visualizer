import {
  createElement,
  getElementDimensions,
} from "../../../../../shared/modules/utils.js";
import FormHandler from "../../FormHandler.js";
import HighlightText from "../../../../view/widgets/text/HighlightText.js";

export default class HighlightTextHandler extends FormHandler {
  static defaults = {
    type: "HighlightText",
    title: "Highlight Text",
    generator: { id: "" },
    options: {},
    icon: "bi bi-card-text",
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
    this.initButtons(modal, "Highlight Text Options", grid);

    this.element._chart = new HighlightText(this.element, "", {
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

const data = { spans: [], datasets: [] };
