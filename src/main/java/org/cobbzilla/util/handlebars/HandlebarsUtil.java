package org.cobbzilla.util.handlebars;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.github.jknack.handlebars.io.AbstractTemplateLoader;
import com.github.jknack.handlebars.io.StringTemplateSource;
import com.github.jknack.handlebars.io.TemplateSource;
import lombok.AllArgsConstructor;
import lombok.Cleanup;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.iterators.ArrayIterator;
import org.cobbzilla.util.reflect.ReflectionUtil;
import org.joda.time.DateTime;
import org.joda.time.Period;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.cobbzilla.util.daemon.ZillaRuntime.*;
import static org.cobbzilla.util.security.ShaUtil.sha256_hex;
import static org.cobbzilla.util.string.StringUtil.*;

@AllArgsConstructor @Slf4j
public class HandlebarsUtil extends AbstractTemplateLoader {

    public static final DateTimeFormatter DATE_FORMAT_MMDDYYYY = DateTimeFormat.forPattern("MM/dd/yyyy");
    public static final DateTimeFormatter DATE_FORMAT_MMMM_D_YYYY = DateTimeFormat.forPattern("MMMM d, yyyy");
    public static final DateTimeFormatter DATE_FORMAT_YYYY_MM_DD = DateTimeFormat.forPattern("yyyy-MM-dd");
    public static final DateTimeFormatter DATE_FORMAT_MMM_DD_YYYY = DateTimeFormat.forPattern("MMM dd, yyyy");

    // For now only m (months) and d (days) are supported to add to datetime values within Handlebars (both has to be
    // present at the same time in that same order, but the value for each can be 0 to exclude that one - i.e. 0m15d).
    public static final PeriodFormatter PERIOD_FORMATTER = new PeriodFormatterBuilder()
            .appendMonths().appendSuffix("m").appendDays().appendSuffix("d").toFormatter();

    private String sourceName = "unknown";

