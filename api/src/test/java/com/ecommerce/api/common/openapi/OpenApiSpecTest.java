package com.ecommerce.api.common.openapi;

import com.ecommerce.api.support.WebLayerTestSupport;
import io.swagger.v3.oas.annotations.Operation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OpenApiSpecTest extends WebLayerTestSupport {

    // Gradle은 api/ 디렉터리에서 테스트를 실행한다
    private static final Path SPEC_FILE = Path.of("..", "docs", "api", "openapi.yaml");
    private static final String UPDATE_COMMAND = "./gradlew updateOpenApiSpec";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    @Test
    @DisplayName("커밋된 명세 파일은 코드에서 생성한 명세와 같다")
    void specFile_matchesGeneratedSpec() throws Exception {
        String generated = mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        if (Boolean.getBoolean("openapi.update")) {
            Files.createDirectories(SPEC_FILE.getParent());
            Files.writeString(SPEC_FILE, generated, StandardCharsets.UTF_8);
            return;
        }

        if (!Files.exists(SPEC_FILE)) {
            fail("%s가 없다. `%s`으로 만들고 커밋한다.".formatted(SPEC_FILE, UPDATE_COMMAND));
        }
        List<String> committed = lines(Files.readString(SPEC_FILE, StandardCharsets.UTF_8));
        List<String> current = lines(generated);
        int line = firstDifferentLine(committed, current);
        if (line >= 0) {
            fail("""
                    docs/api/openapi.yaml이 코드와 다르다(%d번째 줄부터). `%s`으로 다시 만들고 커밋한다.
                    파일: %s
                    코드: %s""".formatted(line + 1, UPDATE_COMMAND, lineAt(committed, line), lineAt(current, line)));
        }
    }

    @Test
    @DisplayName("모든 엔드포인트에 설명과 분기할 오류가 적혀 있다")
    void everyEndpoint_hasOperationAndErrorCodes() {
        List<String> missing = handlerMapping.getHandlerMethods().values().stream()
                .filter(handler -> handler.getBeanType().getPackageName().startsWith("com.ecommerce.api"))
                .map(HandlerMethod::getMethod)
                .filter(method -> !method.isAnnotationPresent(Operation.class) || !hasErrorCodes(method))
                .map(method -> method.getDeclaringClass().getSimpleName() + "." + method.getName())
                .sorted()
                .toList();

        assertThat(missing)
                .as("@Operation과 @ApiErrorCode를 단다. 분기할 오류가 없으면 @ApiErrorCodes({})를 단다")
                .isEmpty();
    }

    private static boolean hasErrorCodes(Method method) {
        return method.isAnnotationPresent(ApiErrorCode.class) || method.isAnnotationPresent(ApiErrorCodes.class);
    }

    // 체크아웃 설정에 따라 줄바꿈이 CRLF일 수 있다
    private static List<String> lines(String text) {
        return text.replace("\r", "").lines().toList();
    }

    private static int firstDifferentLine(List<String> a, List<String> b) {
        for (int i = 0; i < Math.max(a.size(), b.size()); i++) {
            if (!lineAt(a, i).equals(lineAt(b, i))) {
                return i;
            }
        }
        return -1;
    }

    private static String lineAt(List<String> lines, int index) {
        return index < lines.size() ? lines.get(index) : "(없음)";
    }
}
