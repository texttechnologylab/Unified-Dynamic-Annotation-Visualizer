import { createElement } from "../../../../shared/modules/utils.js";
import FormHandler from "../FormHandler.js";

export default class CategoryNumberMappingHandler extends FormHandler {
  static defaults = {
    name: "CategoryNumberMapping",
    short: "NU",
    type: "CategoryNumberMapping",
    source: "",
    settings: {},
  };

  constructor(generator) {
    const template = document.querySelector("#added-generator-template");
    super(template.content.cloneNode(true).children[0]);

    this.generator = generator;
    this.spans = this.element.querySelectorAll("span");
  }

  init(modal, generators) {
    this.spans[0].textContent = this.generator.short;
    this.spans[1].textContent = this.generator.name;

    this.initButtons(modal, "Generator Options", () => {
      this.element.remove();
      generators.filter((item) => item.id !== this.generator.id);
    });
  }

  createForm() {
    const nameInput = this.createTextInput("name", "Name", this.generator.name);
    const sourceInput = this.createTextInput(
      "source",
      "Data source",
      this.generator.source
    );

    return createElement("form", { className: "dv-form-column" }, [
      nameInput,
      sourceInput,
    ]);
  }

  saveForm(form) {
    // Save form input
    this.generator.name = form.name;
    this.generator.source = form.source;

    // Update name
    this.spans[1].textContent = form.name;
  }
}
