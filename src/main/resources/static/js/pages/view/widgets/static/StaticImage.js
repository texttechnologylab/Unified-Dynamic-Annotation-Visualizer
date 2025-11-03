export default class StaticImage {
  constructor(root, src, {}) {
    this.root = d3.select(root);
    this.src = src;
  }

  init() {
    this.root
      .append("img")
      .attr("src", this.src)
      .attr("width", "100%")
      .attr("height", "100%");

    this.root.classed("hide", false);
  }
}
