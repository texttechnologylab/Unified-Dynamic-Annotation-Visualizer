import { getFiles } from "../../../api/files.api.js";

export default class TextFormatting {
  static token = "TF";
  static description = `
  A generator for storing text, including various types of formatting and colorization.
  <br> Compatible with: <strong>Highlight Text</strong>`;
  static defaultConfig = {
    name: "New TextFormatting",
    type: "TextFormatting",
    settings: {
      style: "underline",
      sofaFile: "",
    },
    extends: [],
  };
  static formConfig = {
    name: {
      type: "text",
      label: "Name",
    },
    "settings.style": {
      type: "select",
      label: "Accent style",
      options: ["underline", "highlight", "bold"],
    },
    "settings.sofaFile": {
      type: "searchselect",
      label: "XML File",
      options: {
        header: ["File", ""],
        getData: getFiles,
      },
    },
  };
}
