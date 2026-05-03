import { createZip, getCsv, getTikz } from "../../../api/convertions.api.js";
import {
  createButton,
  createElement,
  safeFilename,
} from "../../../shared/modules/utils.js";

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

    formats.tex = "bi bi-file-earmark-font";
    formats.csv = "bi bi-table";
    formats.json = "bi bi-braces";

    Object.entries(formats).forEach(([format, icon]) => {
      const button = createButton(icon, "Export as " + format, () => {
        this.startExport(format, false);
      });

      dropdown.append(button);
    });

    if (bulkExports) {
      dropdown.append(createElement("div", { className: "dv-divider" }));

      Object.entries(formats).forEach(([format, icon]) => {
        const button = createButton(icon, "Export all as " + format, () => {
          this.startExport(format, true);
        });

        dropdown.append(button);
      });
    }
  }

  startExport(format, bulk) {
    const exporters = {
      svg: () => this.widget.export(bulk).then((d) => this.svgExport(d)),
      png: () => this.widget.export(bulk).then((d) => this.pngExport(d)),
      tex: () => this.widget.export(bulk).then((d) => this.texExport(d)),
      csv: () => this.widget.export(bulk).then((d) => this.csvExport(d)),
      json: () => this.widget.export(bulk).then((d) => this.jsonExport(d)),
    };

    exporters[format]();
  }

  async svgExport({ items, meta }) {
    const namespace = "http://www.w3.org/2000/svg";
    const header = '<?xml version="1.0" standalone="no"?>\r\n';
    const metadata = document.createElementNS(namespace, "metadata");

    // Prepare metadata node
    for (const [key, value] of Object.entries(meta)) {
      const node = document.createElementNS(namespace, key);
      node.textContent = value;
      metadata.appendChild(node);
    }

    // Create blobs
    const blobs = items.map(({ svg }) => {
      const clone = svg.cloneNode(true);
      clone.prepend(metadata.cloneNode(true));

      const str = this.serializer.serializeToString(clone);

      return new Blob([header + str], { type: "image/svg+xml" });
    });

    await this.downloadBlobs(blobs, "svg");
  }

  async pngExport({ items }) {
    const blobs = items.map(async ({ svg }) => {
      const str = this.serializer.serializeToString(svg);
      const url = URL.createObjectURL(
        new Blob([str], { type: "image/svg+xml" }),
      );

      const img = new Image();

      return await new Promise((resolve) => {
        img.onload = () => {
          URL.revokeObjectURL(url);

          const bbox = svg.getBBox();

          const canvas = document.createElement("canvas");
          canvas.width = bbox.width;
          canvas.height = bbox.height;

          const context = canvas.getContext("2d");
          context.drawImage(img, 0, 0, bbox.width, bbox.height);

          canvas.toBlob((b) => resolve(b), "image/png");
        };

        img.src = url;
      });
    });

    await this.downloadBlobs(await Promise.all(blobs), "png");
  }

  async texExport({ items, meta }) {
    const type = this.widget.config.type;

    const blobs = items.map(async ({ svg, json }) => {
      const str = svg ? this.serializer.serializeToString(svg) : "";

      const data = await getTikz(type, str, json, {
        metadata: meta,
        options: this.widget.config.options,
      });

      return new Blob([data.content], { type: "application/x-tex" });
    });

    await this.downloadBlobs(await Promise.all(blobs), "tex");
  }

  async csvExport({ items, meta }) {
    const type = this.widget.config.type;

    const blobs = items.map(async ({ json }) => {
      const data = await getCsv(type, json, meta);

      return new Blob([data.content], { type: "text/csv" });
    });

    await this.downloadBlobs(await Promise.all(blobs), "csv");
  }

  async jsonExport({ items, meta }) {
    const blobs = items.map(({ json }) => {
      const str = JSON.stringify({ metadata: meta, data: json }, null, 2);

      return new Blob([str], { type: "application/json" });
    });

    await this.downloadBlobs(blobs, "json");
  }

  async downloadBlobs(blobs, type) {
    if (blobs.length > 1) {
      const zip = await createZip(blobs, type);
      this.downloadSingleBlob(zip, `${this.filename}.zip`);
    } else {
      this.downloadSingleBlob(blobs[0], `${this.filename}.${type}`);
    }
  }

  downloadSingleBlob(blob, name) {
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");

    a.href = url;
    a.download = name;
    a.click();

    a.remove();
    URL.revokeObjectURL(url);
  }
}
