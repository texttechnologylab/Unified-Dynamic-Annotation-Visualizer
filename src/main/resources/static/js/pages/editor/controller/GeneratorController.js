import FormBuilder from "../../../shared/classes/FormBuilder.js";
import { createTemplateElement } from "../../../shared/modules/utils.js";
import {
  getGeneratorOptions,
  removeGenerator,
} from "../utils/editorActions.js";
import configs from "../configs/configs.js";
import state from "../utils/editorState.js";

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

    const formConfig = {
      ...Generator.formConfig,
      extends: {
        type: "multiselect",
        label: "Extends (optional)",
        options: () =>
          getGeneratorOptions(this.item.type).filter(
            (option) => option.value !== this.item.id,
          ),
      },
    };

    const builder = new FormBuilder(
      state.modal,
      "Generator Options",
      formConfig,
    );
    const buttons = this.root.querySelectorAll("button");

    buttons[0].addEventListener("click", () => {
      const { name, settings, extends: ext } = this.item;

      builder.buildForm(
        { name, settings, extends: ext },
        ({ name, settings, extends: ext }) => {
          this.setName(name);
          this.item.settings = settings;
          this.item.extends = ext;
        },
      );
    });
    buttons[1].addEventListener("click", () => {
      // Remove generator from the dom
      this.root.remove();

      // Remove generator from the state list
      removeGenerator(this.item);
    });
  }
}
