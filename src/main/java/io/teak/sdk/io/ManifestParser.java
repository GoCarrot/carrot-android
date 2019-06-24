package io.teak.sdk.io;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.XmlResourceParser;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import io.teak.sdk.Teak;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import org.xmlpull.v1.XmlPullParser;

public class ManifestParser {
    public final XmlTag tags;

    public ManifestParser(@NonNull Activity activity) throws IOException, PackageManager.NameNotFoundException {
        AssetManager assetManager = activity.createPackageContext(activity.getPackageName(), 0).getAssets();
        XmlResourceParser xmlResourceParser = assetManager.openXmlResourceParser(0, "AndroidManifest.xml");

        // Parse manifest
        this.tags = parseManifest(xmlResourceParser);
    }

    public static class XmlTag {
        public final Map<String, String> attributes = new HashMap<>();
        public final ArrayList<XmlTag> tags = new ArrayList<>();
        public final String type;

        XmlTag(@NonNull XmlResourceParser xmlResourceParser) {
            this.type = xmlResourceParser.getName();

            // Attributes
            for (int i = 0; i < xmlResourceParser.getAttributeCount(); i++) {
                final String name = xmlResourceParser.getAttributeName(i);
                final String value = xmlResourceParser.getAttributeValue(i);
                this.attributes.put(name, value);
            }
        }

        public String toString(@NonNull String prefix) {
            final StringBuilder builder = new StringBuilder(prefix);
            builder.append("<");
            builder.append(this.type);
            builder.append(" ");
            boolean firstEntry = true;
            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                if (firstEntry)
                    firstEntry = false;
                else
                    builder.append(", ");

                builder.append(entry.getKey());
                builder.append("=");
                builder.append(entry.getValue());
            }
            builder.append(">");
            for (XmlTag tag : this.tags) {
                builder.append(tag.toString(prefix + "\n\t"));
            }
            return builder.toString();
        }

        @Override
        public String toString() {
            return this.toString("");
        }

        public List<XmlTag> find(@NonNull String path) {
            return find(path, null);
        }

        public List<XmlTag> find(@NonNull String path, @Nullable Map.Entry<String, String> match) {
            final String[] pathSplit = path.split("\\.");
            final int startIndex = ("$".equals(pathSplit[0]) || "manifest".equals(pathSplit[0])) ? 1 : 0;
            return findInternal(pathSplit, startIndex, match);
        }

        private List<XmlTag> findInternal(@NonNull String[] path, int index, @Nullable Map.Entry<String, String> match) {
            final List<XmlTag> ret = new ArrayList<>();
            if (index == path.length) {
                if (match == null) {
                    ret.add(this);
                } else if (this.attributes.get(match.getKey()) != null) {
                    if (this.attributes.get(match.getKey()).matches(match.getValue())) {
                        ret.add(this);
                    }
                }
            } else {
                for (XmlTag tag : this.tags) {
                    if (tag.type.matches(path[index])) {
                        final List<XmlTag> recurse = tag.findInternal(path, index + 1, match);
                        if (recurse.size() > 0) {
                            ret.add(tag);
                        }
                    }
                }
            }
            return ret;
        }
    }

    private static XmlTag parseManifest(@NonNull XmlResourceParser xmlResourceParser) {
        XmlTag ret = null;
        try {
            final Stack<XmlTag> xmlTagStack = new Stack<>();
            int xmlEventType = xmlResourceParser.getEventType();
            while (xmlEventType != XmlPullParser.END_DOCUMENT) {
                // If this is a start tag, we may be interested in the tag
                if (xmlEventType == XmlPullParser.START_TAG) {
                    // Push tag
                    final XmlTag tag = new XmlTag(xmlResourceParser);
                    xmlTagStack.push(tag);
                } else if (xmlEventType == XmlPullParser.END_TAG) {
                    ret = xmlTagStack.pop();
                    if (!xmlTagStack.empty()) {
                        xmlTagStack.peek().tags.add(ret);
                    }
                }

                // Advance to next token
                xmlEventType = xmlResourceParser.nextToken();
            }
        } catch (Exception e) {
            Teak.log.exception(e);
        } finally {
            xmlResourceParser.close();
        }

        return ret;
    }
}
