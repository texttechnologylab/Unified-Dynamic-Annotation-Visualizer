import { getCsv, getTikz } from "../../../api/convertions.api.js";
import { exportData } from "../../../api/data.api.js";
import {
  applyStyles,
  createButton,
  createElement,
  safeFilename,
} from "../../../shared/modules/utils.js";
import state from "../utils/viewState.js";

export default class ExportHandler {
  constructor(widget) {
    this.serializer = new XMLSerializer();
    this.widget = widget;
    this.filename = safeFilename(widget.config.title);
  }

  init(bulkExports = false) {
    const root = this.widget.root.node
      ? this.widget.root.node()
      : this.widget.root;
    const dropdown = root.querySelector(".dv-dropdown-menu");
    const formats = {};

    if (this.widget.svg) {
      formats.svg = "bi bi-file-earmark-code";
      formats.png = "bi bi-image";
    }

    // formats.tex = "bi bi-file-earmark-font";
    formats.csv = "bi bi-table";
    formats.json = "bi bi-braces";

    Object.entries(formats).forEach(([format, icon]) => {
      const button = createButton(icon, "Export as " + format, () =>
        this.prepareExport(format),
      );

      dropdown.append(button);
    });

    if (bulkExports) {
      dropdown.append(createElement("div", { className: "dv-divider" }));

      Object.entries({ csv: "bi bi-table", json: "bi bi-braces" }).forEach(
        ([format, icon]) => {
          const button = createButton(icon, "Export all as " + format, () =>
            this.exportZIP(format),
          );

          dropdown.append(button);
        },
      );
    }
  }

  getJSON() {
    return this.widget.data || [];
  }

  getSVG() {
    return this.widget.svg?.node ? this.widget.svg.node() : this.widget.svg;
  }

  getMetadata() {
    return { ...state.corpusFilter.filter, ...this.widget.filter };
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

  async exportZIP(format) {
    const { pipeline, generator, type } = this.widget.config;

    const blob = await exportData(pipeline, generator.id, type, format, {
      corpus: state.corpusFilter.filter,
      chart: this.widget.filter,
    });
    const url = URL.createObjectURL(blob, {
      type: "application/zip",
    });
    this.downloadURL(url, `${this.filename}.zip`);
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
    let str = "";

    // Prepare svg if one exists
    if (this.widget.svg) {
      let svg = this.getSVG().cloneNode(true);

      svg = applyStyles(svg, [
        { selector: '[stroke="currentColor"]', styles: { stroke: "black" } },
        { selector: '[fill="currentColor"]', styles: { fill: "black" } },
        { selector: '[stroke="transparent"]', styles: { stroke: "none" } },
        { selector: '[fill="transparent"]', styles: { fill: "none" } },
      ]);

      str = this.serializer.serializeToString(svg);
    }

    const type = this.widget.constructor.defaultConfig.type;
    const json = this.getJSON();
    const meta = {
      metadata: this.getMetadata(),
      options: this.widget.config.options,
    };

    const data = await getTikz(type, str, json, meta);
    const url = this.createURL(data.content, "application/x-tex");

    this.downloadURL(url, `${this.filename}.tex`);
  }

  async exportCSV() {
    const type = this.widget.constructor.defaultConfig.type;
    const json = this.getJSON();
    const meta = this.getMetadata();

    const data = await getCsv(type, json, meta);
    const url = this.createURL(data.content, "text/csv");

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
