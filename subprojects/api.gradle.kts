// Magnet - only the server jar is required; the old 1.12.2 sources do not Javadoc cleanly on JDK 17.
tasks.withType<org.gradle.api.tasks.javadoc.Javadoc> {
    enabled = false
}
