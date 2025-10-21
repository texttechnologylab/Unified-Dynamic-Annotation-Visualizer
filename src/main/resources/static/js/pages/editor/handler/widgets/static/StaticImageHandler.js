import { createElement } from "../../../../../shared/modules/utils.js";
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
    const template = document.querySelector("#static-image-template");
    super(template.content.cloneNode(true).children[0]);

    this.item = item;
    this.img = this.element.querySelector("img");
  }

  init(modal, grid) {
    this.img.src = this.item.src;

    this.initButtons(modal, "Image Options", grid);
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
    // Save form input
    const data = Object.fromEntries(new FormData(form));
    this.item.src = data.src;
    this.item.title = data.title;

    this.img.src = this.item.src;
  }
}
