const serializer = new XMLSerializer();

export async function svgToBase64(svg) {
  const str = serializer.serializeToString(svg);

  const blob = new Blob([str], {
    type: "image/svg+xml",
  });
  const url = URL.createObjectURL(blob);

  const img = new Image();

  return new Promise((resolve) => {
    img.onload = () => {
      URL.revokeObjectURL(url);

      // The image is the whole SVG, so the canvas takes the SVG's size (as in the PNG export)
      const width = svg.width.baseVal.value;
      const height = svg.height.baseVal.value;

      const canvas = document.createElement("canvas");
      canvas.width = width;
      canvas.height = height;

      const context = canvas.getContext("2d");
      context.drawImage(img, 0, 0, width, height);

      resolve(canvas.toDataURL("image/png"));
    };

    img.src = url;
  });
}
