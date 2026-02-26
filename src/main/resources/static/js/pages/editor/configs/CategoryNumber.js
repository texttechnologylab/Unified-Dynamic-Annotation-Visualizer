export default class CategoryNumber {
  static token = "CN";
  static defaultConfig = {
    name: "New CategoryNumber",
    type: "CategoryNumber",
    settings: {},
    extends: [],
  };
  static formConfig = {
    name: {
      type: "text",
      label: "Name",
    },
  };
}
