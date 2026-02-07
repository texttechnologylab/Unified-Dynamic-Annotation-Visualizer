import { createElement } from "../../../../../shared/modules/utils.js";
import state from "../../../utils/state.js";
import FormHandler from "../../FormHandler.js";

export default class StaticTextHandler extends FormHandler {
  static defaults = {
    type: "StaticText",
    title: "Text",
    src: "Empty Text",
    options: {
      style: "text-start fs-5 fw-normal fst-normal text-decoration-none",
    },
    icon: "bi bi-type",
    w: 4,
    h: 2,
  };

  constructor(item) {
    const template = document.querySelector("#default-static-template");
    super(template.content.cloneNode(true).children[0]);

    this.item = item;
    this.div = createElement("div");
    this.element.querySelector("span").replaceWith(this.div);
  }

  init() {
    this.div.textContent = this.item.src;
    this.div.className = this.item.options.style;

    this.initButtons("Text Options", () => {
      state.grid.removeWidget(this.item.el);
    });
  }

  createForm() {
    const styles = this.item.options.style.split(" ");

    const textInput = this.createTextArea("text", "Text", this.item.src);
    const alignInput = this.createSelect(
      "align",
      "Text alignment",
      ["start", "center", "end"],
      styles[0].replace("text-", ""),
    );
    const sizeInput = this.createSelect(
      "size",
      "Font size",
      ["1", "2", "3", "4", "5", "6"],
      styles[1].replace("fs-", ""),
    );
    const weightInput = this.createSelect(
      "weight",
      "Font weight",
      ["normal", "bold"],
      styles[2].replace("fw-", ""),
    );
    const styleInput = this.createSelect(
      "style",
      "Font style",
      ["normal", "italic"],
      styles[3].replace("fst-", ""),
    );
    const decorationInput = this.createSelect(
      "decoration",
      "Text decoration",
      ["none", "underline", "line-through"],
      styles[4].replace("text-decoration-", ""),
    );
    const titleInput = this.createTextInput(
      "title",
      "Tooltip",
      this.item.title,
    );

    return createElement("form", { className: "dv-form-column" }, [
      textInput,
      alignInput,
      sizeInput,
      weightInput,
      styleInput,
      decorationInput,
      titleInput,
    ]);
  }

  saveForm(form) {
    // Save form input
    this.item.src = form.text;
    this.item.options.style = `text-${form.align} fs-${form.size} fw-${form.weight} fst-${form.style} text-decoration-${form.decoration}`;
    this.item.title = form.title;

    this.div.textContent = this.item.src;
    this.div.className = this.item.options.style;
  }
}
