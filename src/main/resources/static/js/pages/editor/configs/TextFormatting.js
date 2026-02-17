import { getFiles } from "../../../api/files.api.js";
import { getGeneratorOptions } from "../utils/editorActions.js";

export default class TextFormatting {
  static token = "TF";
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
    extends: {
      type: "multiselect",
      label: "Extends (optional)",
      options: () => getGeneratorOptions("TextFormatting"),
    },
  };
}
