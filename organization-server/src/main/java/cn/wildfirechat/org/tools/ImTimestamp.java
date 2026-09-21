package cn.wildfirechat.org.tools;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 野火 IM 库时间戳换算工具。
 * <p>
 * 官方《野火IM数据库表结构说明》只写了 t_user_session._dt 是「最后更新/活跃时间戳」（bigint），
 * 没有写明单位（秒/毫秒/微秒）。
 * <p>
 * 单位判断错误会直接导致「今日使用情况」查不到人：SQL 里是拿「今日 0 点」和 _dt 直接比大小的，
 * 若 _dt 存的是秒而阈值传的是毫秒，阈值会比真实值大 1000 倍，当天全部记录都会被过滤掉。
 * 因此这里不假设单位，而是从表中已有数据的量级推断，再统一换算。
 * <p>
 * 量级参考（2025 年）：秒 ≈ 1.7e9，毫秒 ≈ 1.7e12，微秒 ≈ 1.7e15。
 * 分界点 1e11 / 1e14 距离这几种量级都有两个数量级以上余量，不会误判。
 */
public final class ImTimestamp {

    /** 大于等于该值视为微秒 */
    private static final long MICRO_THRESHOLD = 100_000_000_000_000L;
    /** 大于等于该值视为毫秒 */
    private static final long MILLI_THRESHOLD = 100_000_000_000L;

    /**
     * _dt 的单位。
     */
    public enum Unit {
        SECONDS,
        MILLIS,
        MICROS
    }

    private ImTimestamp() {
    }

    /**
     * 依据表中已有数据的量级推断 _dt 的单位。
     *
     * @param reference 表中 _dt 的一个代表性取值，例如 max(_dt)
     * @return 推断出的单位；reference &lt;= 0（表为空或无有效值）时按毫秒处理
     */
    public static Unit detectUnit(long reference) {
        if (reference >= MICRO_THRESHOLD) {
            return Unit.MICROS;
        }
        if (reference >= MILLI_THRESHOLD) {
            return Unit.MILLIS;
        }
        if (reference > 0) {
            return Unit.SECONDS;
        }
        return Unit.MILLIS;
    }

    /**
     * 解析配置项里的单位。为空、auto 或无法识别时返回 null，表示需要自动探测。
     *
     * @param configured 配置项 im.session.timestamp-unit 的值
     */
    public static Unit parseUnit(String configured) {
        if (configured == null) {
            return null;
        }
        String value = configured.trim().toLowerCase();
        switch (value) {
            case "s":
            case "sec":
            case "second":
            case "seconds":
                return Unit.SECONDS;
            case "ms":
            case "milli":
            case "millis":
            case "milliseconds":
                return Unit.MILLIS;
            case "us":
            case "micro":
            case "micros":
            case "microseconds":
                return Unit.MICROS;
            default:
                return null;
        }
    }

    /**
     * 把表中 _dt 的原始值换算成毫秒时间戳，用于展示和排序。
     *
     * @param raw  表中 _dt 的原始值
     * @param unit 该表 _dt 的单位
     * @return 毫秒时间戳；raw &lt;= 0 时返回 0
     */
    public static long toMillis(long raw, Unit unit) {
        if (raw <= 0) {
            return 0;
        }
        switch (unit) {
            case SECONDS:
                return raw * 1000;
            case MICROS:
                return raw / 1000;
            case MILLIS:
            default:
                return raw;
        }
    }

    /**
     * 把毫秒时间戳换算成表 _dt 所用的单位，用于构造 SQL 的比较条件。
     *
     * @param millis 毫秒时间戳
     * @param unit   该表 _dt 的单位
     */
    public static long fromMillis(long millis, Unit unit) {
        switch (unit) {
            case SECONDS:
                return millis / 1000;
            case MICROS:
                return millis * 1000;
            case MILLIS:
            default:
                return millis;
        }
    }

    /**
     * 求「今日 0 点」对应的毫秒时间戳。
     *
     * @param zone 判定「今日」所用的时区，传 null 时用 JVM 默认时区
     */
    public static long todayStartMillis(ZoneId zone) {
        ZoneId actualZone = zone == null ? ZoneId.systemDefault() : zone;
        return LocalDate.now(actualZone).atStartOfDay(actualZone).toInstant().toEpochMilli();
    }
}
