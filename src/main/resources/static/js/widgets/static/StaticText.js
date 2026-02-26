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

  constructor(
    root,
    src,
    {
      align = "start",
      size = "5",
      weight = "normal",
      style = "normal",
      decoration = "none",
    },
  ) {
    this.root = d3.select(root);
    this.text = src;

    this.align = align;
    this.size = size;
    this.weight = weight;
    this.style = style;
    this.decoration = decoration;
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
