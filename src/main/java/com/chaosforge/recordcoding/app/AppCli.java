package com.chaosforge.recordcoding.app;

import com.chaosforge.recordcoding.config.AppConfig;
import com.chaosforge.recordcoding.config.ConfigException;
import com.chaosforge.recordcoding.util.ErrorCodeTable;
import com.chaosforge.recordcoding.util.RuntimeSummary;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/** 运维 CLI：config-check / conn-check / replay / batch / case-suite / capture / gui。 */
public final class AppCli {
    private AppCli() {}

    public static int run(String[] args, PrintStream out, PrintStream err) {
        String cmd = args[0];
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        return switch (cmd) {
            case "config-check" -> configCheck(out);
            case "conn-check" -> connCheck(out);
            case "replay" -> ReplayCommand.run(rest, out, err);
            case "batch" -> BatchRunner.run(rest, out, err);
            case "case-suite" -> CaseSuiteRunner.run(rest, out, err);
            case "capture" -> ScreenshotCapture.run(rest, out, err);
            case "gui" -> launchDesktop(out, err);
            default -> {
                err.println("未知子命令：" + cmd);
                usage(err);
                yield App.EXIT_USAGE;
            }
        };
    }

    public static int configCheck(PrintStream out) {
        AppConfig cfg = AppConfig.fromEnv();
        java.util.List<String> missing = cfg.missingRequired();
        out.println("配置校验（全部凭据位从环境变量读取，无 localhost / mock 默认值）");
        for (String key : AppConfig.REQUIRED) {
            out.println("  " + key + " = " + (cfg.has(key) ? "已配置" : "缺失"));
        }
        if (!missing.isEmpty()) {
            out.println("缺少关键配置项：" + String.join(", ", missing));
            out.println("请以非零退出码结束并补齐上述环境变量后重启。");
            return App.EXIT_PRECONDITION;
        }
        out.println("配置齐全，可启动桌面程序（真实端点）。");
        return App.EXIT_OK;
    }

    public static int connCheck(PrintStream out) {
        RuntimeSummary.Snapshot s = RuntimeSummary.of(readState("status"), readInt("pending"),
                readInt("submitted_today"), Boolean.parseBoolean(readState("degraded")),
                readState("last_error"), readState("last_error_time"));
        out.println("连接状态：" + s.connectionStatus());
        out.println("待处理条数：" + s.pendingCount());
        out.println("今日已提交数：" + s.submittedToday());
        out.println("降级态：" + s.degraded());
        out.println("最近错误：" + (s.lastErrorCode().isEmpty() ? "无" : s.lastErrorCode()));
        out.println("统一错误码表条目：" + ErrorCodeTable.codes().size() + "（重复码 " + ErrorCodeTable.duplicateCodes().size() + "）");
        return App.EXIT_OK;
    }

    private static String readState(String key) {
        Path p = Path.of("data", "runtime-state.properties");
        if (!Files.exists(p)) return key.equals("status") ? "UNAVAILABLE" : "";
        try {
            java.util.Properties props = new java.util.Properties();
            try (var in = Files.newInputStream(p)) { props.load(in); }
            return props.getProperty(key, "");
        } catch (java.io.IOException e) { return ""; }
    }

    private static int readInt(String key) {
        try { return Integer.parseInt(readState(key)); } catch (NumberFormatException e) { return 0; }
    }

    private static int launchDesktop(PrintStream out, PrintStream err) {
        try {
            AppConfig cfg = AppConfig.fromEnv();
            cfg.validateRequired();
        } catch (ConfigException e) {
            err.println("缺少关键配置项：" + e.missingList());
            return App.EXIT_PRECONDITION;
        }
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            err.println("当前为无图形界面环境，无法启动桌面窗口；请在工作站本机运行 gui 子命令。");
            return App.EXIT_PRECONDITION;
        }
        javax.swing.SwingUtilities.invokeLater(() -> {
            com.chaosforge.recordcoding.ui.MainFrame frame = new com.chaosforge.recordcoding.ui.MainFrame();
            frame.setVisible(true);
        });
        out.println("桌面程序已启动（窗口 + 系统托盘）。");
        return App.EXIT_OK;
    }

    public static void usage(PrintStream ps) {
        ps.println("用法：java -jar record-coding-evidence.jar [gui|config-check|conn-check|replay|batch|case-suite|capture|--version|--help]");
    }

    public static Map<String, String> parseOptions(String[] args) {
        Map<String, String> opts = new LinkedHashMap<>();
        for (int i = 0; i < args.length - 1; i += 2) {
            if (args[i].startsWith("--")) opts.put(args[i].substring(2), args[i + 1]);
        }
        return opts;
    }
}
