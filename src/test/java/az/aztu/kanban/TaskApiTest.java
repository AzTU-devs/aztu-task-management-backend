package az.aztu.kanban;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the HTTP layer, which is where lazily loaded collections leak out of
 * their transaction and blow up during JSON serialisation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TaskApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void signIn() throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@aztu.edu.az\",\"password\":\"Admin123!\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        token = objectMapper.readTree(response).get("token").asText();
        assertThat(token).isNotBlank();
    }

    private String bearer() {
        return "Bearer " + token;
    }

    @Test
    void loginIsRequired() throws Exception {
        mockMvc.perform(get("/api/boards")).andExpect(status().isUnauthorized());
    }

    @Test
    void createMoveAndReadATaskWithLabels() throws Exception {
        JsonNode board = objectMapper.readTree(mockMvc.perform(get("/api/boards/LMS").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        long boardId = board.get("id").asLong();
        long firstColumn = board.get("columns").get(0).get("id").asLong();
        long doneColumn = -1;
        for (JsonNode column : board.get("columns")) {
            if ("DONE".equals(column.get("category").asText())) {
                doneColumn = column.get("id").asLong();
            }
        }
        assertThat(doneColumn).isPositive();

        String payload = """
                {"title":"Serialization regression","boardId":%d,"columnId":%d,
                 "type":"BUG","priority":"HIGH","labels":["api","regression"]}
                """.formatted(boardId, firstColumn);

        JsonNode created = objectMapper.readTree(mockMvc.perform(post("/api/tasks")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.labels").isArray())
                .andReturn().getResponse().getContentAsString());

        long taskId = created.get("id").asLong();
        String taskKey = created.get("taskKey").asText();

        // reading a persisted task must not trip over the lazy label collection
        mockMvc.perform(get("/api/tasks/key/" + taskKey).header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.labels.length()").value(2));

        mockMvc.perform(patch("/api/tasks/" + taskId + "/move")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"columnId\":%d,\"position\":0}".formatted(doneColumn)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("DONE"))
                .andExpect(jsonPath("$.labels.length()").value(2));

        mockMvc.perform(get("/api/boards/LMS/kanban").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.columns.length()").value(5));
    }

    @Test
    void clientMistakesAreClientErrors() throws Exception {
        // an unknown URL must be a 404, never a 500 with a logged stack trace
        mockMvc.perform(get("/api/does-not-exist").header("Authorization", bearer()))
                .andExpect(status().isNotFound());

        // a bad enum in a query parameter is the caller's mistake
        mockMvc.perform(get("/api/tasks?priority=NOT_A_PRIORITY").header("Authorization", bearer()))
                .andExpect(status().isBadRequest());

        // malformed JSON body
        mockMvc.perform(post("/api/tasks")
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());

        // wrong verb on a real endpoint
        mockMvc.perform(patch("/api/platforms").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void configurationEndpointsAreNotExposed() throws Exception {
        mockMvc.perform(get("/actuator/env").header("Authorization", bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/configprops").header("Authorization", bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void healthIsUpWithoutSmtpCredentials() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
