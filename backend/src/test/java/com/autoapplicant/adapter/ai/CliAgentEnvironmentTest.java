package com.autoapplicant.adapter.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CliAgentEnvironmentTest {

    @Test
    void claudeKeepsBasicsAndItsOwnAuthButNoBackendSecrets() {
        Map<String, String> env = backendEnv();

        CliAgentEnvironment.restrict(env, "claude");

        assertThat(env.keySet())
                .containsExactlyInAnyOrder(
                        "Path",
                        "HOME",
                        "USERPROFILE",
                        "SystemRoot",
                        "TEMP",
                        "LC_ALL",
                        "ANTHROPIC_API_KEY",
                        "CLAUDE_CONFIG_DIR");
    }

    @Test
    void jvmAndSpringVariablesThatCanCarryPasswordsAreDropped() {
        Map<String, String> env = new HashMap<>();
        env.put("SPRING_APPLICATION_JSON", "{\"spring.datasource.password\":\"p\"}");
        env.put("JAVA_TOOL_OPTIONS", "-Dspring.datasource.password=p");
        env.put("JDK_JAVA_OPTIONS", "-Dspring.datasource.password=p");
        env.put("_JAVA_OPTIONS", "-Dx=p");

        CliAgentEnvironment.restrict(env, "claude");

        assertThat(env).isEmpty();
    }

    @Test
    void codexGetsTheOpenAiKeyButNotClaudeAuth() {
        Map<String, String> env = backendEnv();
        env.put("CODEX_HOME", "/home/u/.codex");

        CliAgentEnvironment.restrict(env, "C:\\tools\\codex.cmd");

        assertThat(env)
                .containsKeys("OPENAI_API_KEY", "CODEX_HOME", "Path")
                .doesNotContainKeys("ANTHROPIC_API_KEY", "CLAUDE_CONFIG_DIR", "DB_PASS");
    }

    @Test
    void cloudCredentialsAreDroppedUnlessBedrockOrVertexIsSwitchedOn() {
        Map<String, String> off = cloudEnv("0");
        Map<String, String> on = cloudEnv("1");

        CliAgentEnvironment.restrict(off, "claude");
        CliAgentEnvironment.restrict(on, "claude");

        assertThat(off).doesNotContainKeys("AWS_REGION", "GOOGLE_APPLICATION_CREDENTIALS");
        assertThat(on)
                .containsKeys(
                        "AWS_REGION",
                        "AWS_PROFILE",
                        "GOOGLE_APPLICATION_CREDENTIALS",
                        "CLOUD_ML_REGION");
    }

    private static Map<String, String> backendEnv() {
        Map<String, String> env = new HashMap<>();
        env.put("Path", "/usr/bin");
        env.put("HOME", "/home/u");
        env.put("USERPROFILE", "C:\\Users\\u");
        env.put("SystemRoot", "C:\\Windows");
        env.put("TEMP", "C:\\Temp");
        env.put("LC_ALL", "C.UTF-8");
        env.put("ANTHROPIC_API_KEY", "cli-auth");
        env.put("CLAUDE_CONFIG_DIR", "/home/u/.claude");
        env.put("OPENAI_API_KEY", "sk-x");
        env.put("GEMINI_API_KEY", "g");
        env.put("SECRET_ENCRYPTION_KEY", "k");
        env.put("DB_PASS", "p");
        env.put("DB_URL", "jdbc:postgresql://db/app");
        env.put("SPRING_DATASOURCE_PASSWORD", "p");
        env.put("AWS_SECRET_ACCESS_KEY", "aws");
        env.put("GOOGLE_APPLICATION_CREDENTIALS", "/secrets/sa.json");
        return env;
    }

    private static Map<String, String> cloudEnv(String bedrockFlag) {
        Map<String, String> env = new HashMap<>();
        env.put("CLAUDE_CODE_USE_BEDROCK", bedrockFlag);
        env.put("AWS_REGION", "eu-west-1");
        env.put("AWS_PROFILE", "dev");
        env.put("GOOGLE_APPLICATION_CREDENTIALS", "/secrets/sa.json");
        env.put("CLOUD_ML_REGION", "europe-west4");
        return env;
    }
}
