import { createElement } from "../modules/utils.js";

export default class Searchselect {
  constructor({ getData, keys = ["", ""], headers = ["Results", ""] }) {
    this.getData = getData;
    this.keys = keys;
    this.headers = headers;
    this.value = "";
  }

  create(key, selected) {
    const template = document.querySelector("#searchselect-template");
    const root = template.content.cloneNode(true);

    this.reference = root.querySelector(".dv-searchselect-input");
    this.input = root.querySelector("input");
    this.dropdown = root.querySelector(".dv-dropdown");
    this.header = root.querySelectorAll(".dv-searchselect-header>span");
    this.container = root.querySelector(".dv-searchselect-container");

    this.header[0].textContent = this.headers[0];
    this.header[1].textContent = this.headers[1];
    this.input.name = key;
    this.input.value = selected;
    this.value = selected;

    this.input.addEventListener("focus", () => {
      this.input.select();
      this.autocomplete(this.input.value, 20);
    });

    this.input.addEventListener("blur", () => {
      this.input.value = this.value;
      this.hide();
      this.clear();
    });

    let timeout = null;
    this.input.addEventListener("input", () => {
      clearTimeout(timeout);
      timeout = setTimeout(() => this.autocomplete(this.input.value, 20), 300);
    });

    this.dropdown.addEventListener("mousedown", (event) => {
      event.preventDefault();
    });

    return root;
  }

  autocomplete(query, size) {
    this.getData(query, size)
      .then((items) => {
        this.clear();

        items.forEach((item) => {
          const result = this.createResult(item);
          this.container.append(result);
        });

        if (items.length === 0) {
          this.container.append(
            createElement("span", {
              className: "m-1",
              textContent: "No results found",
            }),
          );
        }

        this.show();
      })
      .catch((error) => {
        console.error(error);
      });
  }

  createResult(item) {
    const label = createElement("span", {
      className: "dv-text-truncate",
      textContent: item[this.keys[0]] || item,
    });

    const info = createElement("span", {
      textContent: item[this.keys[1]] || "",
    });

    const result = createElement(
      "div",
      {
        className: "dv-btn dv-searchselect-result",
        title: item[this.keys[0]] || "",
      },
      [label, info],
    );

    result.addEventListener("click", (event) => {
      event.preventDefault();
      this.value = item[this.keys[0]] || item;
      this.input.blur();
    });

    return result;
  }

  show() {
    this.dropdown.classList.add("show");
    this.updatePosition();
  }

  hide() {
    this.dropdown.classList.remove("show");
    this.cleanup();
  }

  clear() {
    this.container.innerHTML = "";
  }

  updatePosition() {
    const { autoUpdate, computePosition, size, hide } = FloatingUIDOM;

    // Position dropdown and update on reference move
    this.cleanup = autoUpdate(this.reference, this.dropdown, () => {
      computePosition(this.reference, this.dropdown, {
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
