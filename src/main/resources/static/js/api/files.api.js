import { api } from "./clients.js";

export async function getFiles(query) {
  const path = query ? `/files/documents?q=${query}` : "/files/documents";
  const response = await api.get(path);

  return response.json();
}
