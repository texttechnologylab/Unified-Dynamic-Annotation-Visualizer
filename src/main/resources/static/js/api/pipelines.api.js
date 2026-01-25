import { http } from "./HTTPClient.js";

export async function getPipelines() {
  const response = await http.get("/pipelines");

  return response.json();
}

export async function getPipeline(id) {
  const response = await http.get(`/pipelines/${id}`);

  return response.json();
}

export async function createPipeline(config) {
  const response = await http.post("/pipelines", config);

  return response.json();
}

export async function updatePipeline(config) {
  const response = await http.put("/pipelines", config);

  return response.json();
}

export async function deletePipeline(id) {
  const response = await http.delete(`/pipelines/${id}`);

  return response.text();
}
