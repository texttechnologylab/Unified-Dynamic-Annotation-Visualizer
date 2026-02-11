import FormBuilder from "../../../shared/classes/FormBuilder.js";
import { createTemplateElement } from "../../../shared/modules/utils.js";
import { removeGenerator } from "../utils/actions.js";
import configs from "../utils/configs.js";
import state from "../utils/state.js";

export default class GeneratorController {
  constructor(item) {
    this.root = createTemplateElement("#generator-card-template");
    this.span = this.root.querySelector(".dv-generator-card-name");
    this.item = item;
  }

  setName(name) {
    this.item.name = name;
    this.span.textContent = name;
  }

  init() {
    this.setName(this.item.name);

    const Generator = configs[this.item.type];

    this.root.querySelector(".dv-generator-card-token").textContent =
      Generator.token;
    this.root.querySelector(".dv-generator-card-type").textContent =
      this.item.type;

    const builder = new FormBuilder(
      state.modal,
      "Generator Options",
      Generator.formConfig,
    );
    const buttons = this.root.querySelectorAll("button");

    buttons[0].addEventListener("click", () => {
      const { name, settings } = this.item;

      builder.buildForm({ name, settings }, ({ name, settings }) => {
        this.setName(name);
        this.item.settings = settings;
      });
    });
    buttons[1].addEventListener("click", () => {
      // Remove generator from the dom
      this.root.remove();

      // Remove generator from the state list
      removeGenerator(this.item);
    });
  }
}
