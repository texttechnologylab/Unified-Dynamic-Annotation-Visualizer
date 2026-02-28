export default class StaticText {
  static defaultConfig = {
    type: "StaticText",
    title: "Text",
    src: "Text field",
    options: {
      align: "start",
      size: "5",
      weight: "normal",
      style: "normal",
      decoration: "none",
    },
    icon: "bi bi-type",
    w: 4,
    h: 2,
  };
  static formConfig = {
    title: {
      type: "text",
      label: "Tooltip",
    },
    src: {
      type: "textarea",
      label: "Text",
    },
    "options.align": {
      type: "select",
      label: "Text align",
      options: ["start", "center", "end"],
    },
    "options.size": {
      type: "select",
      label: "Font size",
      options: ["1", "2", "3", "4", "5", "6"],
    },
    "options.weight": {
      type: "select",
      label: "Font weight",
      options: ["normal", "bold"],
    },
    "options.style": {
      type: "select",
      label: "Font style",
      options: ["normal", "italic"],
    },
    "options.decoration": {
      type: "select",
      label: "Text decoration",
      options: ["none", "underline", "line-through"],
    },
  };

  constructor(root, config) {
    this.root = d3.select(root);
    this.config = config;

    this.text = config.src || "";
    this.align = config.options.align || "start";
    this.size = config.options.size || "5";
    this.weight = config.options.weight || "normal";
    this.style = config.options.style || "normal";
    this.decoration = config.options.decoration || "none";
  }

  clear() {
    this.root.select(".text-container").remove();
  }

  init() {
    this.render(this.text);
  }

  render(data) {
    this.clear();

    this.root
      .append("div")
      .attr(
        "class",
        `text-container text-${this.align} fs-${this.size} fw-${this.weight} fst-${this.style} text-decoration-${this.decoration}`,
      )
      .text(data);
  }
}
