import { getFiles } from "../../../api/files.api.js";
import CheckboxSearch from "./CheckboxSearch.js";
import DateRange from "./DateRange.js";

export default class CorpusFilter {
  constructor(selector) {
    this.root = document.querySelector(selector);

    this.filter = {};
    this.components = {};
  }

  init() {
    this.components.files = new CheckboxSearch("file-filter", getFiles);
    this.components.tags = new CheckboxSearch("tag-filter", async () => []);
    this.components.date = new DateRange("date-filter");
  }

  apply() {
    for (const [key, component] of Object.entries(this.components)) {
      this.filter[key] = component.getValues();
    }
  }

  reset() {
    this.filter = {};
  }
}

export const corpusFilter = new CorpusFilter(".dv-corpus-filter");
