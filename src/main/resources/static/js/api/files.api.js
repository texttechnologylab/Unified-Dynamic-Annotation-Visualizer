import { api } from "./clients.js";

export async function getFiles(query) {
  const path = query ? `/files/documents?q=${query}` : "/files/documents";

  return await api.get(path);
}

export async function getTags(query) {
  const path = query ? `/files/tags?q=${query}` : "/files/tags";

  return await api.get(path);
}
