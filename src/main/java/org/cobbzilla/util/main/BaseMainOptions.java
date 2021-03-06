package org.cobbzilla.util.main;

import lombok.Getter;
import lombok.Setter;
import org.kohsuke.args4j.Option;

import java.io.*;
import java.lang.reflect.Field;

import static org.cobbzilla.util.daemon.ZillaRuntime.*;

public class BaseMainOptions {

    public static final String USAGE_HELP = "Show help for this command";
    public static final String OPT_HELP = "-h";
    public static final String LONGOPT_HELP= "--help";
    @Option(name=OPT_HELP, aliases=LONGOPT_HELP, usage=USAGE_HELP)
    @Getter @Setter private boolean help;

    public void out(String s) { System.out.println(s); }
    public void err(String s) { System.err.println(s); }

    public static InputStream inStream  (File file) {
        try { return file != null ? new FileInputStream(file) : System.in; } catch (Exception e) {
            return die("inStream: "+e, e);
        }
    }
    public static OutputStream outStream (File file) {
        try { return file != null ? new FileOutputStream(file) : System.out;  } catch (Exception e) {
            return die("outStream: "+e, e);
        }
    }

    public static BufferedReader reader (File file) {
        try { return file != null ? new BufferedReader(new FileReader(file)) : stdin(); } catch (Exception e) {
            return die("reader: "+e, e);
        }
    }
    public static BufferedWriter writer (File file) {
        try { return new BufferedWriter(file != null ? new FileWriter(file) : stdout()); } catch (Exception e) {
            return die("writer: "+e, e);
        }
    }

    public void required(String field) {
        try {
            final Field optField = getClass().getField("OPT_"+field);
            final Field longOptField = getClass().getField("LONGOPT_"+field);
            err("Missing option: "+optField.get(null)+"/"+longOptField.get(null));
        } catch (Exception e) {
            die("No such field: "+field+": "+e, e);
        }
    }

    public void requiredAndDie(String field) {
        try {
            final Field optField = getClass().getField("OPT_"+field);
            final Field longOptField = getClass().getField("LONGOPT_"+field);
            die("Missing option: "+optField.get(null)+"/"+longOptField.get(null));
        } catch (Exception e) {
            die("No such field: "+field+": "+e, e);
        }
    }
}
