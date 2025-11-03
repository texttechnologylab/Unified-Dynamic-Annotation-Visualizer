import { createElement } from "../../../../../shared/modules/utils.js";
import state from "../../../utils/state.js";
import FormHandler from "../../FormHandler.js";

export default class StaticImageHandler extends FormHandler {
  static defaults = {
    type: "StaticImage",
    title: "Image",
    src: "https://placehold.co/600x400?text=Image",
    options: {},
    icon: "bi bi-image",
    w: 1,
    h: 1,
  };

  constructor(item) {
    const template = document.querySelector("#default-static-template");
    super(template.content.cloneNode(true).children[0]);

    this.item = item;
    this.img = createElement("img");
    this.element.querySelector("span").replaceWith(this.img);
  }

  init() {
    this.img.src = this.item.src;
    this.img.setAttribute("width", "100%");
    this.img.setAttribute("height", "100%");

    this.initButtons("Image Options", () => {
      state.grid.removeWidget(this.item.el);
    });
  }

  createForm() {
    const srcInput = this.createTextInput("src", "Image-URL", this.item.src);
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
    form = Object.fromEntries(form);

    // Save form input
    this.item.src = form.src;
    this.item.title = form.title;

    this.img.src = this.item.src;
  }
}
