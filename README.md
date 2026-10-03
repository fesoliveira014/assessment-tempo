# Hierarchy filter

There are two versions:
- Single file for reading convenience
- Gradle project for compiling and running the tests in IntelliJ or command line

## Requirements

- JDK 17 or newer
- Internet access on the first build, to download Gradle 9.3.0 and the dependencies

## Open in IntelliJ IDEA

1. **File -> Open** and select this folder.
2. Trust the project. IntelliJ imports it through the Gradle wrapper.
3. Click the run icon next to `FilterTest` in `src/test/kotlin/FilterTest.kt` to run the tests.

## Command line

macOS / Linux:

```bash
./gradlew test                                   # run all tests
./gradlew test --tests "FilterTest.testFilter"   # run a single test
./gradlew build                                  # compile and run tests
```

Windows: use `gradlew.bat` in place of `./gradlew`.

# SimpleCache

The findings are in the markdown file.