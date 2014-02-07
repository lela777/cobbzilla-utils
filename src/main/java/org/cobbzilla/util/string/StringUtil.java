package org.cobbzilla.util.string;

import org.apache.commons.lang.LocaleUtils;
import org.cobbzilla.util.time.ImprovedTimezone;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.util.*;

public class StringUtil {

    public static final String UTF8 = "UTF-8";
    public static final Charset UTF8cs = Charset.forName(UTF8);

    public static final String EMPTY = "";
    public static final String DEFAULT_LOCALE = "en_US";

    public static String prefix(String s, int count) {
        return s == null ? null : s.length() > count ? s.substring(0, count) : s;
    }

    public static String packagePath(Class clazz) {
        return clazz.getPackage().getName().replace(".","/");
    }

    public static boolean empty(String s) { return s == null || s.length() == 0; }

    public static List<String> split (String s, String delim) {
        final StringTokenizer st = new StringTokenizer(s, delim);
        final List<String> results = new ArrayList<>();
        while (st.hasMoreTokens()) {
            results.add(st.nextToken());
        }
        return results;
    }

    public static String lastPathElement(String url) { return url.substring(url.lastIndexOf("/")+1); }

    public static Integer safeParseInt(String s) {
        if (StringUtil.empty(s)) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String shortDateTime(String localeString, Integer timezone, long time) {
        return formatDateTime("SS", localeString, timezone, time);
    }

    public static String mediumDateTime(String localeString, Integer timezone, long time) {
        return formatDateTime("MM", localeString, timezone, time);
    }

    public static String fullDateTime(String localeString, Integer timezone, long time) {
        return formatDateTime("FF", localeString, timezone, time);
    }

    public static String formatDateTime(String style, String localeString, Integer timezone, long time) {
        final Locale locale = LocaleUtils.toLocale(localeString);
        final ImprovedTimezone tz = ImprovedTimezone.getTimeZoneById(timezone);
        return DateTimeFormat.forPattern(DateTimeFormat.patternForStyle(style, locale))
                .withZone(DateTimeZone.forTimeZone(tz.getTimeZone())).print(time);
    }

    public static String trimQuotes (String s) {
        if (s == null) return s;
        while (s.startsWith("\"") || s.startsWith("\'")) s = s.substring(1);
        while (s.endsWith("\"") || s.endsWith("\'")) s = s.substring(0, s.length()-1);
        return s;
    }

    public static String getPackagePath(Class clazz) {
        return clazz.getPackage().getName().replace('.', '/');
    }

    public static String urlEncode (String s) {
        try {
            return URLEncoder.encode(s, UTF8);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("urlEncode: "+e, e);
        }
    }

}
