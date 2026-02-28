export function flatData(datasets, key) {
  return datasets.flatMap(({ [key]: value, ...rest }) =>
    value.map((item) => ({ ...rest, ...item })),
  );
}

export function randomId(str) {
  return str + "-" + Math.random().toString(36).slice(2, 9);
}

export function isObject(item) {
  return typeof item === "object" && !Array.isArray(item) && item !== null;
}

export function debounce(fn, timeout = 300) {
  let timer;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => {
      fn.apply(this, args);
    }, timeout);
  };
}

export function createElement(tag, attributes = {}, children = []) {
  const element = document.createElement(tag);
  Object.assign(element, attributes);
  element.append(...children);

  return element;
}

export function createTemplateElement(
  selector,
  attributes = {},
  children = [],
) {
  const template = document.querySelector(selector);
  const element = template.content.cloneNode(true).children[0];
  Object.assign(element, attributes);
  element.append(...children);

  return element;
}

export function deepClone(object, skip = []) {
  // Make a copy excluding skipped keys
  const copy = {};
  for (const key of Object.keys(object)) {
    if (!skip.includes(key)) copy[key] = object[key];
  }

  // Deep clone the remaining properties
  const clone = structuredClone(copy);

  // Reattach the skipped properties from the original
  for (const key of skip) {
    if (key in object) clone[key] = object[key];
  }

  return clone;
}

export function applyStyles(node, changes) {
  node = d3.select(node);

  changes.forEach((change) => {
    const selection = node.selectAll(change.selector);

    Object.entries(change.styles).forEach(([key, value]) => {
      selection.attr(key, value);
    });
  });

  return node.node();
}
