export default class StaticText {
  constructor(root, src, { style = "" }) {
    this.root = d3.select(root);
    this.text = src;
    this.style = style;
  }

  init() {
    this.root.append("div").attr("class", this.style).text(this.text);

    this.root.classed("hide", false);
  }
}
