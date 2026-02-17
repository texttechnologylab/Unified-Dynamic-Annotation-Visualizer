import FormBuilder from "../../../shared/classes/FormBuilder.js";
import {
  createElement,
  createTemplateElement,
} from "../../../shared/modules/utils.js";
import { createGenerator, removeSource } from "../utils/editorActions.js";
import Source from "../configs/Source.js";
import configs from "../configs/configs.js";
import state from "../utils/editorState.js";

export default class SourceController {
  constructor(item) {
    this.root = createTemplateElement("#source-card-template");
    this.item = item;
  }

  init() {
    const buttons = this.root.querySelectorAll("button");
    const options = this.root.querySelector(".dv-dropdown-menu");
    const body = this.root.querySelector(".dv-source-card-body");

    const builder = new FormBuilder(
      state.modal,
      "Source Options",
      Source.formConfig,
    );

    // Load existing generators
    for (const config of this.item.createsGenerators) {
      this.appendGenerator(body, config);
    }
    this.item.createsGenerators = [];

    // Append available generator options
    Object.values(configs).forEach((Generator) => {
      const config = Generator.defaultConfig;

      const option = createElement(
        "button",
        {
          className: "dv-btn dv-generator-option",
          title: config.type,
        },
        [
          createElement("div", {
            className: "dv-generator-card-token",
            textContent: Generator.token,
          }),
          createElement("span", {
            className: "dv-text-truncate",
            textContent: config.type,
          }),
        ],
      );

      option.addEventListener("click", () => {
        this.appendGenerator(body, config);
      });

      options.append(option);
    });

    // Initialize buttons
    buttons[1].addEventListener("click", () => {
      const { uri } = this.item;

      builder.buildForm({ uri }, ({ uri }) => {
        this.item.uri = uri;
      });
    });

    buttons[2].addEventListener("click", () => {
      // Remove generator from the dom
      this.root.remove();

      // Remove generator from the state list
      removeSource(this.item);
    });
  }

  appendGenerator(container, config) {
    const generator = createGenerator(config, this.item.id);

    container.append(generator.root);
    generator.init();
  }
}
