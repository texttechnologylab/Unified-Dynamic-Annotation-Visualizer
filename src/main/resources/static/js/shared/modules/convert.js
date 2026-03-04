const serializer = new XMLSerializer();

export function svgToUrl(svg) {
  const string = serializer.serializeToString(svg);
  const encoded = encodeURIComponent(string);
  return `data:image/svg+xml,${encoded}`;
}

export function svgToBase64(svg) {
  const string = serializer.serializeToString(svg);
  const encoded = btoa(encodeURIComponent(string));
  return `data:image/svg+xml;base64,${encoded}`;
}
