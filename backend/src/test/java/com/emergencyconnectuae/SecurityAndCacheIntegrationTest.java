package com.emergencyconnectuae;

import com.emergencyconnectuae.security.IpBlacklist;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class SecurityAndCacheIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IpBlacklist ipBlacklist;

    @Test
    public void testPathTraversalRejection() throws Exception {
        // SRS 10.3: path traversal payloads returning 400 Bad Request
        mockMvc.perform(get("/api/v1/incidents/../history"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/incidents?filter=../bad"))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testIpBlacklisting() throws Exception {
        // SRS 10.4: blacklisted IPs returning 403 Forbidden
        String targetIp = "192.168.1.99";
        ipBlacklist.add(targetIp);
        try {
            mockMvc.perform(get("/api/v1/incidents").with(request -> {
                request.setRemoteAddr(targetIp);
                return request;
            })).andExpect(status().isForbidden());
        } finally {
            ipBlacklist.remove(targetIp);
        }
    }

    @Test
    public void testUnauthenticatedAccess() throws Exception {
        // SRS 10.1: missing or invalid tokens return 401 Unauthorized
        mockMvc.perform(get("/api/v1/incidents"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testCorsAllowedOrigin() throws Exception {
        // SRS 10.5: CORS configuration source checks origin
        mockMvc.perform(options("/api/v1/incidents")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"));
    }

    @Test
    public void testCorsDisallowedOrigin() throws Exception {
        // SRS 10.5: CORS preflight from non-configured origin has no Access-Control-Allow-Origin
        mockMvc.perform(options("/api/v1/incidents")
                .header(HttpHeaders.ORIGIN, "http://malicious.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
