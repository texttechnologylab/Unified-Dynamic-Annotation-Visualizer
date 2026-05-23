import express from "express";
import { createBlob } from "./utils.js";

const app = express();
const port = 3000;

app.use(express.json());

// $ curl -X POST http://localhost:3000/batch/api/json
//        -H "Content-Type: application/json"
//        -d '{"pipeline": "25ff293e-ae64-44fe-8b66-d64e94a2e440", "widget": {"type": "BarChart","generator": {"id": "CategoryNumber-esl7guq"}}}'
app.post("/batch/api/:format", async (request, response) => {
  const blob = await createBlob(request.body, request.params.format);
  const buffer = Buffer.from(await blob.arrayBuffer());

  response.status(200).send(buffer);
});

app.listen(port, () => {
  console.log(`Server running on http://localhost:${port}`);
});
