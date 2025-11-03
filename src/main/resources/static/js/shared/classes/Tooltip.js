import { createElement } from "../modules/utils.js";

export default class Tooltip {
  constructor() {
    this.title = createElement("strong");
    this.message = createElement("span");

    this.tooltip = createElement("div", { className: "dv-tooltip" }, [
      this.title,
      createElement("br"),
      this.message,
    ]);

    document.body.prepend(this.tooltip);
  }

  create(trigger, title, message, anchor = trigger, placement = "bottom") {
    trigger.addEventListener("mouseover", () => {
      this.title.textContent = title;
      this.message.textContent = message;

      const rect = anchor.getBoundingClientRect();
      this.show(this.getPosition(rect, placement));
    });
    trigger.addEventListener("mouseleave", () => this.hide());
  }

  getPosition(rect, placement) {
    switch (placement) {
      case "right":
        return {
          top: rect.top + "px",
          left: rect.right + 2 + "px",
          bottom: "auto",
          right: "auto",
        };
      default:
        return {
          top: rect.bottom + 2 + "px",
          left: rect.left + "px",
          bottom: "auto",
          right: "auto",
        };
    }
  }

  show({ top, left, bottom, right }) {
    this.tooltip.style.top = top;
    this.tooltip.style.left = left;
    this.tooltip.style.bottom = bottom;
    this.tooltip.style.right = right;

    this.tooltip.style.display = "block";
  }

  hide() {
    this.tooltip.style.display = "none";
  }
}

export const tooltip = new Tooltip();
