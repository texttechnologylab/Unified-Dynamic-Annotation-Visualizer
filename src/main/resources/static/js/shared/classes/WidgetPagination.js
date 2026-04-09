import { createElement } from "../modules/utils.js";

export default class WidgetPagination {
  constructor({ pageSize = 1, container, onPageChange }) {
    this.pageSize = pageSize;
    this.container = container;
    this.onPageChange = onPageChange;
    this.currentPage = 0;
    this.totalElements = 1;
  }

  get totalPages() {
    return Math.ceil(this.totalElements / this.pageSize);
  }

  init(totalElements, options) {
    this.totalElements = totalElements;
    this.createControls(this.container, options);
    this.updateIndicator();
  }

  createControls(container, options) {
    this.btnPrevious = this.createButton("left");
    this.btnNext = this.createButton("right");
    this.indicator = this.createIndicator();

    this.btnPrevious.addEventListener("click", () =>
      this.setPage(this.currentPage - 1),
    );
    this.btnNext.addEventListener("click", () =>
      this.setPage(this.currentPage + 1),
    );

    container.append(this.btnPrevious);
    container.append(this.btnNext);
    container.append(this.indicator);
    container.append(this.dropdown);
  }

  async setPage(page) {
    // handle both edge cases: going forward from the last page wraps to 0,
    // and going back from page 0 wraps to the last page.
    this.currentPage = (page + this.totalPages) % this.totalPages;

    await this.onPageChange();
    this.updateIndicator();
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

  createDropdown(options) {
    return createElement(
      "select",
      { className: "dv-pagination-dropdown" },
      options.map((opt) => createElement("option", { textContent: opt })),
    );
  }
}
