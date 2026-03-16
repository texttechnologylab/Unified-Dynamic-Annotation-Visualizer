import { getCompletion, getModels } from "../../api/chat.api.js";
import state from "../../pages/view/utils/viewState.js";
import { svgToBase64 } from "../modules/convert.js";
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

    this.modelSelect.append(
      createElement("option", {
        value: models.data[0].id,
        textContent: "Model",
      }),
    );
    models.data.forEach((model) => {
      this.modelSelect.append(
        createElement("option", { value: model.id, textContent: model.name }),
      );
    });

    // Get available charts
    const charts = state.charts.filter((chart) => chart.svg);

    this.contextSelect.append(
      createElement("option", { value: "", textContent: "Context" }),
    );
    charts.forEach((chart) => {
      this.contextSelect.append(
        createElement("option", {
          value: chart.config.id,
          textContent: chart.config.title,
        }),
      );
    });
  }

  async completeMessages() {
    this.setLoading(true);

    const chart = state.charts.find(
      (chart) => chart.config.id === this.contextSelect.value,
    );

    // Create message
    const content = [{ type: "text", text: this.textarea.value }];
    if (chart) {
      content.push({
        type: "image_url",
        image_url: { url: svgToBase64(chart.svg.node()) },
        widget: chart.config.title,
      });
    }

    this.addMessage({ role: "user", content });
    this.textarea.value = "";

    // Get answer
    const completion = await getCompletion(
      this.modelSelect.value,
      this.messages,
    );
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
          innerHTML: this.parseContent(message.content),
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
    let string = "";

    if (Array.isArray(content)) {
      content.forEach((item) => {
        if (item.type === "text") {
          string += item.text;
        } else if (item.type === "image_url") {
          string += ` (+${item.widget})`;
        }
      });
    } else {
      string = content;
    }

    // string.replace("<think>", "<small>");
    // string.replace("</think>", "</small>");

    return DOMPurify.sanitize(marked.parse(string));
  }
}
