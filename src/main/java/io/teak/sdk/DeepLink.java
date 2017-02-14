/* Teak -- Copyright (C) 2016 GoCarrot Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.teak.sdk;

import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DeepLink {
    public static abstract class Call {
        public abstract void call(Map<String, Object> parameters);
    }

    private static final String LOG_TAG = "Teak:DeepLink";

    public static void registerRoute(String route, String name, String description, Call call) {
        // https://github.com/rkh/mustermann/blob/master/mustermann-simple/lib/mustermann/simple.rb
        StringBuffer patternString = new StringBuffer();
        Pattern escape = Pattern.compile("[^\\?\\%\\\\/\\:\\*\\w]");
        Matcher matcher = escape.matcher(route);
        while (matcher.find()) {
            matcher.appendReplacement(patternString, java.util.regex.Pattern.quote(matcher.group()));
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
            matcher.appendReplacement(patternString, "(?<" + matcher.group().substring(1) + ">[^/?#]+)");
        }
        matcher.appendTail(patternString);
        pattern = patternString.toString();

        // Check for duplicate capture group names
        Set<String> set = new HashSet<>(groupNames);

        if (set.size() < groupNames.size()) {
            throw new IllegalArgumentException("Duplicate variable names in TeakLink for route: " + route);
        }

        Pattern regex = Pattern.compile(pattern);
        routes.put(regex, new DeepLink(route, call, groupNames, name, description));
    }

    public static boolean processUri(Uri uri) {
        if (uri == null) return false;
        String uriMatchString = String.format("/%s%s", uri.getAuthority(), uri.getPath());

        for (Map.Entry<Pattern, DeepLink> entry : routes.entrySet()) {
            Pattern key = entry.getKey();
            DeepLink value = entry.getValue();

            Matcher matcher = key.matcher(uriMatchString);
            if (matcher.matches()) {
                Map<String, Object> parameterDict = new HashMap<>();
                for (String name : value.groupNames) {
                    try {
                        parameterDict.put(name, matcher.group(name));
                    } catch (Exception e) {
                        Log.e(LOG_TAG, Log.getStackTraceString(e));
                        return false;
                    }
                }

                try {
                    value.call.call(parameterDict);
                } catch (Exception e) {
                    Log.e(LOG_TAG, Log.getStackTraceString(e));
                    return false;
                }
                return true;
            }
        }
        return false;
    }

    static List<Map<String, String>> getRouteNamesAndDescriptions() {
        List<Map<String, String>> routeNamesAndDescriptions = new ArrayList<>();
        for (Map.Entry<Pattern, DeepLink> entry : routes.entrySet()) {
            DeepLink link = entry.getValue();
            if (link.name != null && !link.name.isEmpty()) {
                Map<String, String> item = new HashMap<>();
                item.put(link.name, link.description == null ? "" : link.description);
                routeNamesAndDescriptions.add(item);
            }
        }
        return routeNamesAndDescriptions;
    }

    private static final Map<Pattern, DeepLink> routes = new HashMap<>();

    private final String route;
    private final Call call;
    private final List<String> groupNames;
    private final String name;
    private final String description;

    protected DeepLink(String route, Call call, List<String> groupNames, String name, String description) {
        this.route = route;
        this.call = call;
        this.groupNames = groupNames;
        this.name = name;
        this.description = description;
    }
}
