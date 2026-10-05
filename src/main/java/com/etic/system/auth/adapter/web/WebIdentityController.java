package com.etic.system.auth.adapter.web;

import com.etic.system.auth.security.WebIdentity;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WebIdentityController {
    @GetMapping("/api/web/me") public Map<String,Object> currentUser() { return WebIdentity.current().profile(); }
}
