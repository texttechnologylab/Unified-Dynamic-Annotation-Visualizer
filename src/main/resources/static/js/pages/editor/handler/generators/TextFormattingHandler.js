import { createElement } from "../../../../shared/modules/utils.js";
import FormHandler from "../FormHandler.js";

export default class TextFormattingHandler extends FormHandler {
  static defaults = {
    name: "TextFormatting",
    short: "TX",
    type: "TextFormatting",
    source: "",
    settings: {
      style: "underline",
    },
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
    const styleInput = this.createSelect(
      "style",
      "Style",
      ["underline", "highlight", "bold"],
      this.generator.settings.style
    );

    return createElement("form", { className: "dv-form-column" }, [
      nameInput,
      sourceInput,
      styleInput,
    ]);
  }

  saveForm(form) {
    // Save form input
    this.generator.name = form.name;
    this.generator.source = form.source;
    this.generator.settings.style = form.style;

    // Update name
    this.spans[1].textContent = form.name;
  }
}
