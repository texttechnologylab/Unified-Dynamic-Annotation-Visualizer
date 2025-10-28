import { modal } from "../../../shared/classes/Modal.js";
import { createElement } from "../../../shared/modules/utils.js";

export default class FormHandler {
  static defaults = {};

  constructor(element) {
    this.element = element;
    this.alert = this.element.querySelector(".dv-chart-alert");
  }

  init() {
    throw new Error("Method init() not implemented.");
  }

  createForm() {
    throw new Error("Method createForm() not implemented.");
  }

  saveForm() {
    throw new Error("Method saveForm() not implemented.");
  }

  showAlert(show) {
    const classes = this.alert.classList;
    show ? classes.add("show") : classes.remove("show");
  }

  initButtons(modalTitle, remove) {
    const buttons = this.element.querySelectorAll("button");

    buttons[0].addEventListener("click", () => {
      const form = this.createForm();

      modal.render(modalTitle, form, () =>
        this.saveForm(Object.fromEntries(new FormData(form)))
      );
    });
    buttons[1].addEventListener("click", () => remove());
  }

  createTextInput(key, title, value) {
    const input = createElement("input", {
      name: key,
      type: "text",
      value,
      className: "dv-text-input",
    });
    const label = createElement("label", { className: "d-flex flex-column" }, [
      createElement("span", { textContent: title }),
      input,
    ]);

    return label;
  }

  createNumberInput(key, title, value, min, max) {
    const input = createElement("input", {
      name: key,
      type: "number",
      value,
      min,
      max,
      className: "dv-text-input",
    });
    const label = createElement("label", { className: "d-flex flex-column" }, [
      createElement("span", { textContent: title }),
      input,
    ]);

    return label;
  }

  createTextArea(key, title, value) {
    const area = createElement("textarea", {
      name: key,
      value,
      rows: 3,
      className: "dv-text-input dv-resize-none",
    });
    const label = createElement("label", { className: "d-flex flex-column" }, [
      createElement("span", { textContent: title }),
      area,
    ]);

    return label;
  }

  createSelect(key, title, options, selected) {
    const select = createElement(
      "select",
      {
        name: key,
        className: "dv-select",
      },
      options.map((opt) => {
        const textContent = typeof opt === "string" ? opt : opt.label;
        const value = typeof opt === "string" ? opt : opt.value;

        return createElement("option", {
          textContent,
          value,
          selected: value === selected,
        });
      })
    );

    const label = createElement("label", { className: "d-flex flex-column" }, [
      createElement("span", { textContent: title }),
      select,
    ]);

    return label;
  }
}
