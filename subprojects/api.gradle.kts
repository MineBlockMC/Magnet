// Magnet - Dionysus' SIMD utilities use jdk.incubator.vector, so the module must be
// added to javac. This mirrors the <compilerArgs> in the upstream pom, which Toothpick
// does not read for compilation.
tasks.withType<org.gradle.api.tasks.compile.JavaCompile> {
    options.compilerArgs.add("--add-modules=jdk.incubator.vector")
}

// Magnet - only the server jar is required; the old 1.12.2 sources do not Javadoc cleanly on JDK 17.
tasks.withType<org.gradle.api.tasks.javadoc.Javadoc> {
    enabled = false
}
