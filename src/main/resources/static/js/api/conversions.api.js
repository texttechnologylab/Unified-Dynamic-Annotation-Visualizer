import { api } from "./clients.js";

export async function getTikz(type, svg, data, meta) {
  return await api.post("/conversions/tikz", { type, svg, data, meta });
}

export async function getCsv(type, data, meta) {
  return await api.post("/conversions/csv", { type, data, meta });
}

export async function createZip(blobs, type) {
  const formData = new FormData();
  blobs.forEach((blob, i) => formData.append("files", blob, `${i}.${type}`));

  return await fetch(api.baseUrl + "/conversions/zip", {
    method: "POST",
    body: formData,
  }).then((response) => response.blob());
}
