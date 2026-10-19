# build-logic

Vendored copy of the [Toothpick](https://github.com/jpenilla/Toothpick) Gradle plugin
(commit `5556a5e`, version `1.1.0-SNAPSHOT`), ported to Gradle 9 and Shadow 9.

Toothpick is licensed under the MIT License; the original copyright headers are kept in
every source file under `src/main/kotlin/xyz/jpenilla/toothpick`.

Changes from upstream:

- `Project.getConvention()` / `JavaPluginConvention` replaced with `JavaPluginExtension`.
- `JavaExec.main` replaced with `JavaExec.mainClass`.
- `Project.getBuildDir()` / `project.buildDir` replaced with the lazily-provided build directory.
- Shadow 9: `SimpleRelocator` replaces the reflection-based `ToothpickRelocator`, and the
  Log4j2 transformer implements the new `ResourceTransformer` API.
- Shadow 9 defaults to `DuplicatesStrategy.EXCLUDE`, which drops duplicate resources before
  transformers see them. `Log4j2Plugins.dat` and `META-INF/services/**` are switched to
  `INCLUDE` so the plugin cache and service files are merged from every jar.
- `GenerateMavenPom.destination` replaced with `destinationFile`.
- Dependency versions bumped (Shadow 9.6.1, Configurate 4.2.0, Jackson 2.22.4).

The plugin ids (`xyz.jpenilla.toothpick` and `xyz.jpenilla.toothpick.settings`) are unchanged so
the build scripts in the root project keep working.
