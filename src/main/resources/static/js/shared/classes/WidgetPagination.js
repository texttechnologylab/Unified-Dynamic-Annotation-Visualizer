import { createElement } from "../modules/utils.js";

export default class WidgetPagination {
  constructor({ pageSize = 1, container, onPageChange }) {
    this.pageSize = pageSize;
    this.container = container;
    this.onPageChange = onPageChange;
    this.currentPage = 0;
    this.elements = [];
  }

  get totalPages() {
    return Math.ceil(this.elements.length / this.pageSize);
  }

  init(elements) {
    this.elements = elements;
    this.createControls();
    this.updateIndicator();
  }

  createControls() {
    this.btnPrevious = this.createButton("left");
    this.btnNext = this.createButton("right");
    this.indicator = this.createIndicator();
    this.dropdown = this.createDropdown();

    this.btnPrevious.addEventListener("click", () =>
      this.setPage(this.currentPage - 1),
    );
    this.btnNext.addEventListener("click", () =>
      this.setPage(this.currentPage + 1),
    );
    this.dropdown.addEventListener("change", (e) =>
      this.setPage(parseInt(e.target.value)),
    );

    const controls = createElement(
      "div",
      { className: "dv-pagination-controls" },
      [this.dropdown, this.btnPrevious, this.btnNext, this.indicator],
    );

    this.container.append(controls);
  }

  setPage(page) {
    // handle both edge cases: going forward from the last page wraps to 0,
    // and going back from page 0 wraps to the last page.
    this.currentPage = (page + this.totalPages) % this.totalPages;

    this.onPageChange();
    this.updateIndicator();
    this.dropdown.value = this.currentPage;
  }

  updateIndicator() {
    this.indicator.textContent = `${this.currentPage + 1} / ${this.totalPages}`;
  }

  createButton(side = "left") {
    const btnClass = side === "right" ? "next" : "prev";
    const title = side === "right" ? "Next" : "Previous";
    const icon = "bi bi-chevron-" + side;

    return createElement(
      "button",
      { className: "dv-btn-pagination " + btnClass, type: "button", title },
      [createElement("i", { className: icon })],
    );
  }

  createIndicator() {
    return createElement("span", {
      className: "dv-pagination-indicator",
      textContent: "0 / 0",
    });
  }

  createDropdown() {
    return createElement(
      "select",
      { className: "dv-pagination-dropdown" },
      this.elements.map((opt, index) =>
        createElement("option", { textContent: opt, value: index }),
      ),
    );
  }
}
