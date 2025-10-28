import { createElement } from "../../../../shared/modules/utils.js";
import FormHandler from "../FormHandler.js";

export default class TextFormattingHandler extends FormHandler {
  static token = "TX";
  static description = "Description of the TextFormatting generator.";
  static defaults = {
    name: "TextFormatting",
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
    this.body = this.element.querySelector(".dv-generator-body");
  }

  init(generators) {
    this.element.querySelector(".dv-generator-token").textContent =
      TextFormattingHandler.token;
    this.element.querySelector(".dv-generator-type").textContent =
      this.generator.type;
    this.body.textContent = this.generator.name;

    this.initButtons("Generator Options", () => {
      // Remove generator from the dom
      this.element.remove();

      // Remove generator from the state list
      generators.splice(generators.indexOf(this.generator), 1);
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
    this.body.textContent = form.name;
  }
}
