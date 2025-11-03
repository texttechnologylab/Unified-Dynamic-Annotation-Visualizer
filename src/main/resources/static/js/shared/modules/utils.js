export function flatData(datasets, key) {
  return datasets.flatMap(({ [key]: value, ...rest }) =>
    value.map((item) => ({ ...rest, ...item }))
  );
}

export function minOf(array) {
  return Math.min(...array);
}

export function maxOf(array) {
  return Math.max(...array);
}

export function randomId(str) {
  return str + "-" + Math.random().toString(36).slice(2, 9);
}

export function getElementDimensions(element) {
  const area = element.querySelector(".dv-chart-area");
  const rect = area.getBoundingClientRect();

  return { width: rect.width, height: rect.height };
}

export function createElement(tag, attributes = {}, children = []) {
  const element = document.createElement(tag);
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
