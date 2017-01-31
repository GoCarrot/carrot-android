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

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dalvik.system.DexFile;

public class DeepLink {
    private static final String LOG_TAG = "Teak:DeepLink";

    private static String[] getClassesOfPackage(Context context, String packageName) {
        ArrayList<String> classes = new ArrayList<String>();
        try {
            String packageCodePath = context.getPackageCodePath();
            DexFile df = new DexFile(packageCodePath);
            for (Enumeration<String> iter = df.entries(); iter.hasMoreElements(); ) {
                String className = iter.nextElement();
                if (className.contains(packageName)) {
                    classes.add(className.substring(className.lastIndexOf(".") + 1, className.length()));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return classes.toArray(new String[classes.size()]);
    }

    public static void validateAnnotations(Method method, boolean noValidate) {
        // Methods must have String parameters and/or a single Map<String, Object>
        Annotation[] annotations = method.getDeclaredAnnotations();

        for (Annotation annotation : annotations) {
            if (annotation instanceof TeakLink) {
                if (!Modifier.isStatic(method.getModifiers())) {
                    throw new IllegalArgumentException("Method: " + method.getName() + " is not static, @TeakLink will not work properly.");
                } else {
                    TeakLink teakLink = (TeakLink) annotation;

                    // https://github.com/rkh/mustermann/blob/master/mustermann-simple/lib/mustermann/simple.rb
                    StringBuffer patternString = new StringBuffer();
                    Pattern escape = Pattern.compile("[^\\?\\%\\\\/\\:\\*\\w]");
                    Matcher matcher = escape.matcher(teakLink.value());
                    while (matcher.find()) {
                        matcher.appendReplacement(patternString, java.util.regex.Pattern.quote(matcher.group()));
                    }
                    matcher.appendTail(patternString);
                    String pattern = patternString.toString();

                    List<String> dupeCheck = new ArrayList<>();
                    Pattern compile = Pattern.compile("((:\\w+)|\\*)");
                    patternString = new StringBuffer();
                    matcher = compile.matcher(pattern);
                    while (matcher.find()) {
                        if (!matcher.group().substring(1).startsWith("arg")){
                            throw new IllegalArgumentException("Variable names must be 'arg0', 'arg1' etc due to Java");
                        } else if (matcher.group().equals("*")) {
                            // 'splat' behavior could be bad to support from a debugging standpoint
                            throw new IllegalArgumentException("'splat' functionality is not supported by TeakLinks. Method: " + method);
                            // return "(?<splat>.*?)";
                        }
                        dupeCheck.add(matcher.group().substring(1));
                        matcher.appendReplacement(patternString, "(?<" + matcher.group().substring(1) + ">[^/?#]+)");
                    }
                    matcher.appendTail(patternString);
                    pattern = patternString.toString();

                    if (!noValidate) {
                        // Check for duplicate capture group names
                        Set<String> set = new HashSet<>(dupeCheck);

                        if (set.size() < dupeCheck.size()) {
                            throw new IllegalArgumentException("Duplicate variable names in TeakLink for method: " + method);
                        }
                    }

                    Pattern regex = Pattern.compile(pattern);
                    routes.put(regex, new DeepLink(regex, method));
                }
            }
        }
    }

    public static boolean processUri(Uri uri) {
        if (uri == null) return false;
        String uriMatchString = String.format("/%s%s", uri.getAuthority(), uri.getPath());

        for (Map.Entry<Pattern, DeepLink> entry : routes.entrySet()) {
            Pattern key = entry.getKey();
            DeepLink value = entry.getValue();

            if(!value.method.isAccessible()) {
                value.method.setAccessible(true);
            }

            Matcher matcher = key.matcher(uriMatchString);
            if (matcher.matches()) {
                Class<?>[] paramTypes = value.method.getParameterTypes();
                Object[] params = new Object[paramTypes.length];
                for(int i = 0; i < paramTypes.length; i++) {
                    try {
                        params[i] = matcher.group("arg" + i);
                    } catch (Exception e) {
                        Log.e(LOG_TAG, Log.getStackTraceString(e));
                        return false;
                    }
                }
                try {
                    value.method.invoke(null, params);
                } catch (Exception e) {
                    Log.e(LOG_TAG, Log.getStackTraceString(e));
                    return false;
                }
                return true;
            }
        }
        return false;
    }

    private static final Map<Pattern, DeepLink> routes = new HashMap<>();

    private final Pattern regex;
    private final Method method;

    protected DeepLink(Pattern regex, Method method) {
        this.regex = regex;
        this.method = method;
    }
}
