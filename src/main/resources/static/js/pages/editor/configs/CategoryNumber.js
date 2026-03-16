export default class CategoryNumber {
  static token = "CN";
  static description = `
  A generator that maps categories/labels to a numeric value and an associated color.
  <br> Compatible with: <strong>Bar Chart, Pie Chart</strong>`;
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
