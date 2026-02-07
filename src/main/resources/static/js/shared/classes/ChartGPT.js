import { getCompletion, getModels } from "../../api/chat.api.js";
import { createElement } from "../modules/utils.js";

export default class ChartGPT {
  constructor(instruction) {
    const root = document.querySelector(".dv-chat-bot");
    this.chat = root.querySelector(".dv-chat");
    this.input = root.querySelector(".dv-text-input");
    this.send = root.querySelector(".dv-chat-send");
    this.select = root.querySelector("#model-select");

    this.model = null;
    this.messages = [{ role: "system", content: instruction }];
  }

  async init() {
    this.addMessage({ role: "assistant", content: "How can I help you?" });

    // Enable/disable send button
    this.input.addEventListener("input", () => {
      if (this.input.value.trim() !== "") {
        this.send.removeAttribute("disabled");
      } else {
        this.send.setAttribute("disabled", "true");
      }
    });

    // Send message
    this.input.addEventListener("keydown", (event) => {
      if (event.key === "Enter" && this.input.value.trim() !== "") {
        this.completeMessages();
      }
    });
    this.send.addEventListener("click", () => this.completeMessages());

    // Load available models
    const models = await getModels();
    this.model = models.data[0].id;

    models.data.forEach((model) => {
      this.select.append(
        createElement("option", { value: model.id, textContent: model.name }),
      );
    });
    this.select.addEventListener(
      "change",
      (event) => (this.model = event.target.value),
    );
  }

  async completeMessages() {
    this.setLoading(true);
    this.addMessage({ role: "user", content: this.input.value });
    this.input.value = "";

    const completion = await getCompletion(this.model, this.messages);

    this.addMessage(completion.choices[0]?.message);
    this.setLoading(false);
  }

  setLoading(loading) {
    if (loading) {
      this.send.setAttribute("disabled", "true");
      this.send.children[0].classList.add("dv-hidden");
      this.send.children[1].classList.remove("dv-hidden");
    } else {
      this.send.children[0].classList.remove("dv-hidden");
      this.send.children[1].classList.add("dv-hidden");
    }
  }

  addMessage(message) {
    const element = createElement(
      "div",
      { className: "dv-chat-message-" + message.role },
      [
        createElement("div", {
          className: "dv-chat-message",
          textContent: message.content,
        }),
      ],
    );

    this.chat.append(element);
    this.messages.push(message);

    // Scroll to added message
    element.scrollIntoView({
      block: "start",
      inline: "nearest",
      behavior: "smooth",
    });
  }
}

export const chartgpt = new ChartGPT("You are an assistant called ChartGPT.");
