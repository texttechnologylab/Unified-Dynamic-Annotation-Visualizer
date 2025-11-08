import { createElement } from "../modules/utils.js";

export default class Multiselect {
  constructor(options) {
    this.options = options;
    this.selection = [];
    this.dom = {};
  }

  commitSelection() {
    this.dom.input.value = JSON.stringify(this.selection);
  }

  create(key, selected) {
    const template = document.querySelector("#multiselect-template");
    const root = template.content.cloneNode(true);

    this.dom.input = root.querySelector("input");
    this.dom.trigger = root.querySelector(".dv-multiselect");
    this.dom.pills = root.querySelector(".dv-multiselect-pills");
    this.dom.dropdown = root.querySelector(".dv-dropdown");

    this.dom.input.name = key;

    for (const option of this.options) {
      if (selected.includes(option.value)) {
        const node = this.createPill(option.label, option.value);
        this.dom.pills.append(node);

        this.selection.push(option.value);
      } else {
        const node = this.createOption(option.label, option.value);
        this.dom.dropdown.append(node);
      }
    }

    this.dom.trigger.addEventListener("focus", () => this.show());
    this.dom.trigger.addEventListener("blur", () => this.hide());
    this.dom.dropdown.addEventListener("mousedown", (event) =>
      event.preventDefault()
    );

    this.commitSelection();

    return root;
  }

  createOption(label, value) {
    const option = createElement("div", {
      textContent: label,
      className: "dv-btn dv-multiselect-option",
    });

    option.addEventListener("click", () => {
      const node = this.createPill(label, value);
      this.dom.pills.append(node);

      option.remove();

      this.selection.push(value);
      this.commitSelection();
    });

    return option;
  }

  createPill(label, value) {
    const icon = createElement("i", { className: "bi-x" });
    const pill = createElement("div", { classList: "dv-multiselect-pill" }, [
      createElement("span", {
        textContent: label,
        className: "dv-text-truncate",
      }),
      icon,
    ]);

    icon.addEventListener("click", () => {
      const node = this.createOption(label, value);
      this.dom.dropdown.append(node);

      pill.remove();

      const index = this.selection.indexOf(value);
      if (index > -1) this.selection.splice(index, 1);
      this.commitSelection();
    });

    return pill;
  }

  show() {
    this.dom.dropdown.classList.add("show");
  }

  hide() {
    this.dom.dropdown.classList.remove("show");
  }
}
