package com.chaosforge.recordcoding.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Task 1 最小可运行入口的行为测试。 */
class AppTest {

    private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();

    private PrintStream out() {
        return new PrintStream(outBytes, true, StandardCharsets.UTF_8);
    }

    private PrintStream err() {
        return new PrintStream(errBytes, true, StandardCharsets.UTF_8);
    }

    private String outText() {
        return outBytes.toString(StandardCharsets.UTF_8);
    }

    private String errText() {
        return errBytes.toString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("--version 打印产品名与版本号并以 0 退出")
    void versionPrintsProductNameAndVersion() {
        int code = App.run(new String[] {"--version"}, out(), err());

        assertEquals(App.EXIT_OK, code);
        assertTrue(outText().contains(App.PRODUCT_NAME), "应打印产品名，实际输出：" + outText());
        assertTrue(outText().contains(App.VERSION), "应打印版本号，实际输出：" + outText());
    }

    @Test
    @DisplayName("未知子命令给出用法提示并以 2 退出")
    void unknownSubcommandShowsUsage() {
        int code = App.run(new String[] {"not-a-command"}, out(), err());

        assertEquals(App.EXIT_USAGE, code);
        assertTrue(errText().contains("未知子命令"), "应提示未知子命令，实际输出：" + errText());
        assertTrue(errText().contains("用法："), "应给出用法提示，实际输出：" + errText());
    }

    @Test
    @DisplayName("无参数时打印用法说明并以 0 退出")
    void noArgsShowsUsage() {
        int code = App.run(new String[0], out(), err());

        assertEquals(App.EXIT_OK, code);
        assertTrue(outText().contains("用法："), "应给出用法提示，实际输出：" + outText());
    }
}
