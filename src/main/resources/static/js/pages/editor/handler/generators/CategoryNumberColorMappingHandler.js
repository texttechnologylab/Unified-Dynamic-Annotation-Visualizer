import { createElement } from "../../../../shared/modules/utils.js";
import { removeGenerator } from "../../utils/actions.js";
import FormHandler from "../FormHandler.js";

export default class CategoryNumberColorMappingHandler extends FormHandler {
  static token = "CO";
  static description =
    "Description of the CategoryNumberColorMapping generator.";
  static defaults = {
    name: "CategoryNumberColorMapping",
    type: "CategoryNumberColorMapping",
    source: "",
    settings: {},
  };

  constructor(generator) {
    const template = document.querySelector("#added-generator-template");
    super(template.content.cloneNode(true).children[0]);

    this.generator = generator;
    this.body = this.element.querySelector(".dv-generator-body");
  }

  init() {
    this.element.querySelector(".dv-generator-token").textContent =
      CategoryNumberColorMappingHandler.token;
    this.element.querySelector(".dv-generator-type").textContent =
      this.generator.type;
    this.body.textContent = this.generator.name;

    this.initButtons("Generator Options", () => {
      // Remove generator from the dom
      this.element.remove();

      // Remove generator from the state list
      removeGenerator(this.generator);
    });
  }

  createForm() {
    const nameInput = this.createTextInput("name", "Name", this.generator.name);
    const sourceInput = this.createSearchbox(
      "source",
      "Data source",
      "/api/annotations",
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
    this.body.textContent = form.name;
  }
}
