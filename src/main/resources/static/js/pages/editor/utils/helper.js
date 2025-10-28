export function prepareGenerators(available, allowed) {
  const filtered = available.filter((generator) =>
    allowed.includes(generator.type)
  );

  const mapped = filtered.map((generator) => {
    return { label: generator.name, value: generator.id };
  });

  mapped.push({ label: "Choose...", value: "" });

  return mapped;
}

export function safeValue(list, item) {
  return list.includes(item) ? item : "";
}
