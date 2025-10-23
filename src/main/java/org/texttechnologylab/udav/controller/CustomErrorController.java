package org.texttechnologylab.udav.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);

        Integer code = Integer.parseInt(status.toString());
        HttpStatus httpStatus = HttpStatus.resolve(code);

        model.addAttribute("code", code);
        model.addAttribute("phrase", httpStatus.getReasonPhrase());
        model.addAttribute("message", message);

        return "/pages/error/error";
    }
}
