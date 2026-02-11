import { createElement } from "../modules/utils.js";

export default class Modal {
  constructor() {
    this.element = document.querySelector(".dv-modal").parentElement;
    this.title = this.element.querySelector(".dv-title");
    this.header = this.element.querySelector(".dv-modal-header");
    this.body = this.element.querySelector(".dv-modal-body");
    this.footer = this.element.querySelector(".dv-modal-footer");
    this.buttons = this.element.querySelectorAll("button");
    this.listener;

    this.buttons[0].addEventListener("click", () => this.hide());
    this.buttons[1].addEventListener("click", () => this.hide());
  }

  show() {
    this.element.classList.add("show");
    document.body.classList.add("modal-open");
  }

  hide() {
    document.body.classList.remove("modal-open");
    this.element.classList.remove("show");

    this.header.classList.remove("dv-hidden");
    this.title.innerHTML = "";
    this.body.innerHTML = "";
    this.footer.classList.remove("dv-hidden");

    this.buttons[1].classList.remove("dv-hidden");
    this.buttons[2].removeEventListener("click", this.listener);
  }

  confirm(title, message, onConfirm) {
    this.hide();

    this.title.textContent = title;
    this.body.textContent = message;
    this.listener = () => {
      this.hide();
      onConfirm();
    };
    this.buttons[2].addEventListener("click", this.listener);

    this.show();
  }

  alert(title, message) {
    this.hide();

    this.title.textContent = title;
    this.body.textContent = message;
    this.buttons[1].classList.add("dv-hidden");
    this.listener = () => this.hide();
    this.buttons[2].addEventListener("click", this.listener);

    this.show();
  }

  loading(message) {
    this.hide();

    const spinner = createElement(
      "div",
      { className: "dv-modal-body-spinner" },
      [createElement("div", { className: "spinner-border" }), message],
    );

    this.header.classList.add("dv-hidden");
    this.body.append(spinner);
    this.footer.classList.add("dv-hidden");

    this.show();
  }

  render(title, content, onConfirm) {
    this.hide();

    this.title.textContent = title;
    this.body.append(content);

    this.listener = () => {
      this.hide();
      onConfirm();
    };
    this.buttons[2].addEventListener("click", this.listener);

    this.show();
  }
}
