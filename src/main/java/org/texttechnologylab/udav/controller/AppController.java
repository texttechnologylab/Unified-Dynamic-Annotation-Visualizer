package org.texttechnologylab.udav.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.texttechnologylab.udav.api.service.PipelineService;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Controller
public class AppController {
	private final ObjectMapper mapper = new ObjectMapper();
	private final PipelineService service;
	private final List<String> lockedPipelines = List.of("0c1953d4-843b-4de4-a44e-1c607ed5a584=");

	@Value("${app.llm.base-url}")
	private String llmUrl;

	@Value("${app.llm.api-token}")
	private String llmToken;

	public AppController(PipelineService service) {
		this.service = service;
	}

	public String getPipelines() throws Exception {
		return mapper.writeValueAsString(service.listSummaries(0, 100, ""));
	}

	public String getConfigById(String id) throws Exception {
		return service.get(id).toString();
	}

	private String ensureValidConfig(String json) throws Exception {
		JsonNode jsonNode = mapper.readTree(json);
		ObjectNode objectNode;

		if (jsonNode.isObject()) {
			objectNode = (ObjectNode) jsonNode;
		} else {
			objectNode = mapper.createObjectNode();
		}

		objectNode.put("id", UUID.randomUUID().toString());

		return mapper.writeValueAsString(objectNode);
	}

	@GetMapping("/")
	public String index(Model model) throws Exception {
		model.addAttribute("pipelines", getPipelines());
		model.addAttribute("lockedPipelines", lockedPipelines);

		return "/pages/index/index";
	}

	@GetMapping("/view/{id}")
	public String view(@PathVariable("id") String id, Model model) throws Exception {
		model.addAttribute("pipelines", getPipelines());
		model.addAttribute("lockedPipelines", lockedPipelines);
		model.addAttribute("config", getConfigById(id));
		model.addAttribute("chatbot", !llmUrl.isEmpty() && !llmToken.isEmpty());

		return "/pages/view/view";
	}

	@GetMapping("/editor")
	public String editorNew(Model model) throws Exception {
		model.addAttribute("config", "{\"id\":\"" + UUID.randomUUID().toString() + "\"}");

		return "/pages/editor/editor";
	}

	@PostMapping("/editor")
	public String editorFile(@RequestParam("file") MultipartFile file, Model model) throws Exception {
		String json = new String(file.getBytes(), StandardCharsets.UTF_8);
		model.addAttribute("config", ensureValidConfig(json));

		return "/pages/editor/editor";
	}

	@GetMapping("/editor/{id}")
	public String editorEdit(@PathVariable("id") String id, Model model) throws Exception {
		model.addAttribute("config", getConfigById(id));

		return lockedPipelines.contains(id) ? "/error/404" : "/pages/editor/editor";
	}
}
