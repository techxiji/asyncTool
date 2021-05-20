package v15.schedulingtest;

import java.io.*;
import java.util.Objects;

/**
 * @author create by TcSnZh on 2021/5/17-上午11:34
 */
public class FileStringReader {
    public static String readFile(String resourceClasspath) {
        InputStream is = null;
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(
                    is = Objects.requireNonNull(
                            Thread.currentThread().getContextClassLoader(), "get classLoader is null"
                    ).getResourceAsStream(resourceClasspath), () -> "get resource " + resourceClasspath + " is null"
            )));
            StringWriter stringWriter = new StringWriter();
            char[] buf = new char[4096];
            int len;
            while ((len = reader.read(buf)) > 0) {
                stringWriter.write(buf, 0, len);
            }
            return stringWriter.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
