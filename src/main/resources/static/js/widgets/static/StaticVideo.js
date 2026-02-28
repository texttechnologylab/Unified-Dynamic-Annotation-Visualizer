export default class StaticVideo {
  static defaultConfig = {
    type: "StaticVideo",
    title: "Video",
    src: "https://lorem.video/cat_10s",
    options: {
      controls: false,
      autoplay: true,
    },
    icon: "bi bi-film",
    w: 6,
    h: 4,
  };
  static formConfig = {
    title: {
      type: "text",
      label: "Tooltip",
    },
    src: {
      type: "text",
      label: "Video URL",
    },
    "options.controls": {
      type: "switch",
      label: "Controls",
    },
    "options.autoplay": {
      type: "switch",
      label: "Autoplay",
    },
  };

  constructor(root, config) {
    this.root = d3.select(root);
    this.config = config;

    this.src = config.src || "";
    this.controls = config.options.controls || true;
    this.autoplay = config.options.autoplay || false;
  }

  clear() {
    this.root.select("video").remove();
  }

  init() {
    this.render(this.src);
  }

  render(data) {
    this.clear();

    this.root
      .append("video")
      .attr("src", data)
      .attr("width", "100%")
      .attr("height", "100%")
      .property("controls", this.controls)
      .property("autoplay", this.autoplay);

    this.root.classed("overflow-hidden", true);
  }
}
