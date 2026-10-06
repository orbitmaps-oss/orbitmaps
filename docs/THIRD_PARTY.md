# Third-party components

Every dependency of Orbit Maps (libraries, Gradle plugins, GitHub Actions, pipeline tools, fonts)
is listed here with its licence. Each one must also be in
[`config/dependency-allowlist.txt`](../config/dependency-allowlist.txt), and CI
(`scripts/check_dependencies.py`) checks that every allow-list pattern appears in the
**Coordinates** column below.

There are two scopes:

- **App:** shipped inside the APK (any `*RuntimeClasspath` that isn't a test classpath). These need
  an open-source licence that is compatible with GPL-3.0 and **not GPL-only**: Apache-2.0, MIT,
  BSD-2-Clause, BSD-3-Clause, ISC, MPL-2.0, Zlib, Unicode, public domain/CC0, and OFL-1.1 for fonts.
- **Build:** Gradle plugins, compilers, linters and test libraries. These never ship. They must be
  open source (OSI-approved), and copyleft licences such as EPL or LGPL are acceptable here.

**If a licence is not open source, or is unclear: stop and ask.**

## App (shipped in the APK)

| Component | Coordinates | Licence | Why |
|---|---|---|---|
| AndroidX (Activity, Compose, Core, Lifecycle, SavedState, Window, …) | `androidx.*:*` | Apache-2.0 | UI toolkit and Android support libraries |
| Kotlin standard library | `org.jetbrains.kotlin:*` | Apache-2.0 | Language runtime (also the compiler, at build time) |
| kotlinx.coroutines, kotlinx.serialization | `org.jetbrains.kotlinx:*` | Apache-2.0 | Pulled in by Compose; build tooling too |
| JetBrains annotations | `org.jetbrains:annotations` | Apache-2.0 | Nullability annotations |
| JSpecify | `org.jspecify:jspecify` | Apache-2.0 | Nullability annotations (AndroidX) |
| Guava ListenableFuture | `com.google.guava:listenablefuture` | Apache-2.0 | Interface used by AndroidX |

## Build and test only (not shipped)

| Component | Coordinates | Licence | Why |
|---|---|---|---|
| Android Gradle Plugin and tools | `com.android.tools.*:*`, `com.android.tools:*`, `com.android:*`, `com.android.application:*`, `com.android.lint:*`, `com.android.databinding:*` | Apache-2.0 | Builds the app; Android Lint |
| AGP analytics library | `com.android.tools.analytics-library:*` | Apache-2.0 | Part of AGP. It only sends usage stats when a developer opts in through Android Studio settings. Never enabled by this project |
| Kotlin Gradle plugins | `org.jetbrains.kotlin.jvm:*`, `org.jetbrains.kotlin.plugin.compose:*` | Apache-2.0 | Kotlin/JVM and Compose compiler plugins |
| IntelliJ trove4j | `org.jetbrains.intellij.deps:*` | LGPL-2.1 | Used by the Kotlin compiler / lint. Build-time only |
| ktlint Gradle plugin | `org.jlleitschuh.gradle:*`, `org.jlleitschuh.gradle.ktlint:*` | MIT | Kotlin style checks |
| ktlint | `com.pinterest.ktlint:*` | MIT | Kotlin style checks |
| ec4j | `org.ec4j.core:*` | Apache-2.0 | `.editorconfig` parsing for ktlint |
| Clikt | `com.github.ajalt.clikt:*` | Apache-2.0 | ktlint CLI dependency |
| Poko | `dev.drewhamilton.poko:*` | Apache-2.0 | ktlint dependency |
| sarif4k | `io.github.detekt.sarif4k:*` | Apache-2.0 | ktlint SARIF reporter |
| kotlin-logging | `io.github.oshai:*` | Apache-2.0 | ktlint logging |
| SLF4J | `org.slf4j:*` | MIT | Logging API for build tools |
| Logback | `ch.qos.logback:*` | EPL-1.0 OR LGPL-2.1 | ktlint logging backend |
| Guava, Gson, Error Prone annotations, j2objc annotations, Jimfs, Auto Value, Dagger, Tink, FlatBuffers | `com.google.guava:*`, `com.google.code.gson:*`, `com.google.errorprone:*`, `com.google.j2objc:*`, `com.google.jimfs:*`, `com.google.auto.value:*`, `com.google.dagger:*`, `com.google.crypto.tink:*`, `com.google.flatbuffers:*` | Apache-2.0 | AGP / lint dependencies |
| JSR-305 annotations | `com.google.code.findbugs:*` | Apache-2.0 | AGP / lint dependency |
| Protocol Buffers | `com.google.protobuf:*` | BSD-3-Clause | AGP dependency |
| TensorFlow Lite metadata | `org.tensorflow:*` | Apache-2.0 | AGP ML model binding (unused) |
| JavaPoet, JavaWriter | `com.squareup:*` | Apache-2.0 | AGP code generation |
| Apache Commons, HttpComponents, Groovy | `commons-codec:*`, `commons-io:*`, `commons-logging:*`, `org.apache.commons:*`, `org.apache.httpcomponents:*`, `org.apache.groovy:*` | Apache-2.0 | AGP / lint dependencies |
| javax.inject | `javax.inject:*` | Apache-2.0 | AGP dependency |
| Jakarta XML Binding / Activation, GlassFish JAXB, istack, FastInfoset, StAX-Ex | `jakarta.xml.bind:*`, `jakarta.activation:*`, `org.glassfish.jaxb:*`, `com.sun.istack:*`, `com.sun.xml.fastinfoset:*`, `org.jvnet.staxex:*` | BSD-3-Clause (EDL-1.0) | AGP XML handling |
| JNA | `net.java.dev.jna:*` | Apache-2.0 OR LGPL-2.1 | AGP native access |
| JOpt Simple | `net.sf.jopt-simple:*` | MIT | AGP CLI parsing |
| kXML2 | `net.sf.kxml:*` | MIT | Lint XML parsing |
| jose4j | `org.bitbucket.b_c:*` | Apache-2.0 | AGP dependency |
| Bouncy Castle | `org.bouncycastle:*` | MIT (Bouncy Castle Licence) | APK signing |
| Checker Framework qualifiers | `org.checkerframework:*` | MIT | Annotations (Guava) |
| JDOM 2 | `org.jdom:*` | BSD-style (JDOM licence) | AGP XML handling |
| juniversalchardet | `com.googlecode.juniversalchardet:*` | MPL-1.1 | Lint encoding detection |
| ASM | `org.ow2.asm:*` | BSD-3-Clause | Bytecode tooling |
| JUnit 4 | `junit:junit` | EPL-1.0 | Unit tests |
| JUnit 5 platform, opentest4j | `org.junit:*`, `org.junit.platform:*`, `org.opentest4j:*` | EPL-2.0 / Apache-2.0 | Test runner support (AGP) |
| Hamcrest | `org.hamcrest:*` | BSD-3-Clause | JUnit 4 matchers |

## GitHub Actions (CI only)

| Component | Coordinates | Licence | Why |
|---|---|---|---|
| actions/checkout | `actions/checkout` | MIT | Check out the repository |
| actions/setup-java | `actions/setup-java` | MIT | Install Temurin JDK on the runner |
| actions/setup-python | `actions/setup-python` | MIT | Python for the policy scripts |
| actions/upload-artifact | `actions/upload-artifact` | MIT | Upload lint and test reports |
| gradle/actions | `gradle/actions` | MIT | Gradle caching and wrapper validation |

## Planned (not yet added)

| Component | Licence | URL | Note |
|---|---|---|---|
| MapLibre Native (Android) | BSD-2-Clause | https://github.com/maplibre/maplibre-native | Map rendering, PMTiles support |
| PMTiles | BSD-3-Clause | https://github.com/protomaps/PMTiles | Tile archive format |
| Ferrostar | BSD-3-Clause | https://github.com/stadiamaps/ferrostar | Navigation core and UI |
| Valhalla | MIT | https://github.com/valhalla/valhalla | Routing engine |
| valhalla-mobile | **to verify when added** | https://github.com/Rallista/valhalla-mobile | Valhalla bindings for Android |
| SQLite (FTS5) | Public domain | https://sqlite.org | Offline search |
