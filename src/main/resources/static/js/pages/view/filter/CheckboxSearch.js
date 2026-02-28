export default class CheckboxSearch {
  constructor(id, getData) {
    this.getData = getData;
    this.templates = {
      result: document.querySelector("#result-template"),
      checkbox: document.querySelector("#checkbox-template"),
    };

    const root = document.getElementById(id);
    this.input = root.querySelector(".dv-autocomplete-input");
    this.results = root.querySelector(".dv-dropdown");
    this.checkboxes = root.querySelector(".dv-filter-checkboxes");
    this.info = root.querySelector(".dv-info-text");
    this.addedCheckboxes = [];

    this.getData().then((data) => {
      this.total = data.length;

      const items = data.slice(0, 10);
      items.forEach((item) => this.addCheckbox(item));
    });

    let timeout = null;
    this.input.addEventListener("input", () => {
      clearTimeout(timeout);
      timeout = setTimeout(() => this.autocomplete(this.input.value), 300);
    });

    this.results.addEventListener("mousedown", (event) => {
      event.preventDefault();
    });

    this.input.addEventListener("focus", () => {
      this.autocomplete(this.input.value);
      this.results.classList.add("show");
    });

    this.input.addEventListener("blur", () => {
      this.results.classList.remove("show");
      this.results.innerHTML = "";
    });
  }

  getValues() {
    const nodes = this.checkboxes.querySelectorAll(".dv-check-input");
    const checked = Array.from(nodes).filter((cb) => cb.checked);
    const values = checked.map((cb) => cb.value);

    return values;
  }

  createFromTemplate(key) {
    return this.templates[key].content.cloneNode(true).firstElementChild;
  }

  autocomplete(value) {
    this.getData(value)
      .then((data) => {
        const filtered = data.filter((d) => !this.addedCheckboxes.includes(d));

        if (filtered.length > 0) {
          this.updateResults(filtered.slice(0, 5));
        } else {
          this.results.innerHTML = "No results found";
        }
      })
      .catch((error) => {
        console.error(error);
        this.results.innerHTML = "No results found";
      });
  }

  updateResults(items) {
    this.results.innerHTML = "";

    for (const item of items) {
      const result = this.createFromTemplate("result");

      result.querySelector("span").textContent = item;
      result.addEventListener("click", () => this.addCheckbox(item));

      this.results.appendChild(result);
    }
  }

  addCheckbox(item) {
    this.input.blur();

    const checkbox = this.createFromTemplate("checkbox");

    checkbox.querySelector("input").value = item;
    checkbox.querySelector("span").textContent = item;
    checkbox.querySelector("button").addEventListener("click", () => {
      this.checkboxes.removeChild(checkbox);
      this.addedCheckboxes = this.addedCheckboxes.filter((id) => id !== item);
      this.updateInfo();
    });

    this.checkboxes.appendChild(checkbox);
    this.addedCheckboxes.push(item);

    this.updateInfo();
  }

  updateInfo() {
    this.info.textContent = `${this.addedCheckboxes.length} of ${this.total} checkboxes added`;
  }
}
