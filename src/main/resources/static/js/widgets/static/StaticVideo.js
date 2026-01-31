export default class StaticVideo {
  constructor(root, src, { controls = true, autoplay = false }) {
    this.root = d3.select(root);
    this.src = src;
    this.controls = controls;
    this.autoplay = autoplay;
  }

  init() {
    this.root
      .append("video")
      .attr("src", this.src)
      .attr("width", "100%")
      .attr("height", "100%")
      .property("controls", this.controls)
      .property("autoplay", this.autoplay);

    this.root.classed("overflow-hidden", true);
    this.root.classed("hide", false);
  }
}
