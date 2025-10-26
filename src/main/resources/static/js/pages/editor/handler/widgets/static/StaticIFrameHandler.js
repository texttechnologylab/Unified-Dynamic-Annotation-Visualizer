import { createElement } from "../../../../../shared/modules/utils.js";
import FormHandler from "../../FormHandler.js";

export default class StaticIFrameHandler extends FormHandler {
  static defaults = {
    type: "StaticIFrame",
    title: "Inline Frame",
    src: "",
    options: {},
    icon: "bi bi-window",
    w: 4,
    h: 3,
  };

  constructor(item) {
    const template = document.querySelector("#default-static-template");
    super(template.content.cloneNode(true).children[0]);

    this.placeholder = "https://placehold.co/600x400?text=Inline+Frame";

    this.item = item;
    this.iframe = createElement("iframe");
    this.element.querySelector("span").replaceWith(this.iframe);
  }

  init(grid) {
    this.iframe.src = this.item.src || this.placeholder;
    this.element.style.overflow = "hidden";
    this.iframe.style.pointerEvents = "none";
    this.iframe.setAttribute("width", "100%");
    this.iframe.setAttribute("height", "100%");

    this.initButtons("Inline Frame Options", () =>
      grid.removeWidget(this.item.el)
    );
  }

  createForm() {
    const srcInput = this.createTextInput("src", "URL", this.item.src);
    const titleInput = this.createTextInput(
      "title",
      "Tooltip",
      this.item.title
    );

    return createElement("form", { className: "dv-form-column" }, [
      srcInput,
      titleInput,
    ]);
  }

  saveForm(form) {
    // Save form input
    this.item.src = form.src;
    this.item.title = form.title;

    this.iframe.src = this.item.src || this.placeholder;
  }
}
