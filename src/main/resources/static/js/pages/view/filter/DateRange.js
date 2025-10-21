export default class DateRange {
  constructor(id) {
    const root = document.getElementById(id);
    this.inputs = root.querySelectorAll("input");
  }

  getValues() {
    return { min: this.inputs[0].value, max: this.inputs[1].value };
  }
}
