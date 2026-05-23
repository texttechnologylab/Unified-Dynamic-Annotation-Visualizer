import HTTPClient from "../../src/main/resources/static/js/api/HTTPClient.js";
// Funktioniert nicht:
// import widgets from "../../src/main/resources/static/js/widgets/widgets.js";

const backend = new HTTPClient("http://localhost:8080/api");

// config + format -> create pipeline -> for each widget -> create svg -> trigger export -> return blob
export async function createBlob(config, format) {
  const data = await getData(
    config.pipeline,
    config.widget.generator.id,
    config.widget.type,
  );

  switch (format) {
    case "json":
      const str = JSON.stringify(data, null, 2);
      return new Blob([str], { type: "application/json" });

    case "csv":
      const csv = await getCsv(config.widget.type, data.data, data.meta);
      return new Blob([csv.content], { type: "text/csv" });
  }
}

async function getData(pipelineId, generatorId, chartType) {
  return await backend.post(
    `/data?pipelineId=${pipelineId}&generatorId=${generatorId}&chartType=${chartType}`,
    {},
  );
}

async function getCsv(type, data, meta) {
  return await backend.post("/convertions/csv", { type, data, meta });
}
