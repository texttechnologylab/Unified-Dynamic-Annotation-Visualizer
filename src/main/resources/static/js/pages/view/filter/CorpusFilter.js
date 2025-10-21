import CheckboxSearch from "./CheckboxSearch.js";
import DateRange from "./DateRange.js";

export default class CorpusFilter {
  constructor(selector) {
    this.root = document.querySelector(selector);

    this.filter = {};
    this.components = {};
  }

  init() {
    this.components.files = new CheckboxSearch(
      "file-filter",
      "/api/files/documents"
    );
    this.components.tags = new CheckboxSearch("tag-filter", "/api/filter/tags");
    this.components.date = new DateRange("date-filter");
  }

  apply() {
    for (const [key, component] of Object.entries(this.components)) {
      this.filter[key] = component.getValues();
    }
  }
}

export const corpusFilter = new CorpusFilter(".dv-corpus-filter");
