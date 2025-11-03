export default class ExportHandler {
  constructor(node, formats, icons) {
    this.serializer = new XMLSerializer();
    this.storage = {
      metadata: null,
      json: null,
      svg: null,
    };

    this.filename = "chart";
    icons = {
      svg: "bi bi-file-earmark-code",
      png: "bi bi-image",
      csv: "bi bi-table",
      json: "bi bi-braces",
      ...icons,
    };

    formats.forEach((format) => {
      const btn = node
        .append("button")
        .attr("class", "dv-btn")
        .attr("type", "button")
        .on("click", () => this.prepareExport(format));

      btn.append("i").attr("class", icons[format]);
      btn.append("span").text("Export as " + format);
    });
  }

  update(metadata, json, svg) {
    this.storage.metadata = metadata;
    this.storage.json = json;
    this.storage.svg = svg;
  }

  prepareExport(format) {
    switch (format) {
      case "svg":
        this.exportSVG();
        break;
      case "png":
        this.exportPNG();
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
    const entries = Object.entries(this.storage.metadata);

    for (const [key, value] of entries) {
      const node = document.createElementNS(namespace, key);
      node.textContent = value;
      metadata.appendChild(node);
    }
    const svg = this.storage.svg.cloneNode(true);
    svg.prepend(metadata);

    const header = '<?xml version="1.0" standalone="no"?>\r\n';
    const str = this.serializer.serializeToString(svg);
    const url = this.createURL(header + str, "image/svg+xml");

    this.downloadURL(url, `${this.filename}.svg`);
  }

  exportPNG() {
    const str = this.serializer.serializeToString(this.storage.svg);
    const url = this.createURL(str, "image/svg+xml");
    const img = new Image();

    img.onload = () => {
      const bbox = this.storage.svg.getBBox();

      const canvas = document.createElement("canvas");
      canvas.width = bbox.width;
      canvas.height = bbox.height;

      const context = canvas.getContext("2d");
      context.drawImage(img, 0, 0, bbox.width, bbox.height);

      this.downloadURL(canvas.toDataURL(), `${this.filename}.png`);
    };
    img.src = url;
  }

  exportCSV() {
    const json = this.storage.json;
    const keys = Object.keys(json[0]);
    const entries = Object.entries(this.storage.metadata);

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
      metadata: this.storage.metadata,
      data: this.storage.json,
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
