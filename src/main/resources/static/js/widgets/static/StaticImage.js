export default class StaticImage {
  static defaultConfig = {
    type: "StaticImage",
    title: "Image",
    src: "https://placehold.co/600x400?text=Image",
    options: {},
    icon: "bi bi-image",
    w: 2,
    h: 2,
  };
  static formConfig = {
    title: {
      type: "text",
      label: "Tooltip",
    },
    src: {
      type: "text",
      label: "Image URL",
    },
  };

  constructor(root, config) {
    this.root = d3.select(root);
    this.config = config;

    this.src = config.src || "";
  }

  clear() {
    this.root.select("img").remove();
  }

  init() {
    this.render(this.src);
  }

  render(data) {
    this.clear();

    this.root
      .append("img")
      .attr("src", data)
      .attr("width", "100%")
      .attr("height", "100%");
  }
}
