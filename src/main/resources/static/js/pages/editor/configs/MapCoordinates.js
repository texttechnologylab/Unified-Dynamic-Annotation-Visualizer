export default class MapCoordinates {
  static token = "MC";
  static defaultConfig = {
    name: "New MapCoordinates",
    type: "MapCoordinates",
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
