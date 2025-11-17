import { createElement } from "../modules/utils.js";

export default class Searchbox {
  constructor(endpoint, keys = ["annotation", "rowCount"]) {
    this.endpoint = endpoint;
    this.keys = keys;
    this.value = "";
    this.dom = {};
  }

  create(key, selected, header = ["Annotation", "#"]) {
    const template = document.querySelector("#searchbox-template");
    const root = template.content.cloneNode(true);

    this.dom.input = root.querySelector("input");
    this.dom.dropdown = root.querySelector(".dv-dropdown");
    this.dom.header = root.querySelectorAll(".dv-searchbox-header>span");
    this.dom.container = root.querySelector(".dv-searchbox-container");

    this.dom.header[0].textContent = header[0];
    this.dom.header[1].textContent = header[1];
    this.dom.input.name = key;
    this.dom.input.value = selected;
    this.value = selected;

    this.dom.input.addEventListener("focus", () => {
      this.dom.input.select();
      this.autocomplete(this.dom.input.value, 20);
    });

    this.dom.input.addEventListener("blur", () => {
      this.dom.input.value = this.value;
      this.hide();
      this.clear();
    });

    let timeout = null;
    this.dom.input.addEventListener("input", () => {
      clearTimeout(timeout);
      timeout = setTimeout(
        () => this.autocomplete(this.dom.input.value, 20),
        300
      );
    });

    this.dom.dropdown.addEventListener("mousedown", (event) => {
      event.preventDefault();
    });

    return root;
  }

  autocomplete(query, size) {
    fetch(`${this.endpoint}?q=${query}&size=${size}`)
      .then((response) => response.json())
      .then((items) => {
        this.clear();

        items.forEach((item) => {
          const result = this.createResult(item);
          this.dom.container.append(result);
        });

        this.show();
      })
      .catch((error) => {
        console.error(error);
      });
  }

  createResult(item) {
    const label = createElement("span", {
      className: "dv-text-truncate",
      textContent: item[this.keys[0]],
    });
    label.addEventListener("click", (event) => {
      event.preventDefault();
      this.value = item[this.keys[0]];
      this.dom.input.blur();
    });

    const info = createElement("span", {
      textContent: item[this.keys[1]],
    });

    const result = createElement(
      "div",
      {
        className: "dv-btn dv-searchbox-result",
        title: item[this.keys[0]],
      },
      [label, info]
    );

    return result;
  }

  show() {
    this.dom.dropdown.classList.add("show");
  }

  hide() {
    this.dom.dropdown.classList.remove("show");
  }

  clear() {
    this.dom.container.innerHTML = "";
  }
}
