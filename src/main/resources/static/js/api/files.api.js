import { http } from "./HTTPClient.js";

export async function getFiles(query) {
  const path = query ? `/files/documents?q=${query}` : "/files/documents";
  const response = await http.get(path);

  return response.json();
}
