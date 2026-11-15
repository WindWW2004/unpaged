package com.fixmyride.servicerequest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class WebPageSmokeTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void rendersEveryPrimaryPage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Operations dashboard")));
        mockMvc.perform(get("/jobs"))
                .andExpect(status().isOk())
                .andExpect(view().name("service-jobs"));
        mockMvc.perform(get("/jobs/request"))
                .andExpect(status().isOk())
                .andExpect(view().name("service-job-form"));
        mockMvc.perform(get("/jobs/1"))
                .andExpect(status().isOk())
                .andExpect(view().name("job-details"));
        mockMvc.perform(get("/jobs/2"))
                .andExpect(status().isOk())
                .andExpect(view().name("job-details"));
        mockMvc.perform(get("/jobs/3"))
                .andExpect(status().isOk())
                .andExpect(view().name("job-details"));
        mockMvc.perform(get("/reports"))
                .andExpect(status().isOk())
                .andExpect(view().name("reports"));
    }

    @Test
    void sequentialProcessorRedirectsToAnExplicitControllerRoute() throws Exception {
        mockMvc.perform(post("/jobs/process-next"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));
    }
}
