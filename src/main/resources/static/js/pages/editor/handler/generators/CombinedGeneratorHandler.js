import { createElement } from "../../../../shared/modules/utils.js";
import { removeGenerator, safeValue } from "../../utils/actions.js";
import state from "../../utils/state.js";
import FormHandler from "../FormHandler.js";

export default class CombinedGeneratorHandler extends FormHandler {
  static token = "CG";
  static description = "Description of the CombinedGenerator generator.";
  static defaults = {
    name: "CombinedGenerator",
    type: "CombinedGenerator",
    source: [],
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
      CombinedGeneratorHandler.token;
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
    const generatorOptions = state.generators
      .filter((g) => g.id !== this.generator.id)
      .map((g) => {
        return { label: g.name, value: g.id };
      });

    const nameInput = this.createTextInput("name", "Name", this.generator.name);
    const sourceInput = this.createMultiselect(
      "source",
      "Source generators",
      generatorOptions,
      this.generator.source
    );

    return createElement("form", { className: "dv-form-column" }, [
      nameInput,
      sourceInput,
    ]);
  }

  saveForm(form) {
    // Save form input
    this.generator.name = form.get("name");
    this.generator.source = form.getAll("source");

    // Update name
    this.body.textContent = form.get("name");
  }
}
