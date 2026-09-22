import { getFiles } from "../../../api/files.api.js";

const objectValidator = (json) =>
  typeof json === "object" && json !== null && !Array.isArray(json);

export default class TextFormatting {
  static token = "TF";
  static description = `
  A generator for storing text, including various types of formatting and colorization.
  <br> Compatible with: <b>Highlight Text</b>
  <br> Sources: a UIMA annotation type (pick the document below) or a JSON file
  <code>{"text": "...", "segments": [{"begin": 0, "end": 4, "category": "NOUN", "type": "POS"}]}</code>;
  every distinct segment type becomes one annotation layer.`;
  static defaultConfig = {
    name: "New TextFormatting",
    type: "TextFormatting",
    generatorGroup: false,
    settings: {
      style: "underline",
      sofaFile: "",
      keys: {},
      styles: {},
      colors: {},
    },
    extends: [],
  };
  static formConfig = {
    name: {
      type: "text",
      label: "Name",
    },
    generatorGroup: {
      type: "switch",
      label: "Generator group (JSON sources: one text per top-level key)",
    },
    "settings.style": {
      type: "select",
      label: "Accent style (default for all layers)",
      options: ["underline", "highlight", "bold"],
    },
    "settings.sofaFile": {
      type: "searchselect",
      label: "XML file (UIMA sources only)",
      options: {
        headers: ["File", ""],
        getData: getFiles,
      },
    },
    "settings.keys": {
      type: "json",
      label: "JSON sources: segment keys mapping (json), e.g. {\"begin\": \"start\", \"end\": \"stop\"}",
      options: {
        rows: 2,
        validator: objectValidator,
        message: "Invalid json mapping.",
      },
    },
    "settings.styles": {
      type: "json",
      label: "JSON sources: style per layer (json), e.g. {\"POS\": \"underline\", \"NamedEntity\": \"highlight\"}",
      options: {
        rows: 2,
        validator: objectValidator,
        message: "Invalid json mapping.",
      },
    },
    "settings.colors": {
      type: "json",
      label: "Category colors (json), e.g. {\"NOUN\": \"#4e79a7\"} or {\"POS\": {\"NOUN\": \"#4e79a7\"}}",
      options: {
        rows: 3,
        validator: objectValidator,
        message: "Invalid json mapping.",
      },
    },
  };
}
