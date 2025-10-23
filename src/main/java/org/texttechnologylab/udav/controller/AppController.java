package org.texttechnologylab.udav.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.texttechnologylab.udav.api.service.PipelineService;

import java.nio.charset.StandardCharsets;

@Controller
public class AppController {
	private final ObjectMapper mapper = new ObjectMapper();
	private final PipelineService service;

	public AppController(PipelineService service) {
		this.service = service;
	}

	public String getPipelines() throws Exception {
		return mapper.writeValueAsString(service.listIds(0, 100, ""));
	}

	public String getWidgetsById(String id) throws Exception {
		return service.get(id).get("widgets").toString();
	}

	public String getConfigById(String id) throws Exception {
		return service.get(id).toString();
	}

	@GetMapping("/")
	public String index(Model model) throws Exception {
		model.addAttribute("pipelines", getPipelines());

		return "/pages/index/index";
	}

	@GetMapping("/view/{id}")
	public String view(@PathVariable("id") String id, Model model) throws Exception {
		model.addAttribute("id", id);
		model.addAttribute("pipelines", getPipelines());
		model.addAttribute("widgets", getWidgetsById(id));

		return "/pages/view/view";
	}

	@GetMapping("/editor")
	public String editorNew(Model model) throws Exception {
		model.addAttribute("config", "{}");

		return "/pages/editor/editor";
	}

	@PostMapping("/editor")
	public String editorFile(@RequestParam("file") MultipartFile file, Model model) throws Exception {
		model.addAttribute("config", new String(file.getBytes(), StandardCharsets.UTF_8));

		return "/pages/editor/editor";
	}

	@GetMapping("/editor/{id}")
	public String editorEdit(@PathVariable("id") String id, Model model) throws Exception {
		model.addAttribute("config", getConfigById(id));

		return "/pages/editor/editor";
	}
}
