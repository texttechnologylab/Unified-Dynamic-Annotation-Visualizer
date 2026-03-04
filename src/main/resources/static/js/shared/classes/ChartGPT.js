import { getCompletion, getModels } from "../../api/chat.api.js";
import state from "../../pages/view/utils/viewState.js";
import { svgToBase64, svgToUrl } from "../modules/convert.js";
import { createElement } from "../modules/utils.js";

export default class ChartGPT {
  constructor(instruction) {
    const root = document.querySelector(".dv-chat-bot");
    this.chat = root.querySelector(".dv-chat-messages");
    this.textarea = root.querySelector("textarea");
    this.send = root.querySelector(".dv-chat-send");
    this.modelSelect = root.querySelector("#model-select");
    this.contextSelect = root.querySelector("#context-select");

    root
      .querySelector(".dv-chat-header")
      .addEventListener("click", () => root.classList.toggle("collapsed"));

    this.model = null;
    this.messages = [{ role: "system", content: instruction }];
  }

  async init() {
    this.addMessage({ role: "assistant", content: "How can I help you?" });

    // Enable/disable send button
    this.textarea.addEventListener("input", () => {
      if (this.textarea.value.trim() !== "") {
        this.send.removeAttribute("disabled");
      } else {
        this.send.setAttribute("disabled", "true");
      }
    });

    // Send message
    this.textarea.addEventListener("keydown", (event) => {
      if (event.key === "Enter" && this.textarea.value.trim() !== "") {
        event.preventDefault();
        this.completeMessages();
      }
    });
    this.send.addEventListener("click", () => this.completeMessages());

    // Load available models
    const models = await getModels();
    this.model = models.data[0].id;

    models.data.unshift({ id: this.model, name: "Model" });
    models.data.forEach((model) => {
      this.modelSelect.append(
        createElement("option", { value: model.id, textContent: model.name }),
      );
    });
    this.modelSelect.addEventListener(
      "change",
      (event) => (this.model = event.target.value),
    );
  }

  async completeMessages() {
    this.setLoading(true);

    const chart = state.charts.find(
      (chart) => chart.config.id === this.contextSelect.value,
    );

    const content = chart
      ? [
          { type: "text", text: this.textarea.value },
          {
            type: "image_url",
            image_url: { url: svgToBase64(chart.svg.node()) },
            widget: chart.config.title,
          },
        ]
      : this.textarea.value;

    this.addMessage({ role: "user", content });
    this.textarea.value = "";

    console.log({ model: this.model, messages: this.messages });

    const completion = await getCompletion(this.model, this.messages);
    const answer = completion?.choices[0]?.message;

    this.addMessage(
      answer || { role: "assistant", content: "Error: no completion message" },
    );

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
          textContent: this.parseContent(message.content),
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

  parseContent(content) {
    if (Array.isArray(content)) {
      let string = "";

      content.forEach((item) => {
        if (item.type === "text") {
          string += item.text;
        } else if (item.type === "image_url") {
          string += ` (+${item.widget})`;
        }
      });

      return string;
    }

    return content;
  }
}
