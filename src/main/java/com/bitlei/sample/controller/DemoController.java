/**
 * 
 */
package com.bitlei.sample.controller;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Demo路由.
 * 
 * @author hzyinglei
 *
 */
@RestController
@RequestMapping("/demo")
public class DemoController {
    private static final Logger LOG = LoggerFactory.getLogger(DemoController.class);

    @RequestMapping("/index")
    public void index(HttpServletRequest request) {
        String key = "hello";
        String value = (String) request.getSession().getAttribute(key);
        LOG.info("get {}={}", new Object[] { key, value });

        request.getSession().setAttribute(key, "session");
        value = (String) request.getSession().getAttribute(key);
        LOG.info("get {}={}", new Object[] { key, value });
    }

}
