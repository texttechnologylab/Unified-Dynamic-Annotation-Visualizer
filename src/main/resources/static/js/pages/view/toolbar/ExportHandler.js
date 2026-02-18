import { getTikz } from "../../../api/convertions.api.js";
import { applyStyles, createElement } from "../../../shared/modules/utils.js";

export default class ExportHandler {
  constructor(widget, formats) {
    this.serializer = new XMLSerializer();
    this.widget = widget;

    const root = widget.root.node ? widget.root.node() : widget.root;
    const dropdown = root.querySelector(".dv-dropdown-menu");

    this.filename = "chart";

    if (dropdown) {
      Object.entries(formats).forEach(([format, icon]) => {
        const button = createElement(
          "button",
          {
            className: "dv-btn",
            type: "button",
            onclick: () => this.prepareExport(format),
          },
          [
            createElement("i", { className: icon }),
            createElement("span", { textContent: "Export as " + format }),
          ],
        );
        dropdown.append(button);
      });
    }
  }

  getJSON() {
    return this.widget.data || [];
  }

  getSVG() {
    return this.widget.svg.node ? this.widget.svg.node() : this.widget.svg;
  }

  getMetadata() {
    return this.widget.filter || {};
  }

  prepareExport(format) {
    switch (format) {
      case "svg":
        this.exportSVG();
        break;
      case "png":
        this.exportPNG();
        break;
      case "tex":
        this.exportTEX();
        break;
      case "csv":
        this.exportCSV();
        break;
      case "json":
        this.exportJSON();
        break;
    }
  }

  exportSVG() {
    const namespace = "http://www.w3.org/2000/svg";
    const metadata = document.createElementNS(namespace, "metadata");
    const entries = Object.entries(this.getMetadata());

    for (const [key, value] of entries) {
      const node = document.createElementNS(namespace, key);
      node.textContent = value;
      metadata.appendChild(node);
    }
    const svg = this.getSVG().cloneNode(true);
    svg.prepend(metadata);

    const header = '<?xml version="1.0" standalone="no"?>\r\n';
    const str = this.serializer.serializeToString(svg);
    const url = this.createURL(header + str, "image/svg+xml");

    this.downloadURL(url, `${this.filename}.svg`);
  }

  exportPNG() {
    const str = this.serializer.serializeToString(this.getSVG());
    const url = this.createURL(str, "image/svg+xml");
    const img = new Image();

    img.onload = () => {
      const bbox = this.getSVG().getBBox();

      const canvas = document.createElement("canvas");
      canvas.width = bbox.width;
      canvas.height = bbox.height;

      const context = canvas.getContext("2d");
      context.drawImage(img, 0, 0, bbox.width, bbox.height);

      this.downloadURL(canvas.toDataURL(), `${this.filename}.png`);
    };
    img.src = url;
  }

  async exportTEX() {
    let svg = this.getSVG().cloneNode(true);
    svg = applyStyles(svg, [
      { selector: '[stroke="currentColor"]', styles: { stroke: "black" } },
      { selector: '[fill="currentColor"]', styles: { fill: "black" } },
      { selector: '[stroke="transparent"]', styles: { stroke: "none" } },
      { selector: '[fill="transparent"]', styles: { fill: "none" } },
    ]);

    const type = this.widget.constructor.defaultConfig.type;
    const str = this.serializer.serializeToString(svg);
    const json = this.getJSON();
    const metadata = this.getMetadata();

    const data = await getTikz(type, str, json, metadata);
    const url = this.createURL(data, "application/x-tex");

    this.downloadURL(url, `${this.filename}.tex`);
  }

  exportCSV() {
    const json = this.getJSON();
    const keys = Object.keys(json[0]);
    const entries = Object.entries(this.getMetadata());

    const escape = (value) => {
      const str = String(value);
      return /[",\n]/.test(str) ? '"' + str.replace(/"/g, '""') + '"' : str;
    };

    const metadata = entries.map(([k, v]) => `# ${k}: ${v}`);
    const header = keys.join(",");
    const rows = json.map((o) => keys.map((k) => escape(o[k])).join(","));

    const str = [...metadata, header, ...rows].join("\r\n");
    const url = this.createURL(str, "text/csv");

    this.downloadURL(url, `${this.filename}.csv`);
  }

  exportJSON() {
    const json = {
      metadata: this.getMetadata(),
      data: this.getJSON(),
    };
    const str = JSON.stringify(json, null, 2);
    const url = this.createURL(str, "application/json");

    this.downloadURL(url, `${this.filename}.json`);
  }

  createURL(str, type) {
    return URL.createObjectURL(new Blob([str], { type }));
  }

  downloadURL(url, name) {
    const a = document.createElement("a");
    a.href = url;
    a.download = name;

    a.click();

    a.remove();
    URL.revokeObjectURL(url);
  }
}
