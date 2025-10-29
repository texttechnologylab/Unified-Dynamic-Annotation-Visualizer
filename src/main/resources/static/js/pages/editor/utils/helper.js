export function prepareGenerators(available, allowed) {
  const filtered = available.filter((generator) =>
    allowed.includes(generator.type)
  );

  const mapped = filtered.map((generator) => {
    return { label: generator.name, value: generator.id };
  });

  return mapped;
}

export function safeValue(list, item) {
  list = list.map((o) => o.value);
  return list.includes(item) ? item : "";
}
