package com.sr_vb.commons.core.servlets;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.Servlet;
import javax.servlet.ServletException;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.xss.XSSFilter;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * This is a quick "Log Tail" servlet relying on optimized, light weight, random
 * file access capability based on data size
 */
@Component (service = Servlet.class,
        property = {
                Constants.SERVICE_DESCRIPTION + "=Log Access Servlet",
                "sling.servlet.methods=" + HttpConstants.METHOD_GET,
                "sling.servlet.paths=" + "/bin/accesslog",
                "sling.servlet.extensions=" + "html",
                "sling.servlet.extensions=" + "json",
                "sling.servlet.selectors=" + "lognames"
        })
public class LogAccess extends SlingSafeMethodsServlet
{

    private static final long serialVersionUID = 3733823524632013868L;

    private static final String BR_TAG = "<br/>";

    private static final String LOG_EXTN = ".log";

    private static final String P_DECORATION = "</p><hr/><p class=\"logEntry\">";
    private static final String FIRST_P_DECORATION = "<p class=\"logEntry\">";

    private static final String DEFAULT_LOG_FILE_NAME = "error";

    private static final String LOG_DIRECTORY = "crx-quickstart/logs";

    private static final String LOG_NAMES_SELECTOR = "lognames";

    private static final String TEXT_HTML_TYPE = "text/html";

    private static final String APPLICATION_JSON_TYPE = "application/json";

    private final String[] logLevels = { "*INFO*", "*WARN*", "*DEBUG*", "*ERROR*" };

    @Reference
    private XSSFilter xssFilter;

    @Override
    protected final void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
            throws ServletException, IOException
    {
        processRequest(request, response);
    }

    private void processRequest(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
            throws IOException
    {
        String[] selectors = request.getRequestPathInfo().getSelectors();

        if (selectors != null && Arrays.asList(selectors).contains(LOG_NAMES_SELECTOR)) {
            getLogNames(response);
        } else {
            fetchLogEntries(request, response);
        }
    }

    private void fetchLogEntries(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws FileNotFoundException,
            IOException
    {
        String fileName = request.getParameter("logname");
        fileName = (StringUtils.isNotBlank(fileName) ? xssFilter.filter(fileName) : DEFAULT_LOG_FILE_NAME);
        String sizeVal = request.getParameter("datasize");

        int dataSize = 30;
        if (StringUtils.isNotEmpty(sizeVal) && NumberUtils.isNumber(sizeVal)) {
            dataSize = Integer.parseInt(sizeVal);
        }

        RandomAccessFile randomAccess = null;
        try {
            File file = new File(LOG_DIRECTORY + "/" + fileName + LOG_EXTN);
            if (file != null && file.isFile()) {
                randomAccess = new RandomAccessFile(file, "r");
                long randomAccessLength = randomAccess.length();
                long seekPosition = 1024 * dataSize;
                long filePointer = (randomAccessLength > seekPosition ? randomAccessLength - seekPosition : 0);
                randomAccess.seek(filePointer);
                StringBuilder logBuilder = new StringBuilder();
                String lineFeed = StringUtils.EMPTY;
                boolean firstLine = true;
                while ((lineFeed = randomAccess.readLine()) != null) {
                    boolean newLogEntry = false;
                    if (firstLine) {
                        logBuilder.append(P_DECORATION);

                    }
                    for (String logLevel : logLevels) {
                        if (lineFeed.contains(logLevel)) {
                            lineFeed = lineFeed.replace(logLevel, "<span style=\"background-color:yellow;font-weight:bold\">" + logLevel + "</span>");
                            if (!firstLine)
                                logBuilder.append(P_DECORATION);
                            newLogEntry = true;

                            break;
                        }
                    }
                    firstLine = false;

                    logBuilder.append(lineFeed);
                    if (newLogEntry) {
                        logBuilder.append(BR_TAG);
                    }

                }
                sendResponseHTMl(response, logBuilder);
            }
        } finally {
            randomAccess.close();
        }
    }

    private void sendResponseHTMl(final SlingHttpServletResponse response, StringBuilder logBuilder) throws IOException
    {
        response.setContentType(TEXT_HTML_TYPE);
        response.getWriter().write(logBuilder.toString());
        response.getWriter().flush();
        response.getWriter().close();
    }

    private void sendResponseJSON(final SlingHttpServletResponse response, String gsonString) throws IOException
    {

        response.setContentType(APPLICATION_JSON_TYPE);
        response.getWriter().write(gsonString);
        response.getWriter().flush();
        response.getWriter().close();
    }

    private void getLogNames(final SlingHttpServletResponse response) throws IOException
    {
        File file = new File(LOG_DIRECTORY);
        Map<String, String> logs = new HashMap<String, String>();
        if (file != null && file.isDirectory()) {
            File[] files = file.listFiles();
            StringBuilder builder = new StringBuilder();
            builder.append("Available log names: <br/>");
            Pattern pattern = Pattern.compile("(\\d{4})");
            for (File f : files) {
                Matcher matcher = pattern.matcher(f.getName());
                if (!matcher.find()) {
                    logs.put(f.getName().toUpperCase(), f.getName().replace(LOG_EXTN, StringUtils.EMPTY));
                }
            }

            JsonArray collect = logs.entrySet().stream().map(r -> {
                JsonObject jsonObject = new JsonObject();
                jsonObject.addProperty("text", r.getKey());
                jsonObject.addProperty("value", r.getValue());
                return jsonObject;
            }).reduce(new JsonArray(), (jsonArray, jsonObject) -> {
                jsonArray.add(jsonObject);
                return jsonArray;
            }, (jsonArray, otherJsonArray) -> {
                jsonArray.addAll(otherJsonArray);
                return jsonArray;
            });

            Gson gson = new GsonBuilder().setPrettyPrinting().create();

            sendResponseJSON(response, gson.toJson(collect));

        }
    }
}
