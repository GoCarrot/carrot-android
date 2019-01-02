package io.teak.sdk.core;

import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.teak.sdk.Teak;

public class DeepLink {
    /**
     * @deprecated Use the {@link Teak#registerDeepLink(String, String, String, Teak.DeepLink)} instead.
     */
    @Deprecated
    @SuppressWarnings("unused")
    public static void registerRoute(String route, String name, String description, Teak.DeepLink call) {
        internalRegisterRoute(route, name, description, call);
    }

    public static void internalRegisterRoute(String route, String name, String description, Teak.DeepLink call) {
        // https://github.com/rkh/mustermann/blob/master/mustermann-simple/lib/mustermann/simple.rb
        StringBuffer patternString = new StringBuffer();
        Pattern escape = Pattern.compile("[^\\?\\%\\\\/\\:\\*\\w]");
        Matcher matcher = escape.matcher(route);
        while (matcher.find()) {
            matcher.appendReplacement(patternString, Pattern.quote(matcher.group()));
        }
        matcher.appendTail(patternString);
        String pattern = patternString.toString();

        List<String> groupNames = new ArrayList<>();
        Pattern compile = Pattern.compile("((:\\w+)|\\*)");
        patternString = new StringBuffer();
        matcher = compile.matcher(pattern);
        while (matcher.find()) {
            if (matcher.group().equals("*")) {
                // 'splat' behavior could be bad to support from a debugging standpoint
                throw new IllegalArgumentException("'splat' functionality is not supported by TeakLinks. Route: " + route);
                // return "(?<splat>.*?)";
            }
            groupNames.add(matcher.group().substring(1));
            matcher.appendReplacement(patternString, "([^/?#]+)");
        }
        matcher.appendTail(patternString);
        pattern = patternString.toString();

        // Check for duplicate capture group names
        Set<String> set = new HashSet<>(groupNames);

        if (set.size() < groupNames.size()) {
            throw new IllegalArgumentException("Duplicate variable names in TeakLink for route: " + route);
        }

        final String patternKey = pattern;
        final DeepLink link = new DeepLink(route, call, groupNames, name, description);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                synchronized (routes) {
                    routes.put(patternKey, link);
                }
            }
        });
    }

    public static boolean processUri(URI uri) {
        if (uri == null) return false;
        // TODO: Check uri.getAuthority(), and if it exists, make sure it matches one we care about

        synchronized (routes) {
            for (Map.Entry<String, DeepLink> entry : routes.entrySet()) {
                final String key = entry.getKey();
                final DeepLink value = entry.getValue();

                Pattern pattern = null;
                try {
                    pattern = Pattern.compile(key);
                } catch (Exception e) {
                    Teak.log.exception(e);
                }
                if (pattern == null) continue;

                Matcher matcher = pattern.matcher(uri.getPath());
                if (matcher.matches()) {
                    final Map<String, Object> parameterDict = new HashMap<>();
                    int idx = 1; // Index 0 = full match
                    for (String name : value.groupNames) {
                        try {
                            parameterDict.put(name, matcher.group(idx));
                            idx++;
                        } catch (Exception e) {
                            Teak.log.exception(e);
                            return false;
                        }
                    }

                    Map<String, String> query = new HashMap<String, String>();
                    if (uri.getQuery() != null) {
                        String[] pairs = uri.getQuery().split("&");
                        for (String pair : pairs) {
                            int eqIdx = pair.indexOf("=");
                            try {
                                query.put(URLDecoder.decode(pair.substring(0, eqIdx), "UTF-8"),
                                    URLDecoder.decode(pair.substring(eqIdx + 1), "UTF-8"));
                            } catch (Exception ignored) {
                            }
                        }
                    }

                    // Add the query parameters, allow them to overwrite path parameters
                    for (String name : query.keySet()) {
                        parameterDict.put(name, query.get(name));
                    }

                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                value.call.call(parameterDict);
                            } catch (Exception e) {
                                Teak.log.exception(e);
                            }
                        }
                    });
                    return true;
                }
            }
        }
        return false;
    }

    public static List<Map<String, String>> getRouteNamesAndDescriptions() {
        List<Map<String, String>> routeNamesAndDescriptions = new ArrayList<>();
        synchronized (routes) {
            for (Map.Entry<String, DeepLink> entry : routes.entrySet()) {
                DeepLink link = entry.getValue();
                if (link.name != null && !link.name.isEmpty()) {
                    Map<String, String> item = new HashMap<>();
                    item.put("name", link.name);
                    item.put("description", link.description == null ? "" : link.description);
                    item.put("route", link.route);
                    routeNamesAndDescriptions.add(item);
                }
            }
        }
        return routeNamesAndDescriptions;
    }

    public static final Map<String, DeepLink> routes = new HashMap<>();
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final String route;
    private final Teak.DeepLink call;
    private final List<String> groupNames;
    private final String name;
    private final String description;

    private DeepLink(String route, Teak.DeepLink call, List<String> groupNames, String name, String description) {
        this.route = route;
        this.call = call;
        this.groupNames = groupNames;
        this.name = name;
        this.description = description;
    }
}