    public static Map<String, Object> apply(Handlebars handlebars, Map<String, Object> map, Map<String, Object> ctx) {
        final Map<String, Object> merged = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            final Object value = entry.getValue();
            if (value instanceof String) {
                final String val = (String) value;
                if (val.contains("{{") && val.contains("}}")) {
                    merged.put(entry.getKey(), apply(handlebars, value.toString(), ctx));
                } else {
                    merged.put(entry.getKey(), entry.getValue());
                }

            } else if (value instanceof Map) {
                merged.put(entry.getKey(), apply(handlebars, (Map<String, Object>) value, ctx));

            } else {
                merged.put(entry.getKey(), entry.getValue());
            }
        }
        return merged;
    }

    public static String apply(Handlebars handlebars, String value, Map<String, Object> ctx) {
        try {
            @Cleanup final StringWriter writer = new StringWriter(value.length());
            handlebars.compile(value).apply(ctx, writer);
            return writer.toString();

        } catch (Exception e) {
            return die("apply: "+e, e);
        } catch (Error e) {
            log.warn("apply: "+e, e);
            throw e;
        }
    }

    public static <T> T applyReflectively(Handlebars handlebars, T thing, Map<String, Object> ctx) {
        for (Method getterCandidate : thing.getClass().getMethods()) {

            if (!getterCandidate.getName().startsWith("get")) continue;
            if (!canApplyReflectively(getterCandidate.getReturnType())) continue;

            final String setterName = ReflectionUtil.setterForGetter(getterCandidate.getName());
            for (Method setterCandidate : thing.getClass().getMethods()) {
                if (!setterCandidate.getName().equals(setterName)
                        || setterCandidate.getParameterTypes().length != 1
                        || !setterCandidate.getParameterTypes()[0].isAssignableFrom(getterCandidate.getReturnType())) {
                    continue;
                }
                try {
                    final Object value = getterCandidate.invoke(thing, (Object[]) null);
                    if (value == null) break;
                    if (value instanceof String) {
                        if (value.toString().contains("{{")) {
                            setterCandidate.invoke(thing, apply(handlebars, (String) value, ctx));
                        }
                    } else {
                        // recurse
                        setterCandidate.invoke(thing, applyReflectively(handlebars, value, ctx));
                    }
                } catch (Exception e) {
                    // no setter for getter
                    log.warn("applyReflectively: " + e);
                }
            }
        }
        return thing;
    }

    private static boolean canApplyReflectively(Class<?> returnType) {
        if (returnType.equals(String.class)) return true;
        try {
            return !(returnType.isPrimitive() || (returnType.getPackage() != null && returnType.getPackage().getName().equals("java.lang")));
        } catch (NullPointerException npe) {
            log.warn("canApplyReflectively("+returnType+"): "+npe);
            return false;
        }
    }

    @Override public TemplateSource sourceAt(String source) throws IOException {
        return new StringTemplateSource(sourceName, source);
    }

    public static final Handlebars.SafeString EMPTY_SAFE_STRING = new Handlebars.SafeString("");

    public static void registerUtilityHelpers (final Handlebars hb) {
        hb.registerHelper("sha256", new Helper<Object>() {
            public CharSequence apply(Object src, Options options) {
                if (empty(src)) return "";
                src = HandlebarsUtil.apply(hb, src.toString(), (Map<String, Object>) options.context.model());
                src = sha256_hex(src.toString());
                return new Handlebars.SafeString(src.toString());
            }
        });

        hb.registerHelper("urlEncode", new Helper<Object>() {
            public CharSequence apply(Object src, Options options) {
                if (empty(src)) return "";
                src = HandlebarsUtil.apply(hb, src.toString(), (Map<String, Object>) options.context.model());
                src = urlEncode(src.toString());
                return new Handlebars.SafeString(src.toString());
            }
        });

        hb.registerHelper("find", new Helper<Object>() {
            public CharSequence apply(Object thing, Options options) {
                final Iterator iter;
                if (thing instanceof Collection) {
                    iter = ((Collection) thing).iterator();
                } else if (thing instanceof Map) {
                    iter = ((Map) thing).values().iterator();
                } else if (Object[].class.isAssignableFrom(thing.getClass())) {
                    iter = new ArrayIterator(thing);
                } else {
                    return die("find: invalid argument type "+thing.getClass().getName());
                }
                final String path = options.param(0);
                final String arg = options.param(1);
                final String output = options.param(2);
                while (iter.hasNext()) {
                    final Object item = iter.next();
                    try {
                        final Object val = ReflectionUtil.get(item, path);
                        if (val != null && val.equals(arg)) return new Handlebars.SafeString(""+ReflectionUtil.get(item, output));
                    } catch (Exception e) {
                        log.warn("find: "+e);
                    }
                }
                return EMPTY_SAFE_STRING;
            }
        });

        hb.registerHelper("expr", new Helper<Object>() {
            public CharSequence apply(Object val1, Options options) {
                final String operator = options.param(0);
                final Object val2 = options.param(1);
                final String v1 = val1.toString();
                final String v2 = val2.toString();

                final BigDecimal result;
                switch (operator) {
                    case "+": result = big(v1).add(big(v2)); break;
                    case "-": result = big(v1).subtract(big(v2)); break;
                    case "*": result = big(v1).multiply(big(v2)); break;
                    case "/": result = big(v1).divide(big(v2), BigDecimal.ROUND_HALF_EVEN); break;
                    case "%": result = big(v1).remainder(big(v2)).abs(); break;
                    case "^": result = big(v1).pow(big(v2).intValue()); break;
                    default: return die("expr: invalid operator: "+operator);
                }

                // can't use trigraph (?:) operator here, if we do then for some reason rval always ends up as a double
                final Number rval;
                if (v1.contains(".") || v2.contains(".")) {
                    rval = result.doubleValue();
                } else {
                    rval = result.intValue();
                }
                return new Handlebars.SafeString(rval.toString());
            }
        });

        hb.registerHelper("truncate", new Helper<Integer>() {
            public CharSequence apply(Integer max, Options options) {
                final String val = options.param(0, " ");
                if (empty(val)) return "";
                if (max == -1 || max >= val.length()) return val;
                return new Handlebars.SafeString(val.substring(0, max));
            }
        });

    }

    public static void registerCurrencyHelpers(Handlebars hb) {
        hb.registerHelper("dollarsNoSign", new Helper<Object>() {
            public CharSequence apply(Object src, Options options) {
                if (empty(src)) return "";
                return new Handlebars.SafeString(formatDollarsNoSign(longVal(src)));
            }
        });

        hb.registerHelper("dollarsWithSign", new Helper<Object>() {
            public CharSequence apply(Object src, Options options) {
                if (empty(src)) return "";
                return new Handlebars.SafeString(formatDollarsWithSign(longVal(src)));
            }
        });

        hb.registerHelper("dollarsAndCentsNoSign", new Helper<Object>() {
            public CharSequence apply(Object src, Options options) {
                if (empty(src)) return "";
                return new Handlebars.SafeString(formatDollarsAndCentsNoSign(longVal(src)));
            }
        });

        hb.registerHelper("dollarsAndCentsWithSign", new Helper<Object>() {
            public CharSequence apply(Object src, Options options) {
                if (empty(src)) return "";
                return new Handlebars.SafeString(formatDollarsAndCentsWithSign(longVal(src)));
            }
        });

        hb.registerHelper("dollarsAndCentsPlain", new Helper<Object>() {
            public CharSequence apply(Object src, Options options) {
                if (empty(src)) return "";
                return new Handlebars.SafeString(formatDollarsAndCentsPlain(longVal(src)));
            }
        });
    }

    public static void registerDateHelpers(Handlebars hb) {
        hb.registerHelper("date_short", new Helper<Object>() {
            public CharSequence apply(Object src, Options options) {
                if (empty(src)) src = "now";
                return new Handlebars.SafeString(DATE_FORMAT_MMDDYYYY.print(longVal(src)));
            }
        });

        hb.registerHelper("date_yyyy_mm_dd", new Helper<Object>() {
            public CharSequence apply(Object src, Options options) {
                if (empty(src)) src = "now";
                return new Handlebars.SafeString(DATE_FORMAT_YYYY_MM_DD.print(longVal(src)));
            }
        });

        hb.registerHelper("date_mmm_dd_yyyy", new Helper<Object>() {
            public CharSequence apply(Object src, Options options) {
                if (empty(src)) src = "now";
                return new Handlebars.SafeString(DATE_FORMAT_MMM_DD_YYYY.print(longVal(src)));
            }
        });

        hb.registerHelper("date_long", new Helper<Object>() {
            public CharSequence apply(Object src, Options options) {
                if (empty(src)) src = "now";
                return new Handlebars.SafeString(DATE_FORMAT_MMMM_D_YYYY.print(longVal(src)));
            }
        });
    }

    public static long longVal(Object src) {
        if (src == null) return now();
        String srcStr = src.toString().trim();

        if (srcStr.equals("") || srcStr.equals("0") || srcStr.equals("now")) return now();

        if (srcStr.startsWith("now")) {
            // Multiple periods may be added to the original timestamp (separated by comma), but in the correct order.
            String[] splitSrc = srcStr.substring(3).split(",");
            DateTime result = new DateTime(now()); // todo: allow caller to set timezone. may not be able to use static method
            for (String period : splitSrc) {
                int sign = 1;
                if (period.startsWith("-")) {
                    sign = -1;
                }
                result = result.plus(Period.parse(period, PERIOD_FORMATTER).multipliedBy(sign));
            }
            return result.getMillis();
        }

        return ((Number) src).longValue();
    }

    public static final String CLOSE_XML_DECL = "?>";

    public static void registerXmlHelpers(final Handlebars hb) {
        hb.registerHelper("strip_xml_declaration", new Helper<Object>() {
            public CharSequence apply(Object src, Options options) {
                if (empty(src)) return "";
                String xml = src.toString().trim();
                if (xml.startsWith("<?xml")) {
                    final int closeDecl = xml.indexOf(CLOSE_XML_DECL);
                    if (closeDecl != -1) {
                        xml = xml.substring(closeDecl + CLOSE_XML_DECL.length()).trim();
                    }
                }
                return new Handlebars.SafeString(xml);
            }
        });
    }

}
