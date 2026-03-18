export default class StaticIFrame {
  static defaultConfig = {
    type: "StaticIFrame",
    title: "Inline Frame",
    src: "https://example.com/",
    options: {},
    icon: "bi bi-window",
    w: 8,
    h: 6,
  };
  static formConfig = {
    title: {
      type: "text",
      label: "Tooltip",
    },
    src: {
      type: "text",
      label: "URL",
    },
  };

  constructor(root, config) {
    this.root = d3.select(root);
    this.config = config;

    this.src = config.src || "";
  }

  clear() {
    this.root.select("iframe").remove();
  }

  init() {
    this.render(this.src);
  }

  render(data) {
    this.clear();

    this.root
      .append("iframe")
      .attr("src", data)
      .attr("width", "100%")
      .attr("height", "100%");

    this.root.classed("overflow-hidden", true);
  }
}
