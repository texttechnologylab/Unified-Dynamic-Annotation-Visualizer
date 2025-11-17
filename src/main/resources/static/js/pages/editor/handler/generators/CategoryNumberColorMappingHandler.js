import { createElement } from "../../../../shared/modules/utils.js";
import { removeGenerator } from "../../utils/actions.js";
import state from "../../utils/state.js";
import FormHandler from "../FormHandler.js";

export default class CategoryNumberColorMappingHandler extends FormHandler {
  static defaults = {
    name: "New CategoryNumberColorMapping",
    type: "CategoryNumberColorMapping",
    settings: {},
    token: "CO",
    extends: [],
  };

  constructor(generator) {
    const template = document.querySelector("#generator-card-template");
    super(template.content.cloneNode(true).children[0]);

    this.generator = generator;
    this.name = this.element.querySelector(".dv-generator-card-name");
  }

  init() {
    this.element.querySelector(".dv-generator-card-token").textContent =
      this.generator.token;
    this.element.querySelector(".dv-generator-card-type").textContent =
      this.generator.type;
    this.name.textContent = this.generator.name;

    this.initButtons("Generator Options", () => {
      // Remove generator from the dom
      this.element.remove();

      // Remove generator from the state list
      removeGenerator(this.generator);
    });
  }

  createForm() {
    const options = state.generators
      .filter(
        (gen) =>
          gen.type === this.generator.type &&
          gen.id !== this.generator.id &&
          !gen.extends.includes(this.generator.id)
      )
      .map((gen) => {
        return { label: gen.name, value: gen.id };
      });

    const nameInput = this.createTextInput("name", "Name", this.generator.name);
    const extendsInput = this.createMultiselect(
      "extends",
      "Extends (optional)",
      options,
      this.generator.extends
    );

    return createElement("form", { className: "dv-form-column" }, [
      nameInput,
      extendsInput,
    ]);
  }

  saveForm(form) {
    // Save form input
    this.generator.name = form.name;
    this.generator.extends = JSON.parse(form.extends);

    // Update name
    this.name.textContent = form.name;
  }
}
