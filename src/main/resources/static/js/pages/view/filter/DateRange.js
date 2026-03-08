export default class DateRange {
  constructor(id) {
    const root = document.getElementById(id);
    this.inputs = root.querySelectorAll("input");
  }

  getValues() {
    return { dateFrom: this.inputs[0].value, dateTo: this.inputs[1].value };
  }
}
