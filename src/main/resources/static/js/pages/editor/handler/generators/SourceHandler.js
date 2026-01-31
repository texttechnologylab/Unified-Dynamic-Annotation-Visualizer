import { getAnnotations } from "../../../../api/annotations.api.js";
import { modal } from "../../../../shared/classes/Modal.js";
import { createElement } from "../../../../shared/modules/utils.js";
import { createGenerator, removeSource } from "../../utils/actions.js";
import FormHandler from "../FormHandler.js";

export default class SourceHandler extends FormHandler {
  static defaults = {
    uri: "",
    createsGenerators: [],
  };

  constructor(source) {
    const template = document.querySelector("#source-card-template");
    super(template.content.cloneNode(true).children[0]);

    this.source = source;
    this.tokens = {
      TextFormatting: "TF",
      CategoryNumber: "CN",
      MapCoordinates: "MC",
    };
  }

  init(defaults) {
    const buttons = this.element.querySelectorAll("button");
    const dropdown = this.element.querySelector(".dv-sliding-dropdown");
    const options = this.element.querySelector(".dv-sliding-dropdown>div");
    const body = this.element.querySelector(".dv-source-card-body");

    // Load existing generators
    for (const generator of this.source.createsGenerators) {
      const handler = createGenerator(generator, this.source.id);

      body.append(handler.element);
      handler.init();
    }
    this.source.createsGenerators = [];

    defaults.forEach((item) => {
      const option = createElement(
        "button",
        {
          className: "dv-btn",
          title: item.type,
        },
        [
          createElement("div", {
            className: "dv-generator-card-token",
            textContent: this.tokens[item.type],
          }),
          createElement("span", {
            className: "dv-text-truncate",
            textContent: item.type,
          }),
        ],
      );
      option.addEventListener("click", () => {
        const handler = createGenerator(item, this.source.id);

        body.append(handler.element);
        handler.init();

        dropdown.classList.remove("show");
      });
      options.append(option);
    });

    dropdown.addEventListener("mouseover", () => {
      dropdown.classList.add("show");
    });
    dropdown.addEventListener("mouseleave", () => {
      dropdown.classList.remove("show");
    });

    buttons[0].addEventListener("mouseover", () => {
      dropdown.classList.add("show");
    });
    buttons[0].addEventListener("mouseleave", () => {
      dropdown.classList.remove("show");
    });

    buttons[1].addEventListener("click", () => {
      const form = this.createForm();

      modal.render("Source Options", form, () =>
        this.saveForm(Object.fromEntries(new FormData(form))),
      );
    });
    buttons[2].addEventListener("click", () => {
      // Remove generator from the dom
      this.element.remove();

      // Remove generator from the state list
      removeSource(this.source);
    });
  }

  createForm() {
    const uriInput = this.createSearchbox(
      "uri",
      "Annotation type",
      getAnnotations,
      this.source.uri || "",
    );

    return createElement("form", { className: "dv-form-column" }, [uriInput]);
  }

  saveForm(form) {
    // Save form input
    this.source.uri = form.uri;
  }
}
