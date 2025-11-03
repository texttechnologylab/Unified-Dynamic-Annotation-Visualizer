function init() {
  document.querySelectorAll("[data-bs-toggle='dropdown']").forEach((node) => {
    new bootstrap.Dropdown(node);
  });
}

export default { init };
