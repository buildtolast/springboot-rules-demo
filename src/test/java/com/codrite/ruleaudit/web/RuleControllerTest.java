package com.codrite.ruleaudit.web;

import com.codrite.ruleaudit.rules.Rule;
import com.codrite.ruleaudit.rules.RuleService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RuleController.class)
public class RuleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RuleService ruleService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testListRules() throws Exception {
        Rule r = new Rule("test", "true", true);
        r.setId("1");
        Mockito.when(ruleService.getAllRules()).thenReturn(List.of(r));

        mockMvc.perform(get("/api/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("1"))
                .andExpect(jsonPath("$[0].description").value("test"));
    }

    @Test
    void testCreateRule() throws Exception {
        Rule r = new Rule("test", "true", true);
        Mockito.when(ruleService.saveRule(Mockito.any(Rule.class))).thenReturn(r);

        mockMvc.perform(post("/api/rules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(r)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("test"));
    }
}
