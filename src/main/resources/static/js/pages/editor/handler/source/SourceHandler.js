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
  }

  init(defaults) {
    const buttons = this.element.querySelectorAll("button");
    const dropdown = this.element.querySelector(".dv-dropdown-menu");
    const body = this.element.querySelector(".dv-source-card-body");

    // Load existing generators
    for (const generator of this.source.createsGenerators) {
      const handler = createGenerator(generator, this.source.id);

      body.append(handler.element);
      handler.init();
    }
    this.source.createsGenerators = [];

    defaults.forEach((item) => {
      const btn = createElement("button", {
        className: "dv-btn",
        textContent: item.type,
      });
      btn.addEventListener("click", () => {
        const handler = createGenerator(item, this.source.id);

        body.append(handler.element);
        handler.init();
      });
      dropdown.append(btn);
    });

    buttons[1].addEventListener("click", () => {
      const form = this.createForm();

      modal.render("Source Options", form, () =>
        this.saveForm(Object.fromEntries(new FormData(form)))
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
      "/api/annotations",
      this.source.uri || ""
    );

    return createElement("form", { className: "dv-form-column" }, [uriInput]);
  }

  saveForm(form) {
    // Save form input
    this.source.uri = form.uri;
  }
}
