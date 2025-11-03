export default class Modal {
  constructor() {
    this.element = document.querySelector(".dv-modal").parentElement;
    this.title = this.element.querySelector(".dv-title");
    this.body = this.element.querySelector(".dv-modal-body");
    this.buttons = this.element.querySelectorAll("button");
    this.listener;

    this.buttons[0].addEventListener("click", () => this.hide());
    this.buttons[1].addEventListener("click", () => this.hide());
  }

  confirm(title, message, onConfirm) {
    this.hide();

    this.title.textContent = title;
    this.body.textContent = message;
    this.listener = () => {
      onConfirm();
      this.hide();
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

  render(title, content, onConfirm) {
    this.hide();

    this.title.textContent = title;
    this.body.append(content);

    this.listener = () => {
      onConfirm();
      this.hide();
    };
    this.buttons[2].addEventListener("click", this.listener);

    this.show();
  }

  show() {
    this.element.classList.add("show");
    document.body.classList.add("modal-open");
  }

  hide() {
    this.title.innerHTML = "";
    this.body.innerHTML = "";
    this.buttons[1].classList.remove("dv-hidden");
    this.buttons[2].removeEventListener("click", this.listener);

    this.element.classList.remove("show");
    document.body.classList.remove("modal-open");
  }
}

export const modal = new Modal();
