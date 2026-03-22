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

      const bbox = svg.getBBox();

      const canvas = document.createElement("canvas");
      canvas.width = bbox.width;
      canvas.height = bbox.height;

      const context = canvas.getContext("2d");
      context.drawImage(img, 0, 0, bbox.width, bbox.height);

      resolve(canvas.toDataURL("image/png"));
    };

    img.src = url;
  });
}
