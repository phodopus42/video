# video

Requires Java 17+, Maven 3.6.3+.

```bash
export JAVA_HOME=/path/to/java/17
# E.g. for Fedora, it's /usr/lib/jvm/java-17

mvn clean package
mvn -q exec:java -Dexec.mainClass=phodopus.video.Video
```

PLD files are in `src/pld`.
