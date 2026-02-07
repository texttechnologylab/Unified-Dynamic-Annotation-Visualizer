import { modal } from "../../../shared/classes/Modal.js";
import Multiselect from "../../../shared/classes/Multiselect.js";
import Searchbox from "../../../shared/classes/Searchbox.js";
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
        this.saveForm(Object.fromEntries(new FormData(form))),
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
      min,
      max,
      value,
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
    if (!selected) {
      options.unshift({ label: "Choose...", value: "", disabled: true });
    }

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
          disabled: opt.disabled,
        });
      }),
    );

    const label = createElement("label", { className: "d-flex flex-column" }, [
      createElement("span", { textContent: title }),
      select,
    ]);

    return label;
  }

  createMultiselect(key, title, options, selected) {
    const multiselect = new Multiselect(options);
    const select = multiselect.create(key, selected);

    const label = createElement("label", { className: "d-flex flex-column" }, [
      createElement("span", { textContent: title }),
      select,
    ]);

    return label;
  }

  createSearchbox(key, title, getData, selected) {
    const searchbox = new Searchbox(getData);
    const input = searchbox.create(key, selected);

    const label = createElement("label", { className: "dv-label" }, [
      createElement("span", { textContent: title }),
      input,
    ]);

    return label;
  }

  createRangeSlider(key, title, value, min, max) {
    const input = createElement("input", {
      name: key,
      type: "range",
      min,
      max,
      value,
      className: "dv-slider",
    });
    const output = createElement("output", { textContent: value });
    input.addEventListener(
      "input",
      (event) => (output.textContent = event.target.value),
    );
    const label = createElement("label", { className: "d-flex flex-column" }, [
      createElement("span", { textContent: title }),
      input,
      output,
    ]);

    return label;
  }

  createSwitch(key, title, on) {
    const input = createElement("input", {
      name: key,
      type: "checkbox",
      checked: on,
    });
    const label = createElement("label", { className: "dv-switch" }, [
      input,
      createElement("span", { className: "dv-switch-track" }),
      createElement("span", { textContent: title }),
    ]);

    return label;
  }
}
