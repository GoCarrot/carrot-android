package io.teak.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.GradleException

class Teak implements Plugin<Project> {
    void apply(Project project) {
        Properties props = new Properties()
        try {
            props.load(project.file('teak.properties').newDataInputStream())
        }
        catch(Exception ex) {
            // TODO: Write a sample file here
            throw new GradleException("Missing teak.properties, check the teak.properties.sample file.");
        }

        project.android.applicationVariants.all { variant ->
            buildConfigField "String", "TEAK_APP_ID", "\"${props.getProperty("appId")}\""
            buildConfigField "String", "TEAK_API_KEY", "\"${props.getProperty("apiKey")}\""
        }
    }
}
