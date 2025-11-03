export default class StaticIFrame {
  constructor(root, src, {}) {
    this.root = d3.select(root);
    this.src = src;
  }

  init() {
    this.root
      .append("iframe")
      .attr("src", this.src)
      .attr("width", "100%")
      .attr("height", "100%");

    this.root.classed("overflow-hidden", true);
    this.root.classed("hide", false);
  }
}
