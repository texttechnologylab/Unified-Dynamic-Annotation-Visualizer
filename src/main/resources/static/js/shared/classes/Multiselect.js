import { createElement } from "../modules/utils.js";

export default class Multiselect {
  constructor(options) {
    this.options = options;
    this.selection = [];
  }

  commitSelection() {
    this.input.value = JSON.stringify(this.selection);
  }

  create(key, selected) {
    const template = document.querySelector("#multiselect-template");
    const root = template.content.cloneNode(true);

    this.input = root.querySelector("input");
    this.trigger = root.querySelector(".dv-multiselect");
    this.pills = root.querySelector(".dv-multiselect-pills");
    this.dropdown = root.querySelector(".dv-dropdown");

    this.input.name = key;

    for (const option of this.options) {
      if (selected.includes(option.value)) {
        const node = this.createPill(option.label, option.value);
        this.pills.append(node);

        this.selection.push(option.value);
      } else {
        const node = this.createOption(option.label, option.value);
        this.dropdown.append(node);
      }
    }

    this.trigger.addEventListener("focus", () => this.show());
    this.trigger.addEventListener("blur", () => this.hide());
    this.dropdown.addEventListener("mousedown", (event) =>
      event.preventDefault(),
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
      this.pills.append(node);

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
      this.dropdown.append(node);

      pill.remove();

      const index = this.selection.indexOf(value);
      if (index > -1) this.selection.splice(index, 1);
      this.commitSelection();
    });

    return pill;
  }

  show() {
    this.dropdown.classList.add("show");
    this.updatePosition();
  }

  hide() {
    this.dropdown.classList.remove("show");
    this.cleanup();
  }

  updatePosition() {
    const { autoUpdate, computePosition, size, hide } = FloatingUIDOM;

    // Position dropdown and update on reference move
    this.cleanup = autoUpdate(this.trigger, this.dropdown, () => {
      computePosition(this.trigger, this.dropdown, {
        middleware: [
          size({
            apply({ rects, elements }) {
              elements.floating.style.width = rects.reference.width + "px";
            },
          }),
          hide(),
        ],
      }).then(({ x, y, middlewareData }) => {
        this.dropdown.style.visibility = middlewareData.hide.referenceHidden
          ? "hidden"
          : "visible";
        this.dropdown.style.left = x + "px";
        this.dropdown.style.top = y + "px";
      });
    });
  }
}
