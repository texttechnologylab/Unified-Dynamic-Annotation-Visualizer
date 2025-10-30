import { createElement } from "../../../../../shared/modules/utils.js";
import FormHandler from "../../FormHandler.js";

export default class StaticVideoHandler extends FormHandler {
  static defaults = {
    type: "StaticVideo",
    title: "Video",
    src: "",
    options: {
      controls: true,
      autoplay: false,
    },
    icon: "bi bi-film",
    w: 3,
    h: 2,
  };

  constructor(item) {
    const template = document.querySelector("#default-static-template");
    super(template.content.cloneNode(true).children[0]);

    this.poster = "https://placehold.co/600x400?text=Video";

    this.item = item;
    this.video = createElement("video");
    this.element.querySelector("span").replaceWith(this.video);
  }

  init(grid) {
    this.video.src = this.item.src;
    this.element.style.overflow = "hidden";
    this.video.setAttribute("width", "100%");
    this.video.setAttribute("height", "100%");

    if (this.item.src === "") {
      this.video.setAttribute("poster", this.poster);
    }

    this.initButtons("Video Options", () => grid.removeWidget(this.item.el));
  }

  createForm() {
    const srcInput = this.createTextInput("src", "Video-URL", this.item.src);
    const titleInput = this.createTextInput(
      "title",
      "Tooltip",
      this.item.title
    );
    const controlsInput = this.createSwitch(
      "controls",
      "Controls",
      this.item.options.controls
    );
    const autoplayInput = this.createSwitch(
      "autoplay",
      "Autoplay",
      this.item.options.autoplay
    );

    return createElement("form", { className: "dv-form-column" }, [
      srcInput,
      titleInput,
      controlsInput,
      autoplayInput,
    ]);
  }

  saveForm(form) {
    // Save form input
    this.item.src = form.src;
    this.item.title = form.title;
    this.item.options.controls = form.controls === "on";
    this.item.options.autoplay = form.autoplay === "on";

    this.video.src = this.item.src;

    if (this.item.src === "") {
      this.video.setAttribute("poster", this.poster);
    } else {
      this.video.removeAttribute("poster");
    }
  }
}
