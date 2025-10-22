import { createElement } from "../../../../../shared/modules/utils.js";
import FormHandler from "../../FormHandler.js";

export default class StaticTextHandler extends FormHandler {
  static defaults = {
    type: "StaticText",
    title: "Text",
    text: "The quick brown fox jumps over the lazy dog.",
    options: {
      style: "text-start fs-5 fw-normal fst-normal text-decoration-none",
    },
    icon: "bi bi-fonts",
    w: 2,
    h: 1,
  };

  constructor(item) {
    const template = document.querySelector("#default-static-template");
    super(template.content.cloneNode(true).children[0]);

    this.item = item;
    this.div = createElement("div");
    this.element.querySelector("span").replaceWith(this.div);
  }

  init(modal, grid) {
    this.div.textContent = this.item.text;
    this.div.className = this.item.options.style;

    this.initButtons(modal, "Text Options", grid);
  }

  createForm() {
    const styles = this.item.options.style.split(" ");

    const textInput = this.createTextArea("text", "Text", this.item.text);
    const alignInput = this.createSelect(
      "align",
      "Text alignment",
      ["start", "center", "end"],
      styles[0].replace("text-", "")
    );
    const sizeInput = this.createSelect(
      "size",
      "Font size",
      ["1", "2", "3", "4", "5", "6"],
      styles[1].replace("fs-", "")
    );
    const weightInput = this.createSelect(
      "weight",
      "Font weight",
      ["normal", "bold"],
      styles[2].replace("fw-", "")
    );
    const styleInput = this.createSelect(
      "style",
      "Font style",
      ["normal", "italic"],
      styles[3].replace("fst-", "")
    );
    const decorationInput = this.createSelect(
      "decoration",
      "Text decoration",
      ["none", "underline", "line-through"],
      styles[4].replace("text-decoration-", "")
    );
    const titleInput = this.createTextInput(
      "title",
      "Tooltip",
      this.item.title
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
    const data = Object.fromEntries(new FormData(form));
    this.item.text = data.text;
    this.item.options.style = `text-${data.align} fs-${data.size} fw-${data.weight} fst-${data.style} text-decoration-${data.decoration}`;
    this.item.title = data.title;

    this.div.textContent = this.item.text;
    this.div.className = this.item.options.style;
  }
}
