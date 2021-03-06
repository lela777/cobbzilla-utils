package org.cobbzilla.util.http;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.phantomjs.PhantomJSDriver;

import java.io.File;
import java.util.concurrent.TimeUnit;

import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.now;
import static org.cobbzilla.util.io.FileUtil.abs;
import static org.cobbzilla.util.system.Sleep.sleep;

@Slf4j
public class HtmlScreenCapture extends PhantomUtil {

    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(5);

    public HtmlScreenCapture (String phantomJsPath) { super(phantomJsPath); }
    public HtmlScreenCapture (PhantomJSDriver driver) { super(driver); }

    public static final String SCRIPT = "var page = require('webpage').create();\n" +
            "page.open('@@URL@@', function() {\n" +
            "  page.render('@@FILE@@');\n" +
            "});\n";

    public synchronized void capture (String url, File file) {
        execJs(SCRIPT.replace("@@URL@@", url).replace("@@FILE@@", abs(file)));
        long start = now();
        while (file.length() == 0 && now() - start < TIMEOUT) sleep(50);
        if (file.length() == 0 && now() - start >= TIMEOUT) die("capture: file was never written to: "+abs(file));
    }

    public void capture (File in, File out) {
        try {
            capture(in.toURI().toString(), out);
        } catch (Exception e) {
            die("capture("+abs(in)+"): "+e, e);
        }
    }

}
