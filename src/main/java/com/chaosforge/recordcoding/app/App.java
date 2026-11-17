package com.chaosforge.recordcoding.app;

import java.io.PrintStream;

/**
 * 产品入口：桌面常驻小工具（窗口 + 系统托盘），运维与排障通道以 CLI 子命令挂在同一个 jar 上。
 * 默认启动路径 = 真实端点，缺端点或令牌即 fail-fast；替身只在 src/test 与显式离线回放子命令出现。
 * 退出码：0 成功 / 2 参数错 / 3 前置条件缺失。
 */
public final class App {
    public static final String PRODUCT_NAME = "病案编码证据对照系统";
    public static final String VERSION = "1.0.0";
    public static final int EXIT_OK = 0;
    public static final int EXIT_USAGE = 2;
    public static final int EXIT_PRECONDITION = 3;

    private App() {}

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        if (args == null || args.length == 0) {
            printUsage(out);
            return EXIT_OK;
        }
        String command = args[0];
        if ("--version".equals(command) || "-v".equals(command)) {
            out.println(PRODUCT_NAME + " " + VERSION);
            return EXIT_OK;
        }
        if ("--help".equals(command) || "-h".equals(command) || "help".equals(command)) {
            printUsage(out);
            return EXIT_OK;
        }
        return AppCli.run(args, out, err);
    }

    private static void printUsage(PrintStream ps) {
        ps.println("用法：java -jar record-coding-evidence.jar [子命令]");
        ps.println();
        ps.println("桌面程序：java -jar record-coding-evidence.jar gui");
        ps.println("  （读取环境变量中的真实端点与令牌后启动窗口 + 系统托盘；缺配置即退出并列出缺失变量名）");
        ps.println();
        ps.println("运维子命令：");
        ps.println("  --version        打印产品名与版本号后退出");
        ps.println("  --help           打印本用法说明");
        ps.println("  config-check     校验配置与凭据位，缺项即列出缺失变量名");
        ps.println("  conn-check       打印连接状态、待处理条数与今日已提交数");
        ps.println("  replay           离线回放本地样例，产出对照表草案");
        ps.println("  batch            批量跑批，输出条数 / 耗时 / 吞吐 / 一致率");
        ps.println("  case-suite       逐条执行项目内案例集");
        ps.println("  capture          采集真实运行截图到 docs/screenshots/");
        ps.println();
        ps.println("退出码：0 成功 / 2 参数错 / 3 前置条件缺失。");
    }
}
