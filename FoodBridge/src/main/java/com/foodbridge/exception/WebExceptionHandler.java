package com.foodbridge.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.ModelAndView;

/** Error handling for the Thymeleaf (browser) controllers. */
@Order(Ordered.LOWEST_PRECEDENCE)
@ControllerAdvice(basePackages = "com.foodbridge.controller")
public class WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(WebExceptionHandler.class);

    /** A broken business rule becomes a red alert on the page the user came from. */
    @ExceptionHandler(BusinessRuleException.class)
    public String rule(BusinessRuleException ex, HttpServletRequest req, RedirectAttributes redirect) {
        redirect.addFlashAttribute("error", ex.getMessage());
        String referer = req.getHeader("Referer");
        if (referer != null && referer.contains(req.getServerName())) {
            return "redirect:" + referer;
        }
        return "redirect:/";
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public String conflict(ObjectOptimisticLockingFailureException ex, HttpServletRequest req, RedirectAttributes redirect) {
        return rule(new BusinessRuleException("Someone else changed this at the same time. Please try again."), req, redirect);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ModelAndView notFound(ResourceNotFoundException ex, HttpServletResponse res) {
        res.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return new ModelAndView("error/404");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ModelAndView denied(AccessDeniedException ex, HttpServletResponse res) {
        res.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return new ModelAndView("error/403");
    }

    /** Spring MVC's own errors (unknown URL, wrong method...) keep their real status code; everything else is a 500. */
    @ExceptionHandler(Exception.class)
    public ModelAndView other(Exception ex, HttpServletResponse res) {
        int code = ex instanceof org.springframework.web.ErrorResponse er ? er.getStatusCode().value() : 500;
        if (code >= 500) {
            log.error("Unexpected error", ex);
        }
        res.setStatus(code);
        return new ModelAndView(code == 404 ? "error/404" : (code == 403 ? "error/403" : "error/500"));
    }
}
