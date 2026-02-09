export default class TextFormatting {
  static token = "TF";
  static defaultConfig = {
    name: "New TextFormatting",
    type: "TextFormatting",
    settings: {
      style: "underline",
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
  };
}
