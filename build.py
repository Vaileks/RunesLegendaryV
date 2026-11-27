"""Compile shared gameplay against Spigot, with an optional Paper event adapter."""

import os
import re
from pathlib import Path
import shutil
import subprocess
from zipfile import ZIP_DEFLATED, ZipFile

ROOT = Path(__file__).resolve().parent
MAVEN_CACHE = Path.home() / ".m2/repository"
SERVER_API = Path(os.environ.get(
    "SERVER_API",
    str(MAVEN_CACHE / "org/spigotmc/spigot-api/1.21.1-R0.1-SNAPSHOT/spigot-api-1.21.1-R0.1-SNAPSHOT.jar"),
))
ADVENTURE_VERSION = os.environ.get("ADVENTURE_VERSION", "4.20.0")
DEPENDENCY_GROUPS = (
    "/net/kyori/", "/org/joml/", "/com/google/guava/",
    "/org/jetbrains/annotations/", "/org/jspecify/", "/net/md-5/bungeecord-chat/",
)
# Use one version of each library. Mixing cached Adventure versions can silently
# compile against the wrong runtime API. Adventure 4.20.0 is bundled for Spigot; Paper uses its native text API.
dependencies = {}
for path in MAVEN_CACHE.rglob("*.jar"):
    if not any(group in str(path) for group in DEPENDENCY_GROUPS):
        continue
    if "-sources" in path.name or "-javadoc" in path.name:
        continue
    artifact = path.parent.parent
    if artifact.name.startswith("adventure-") and path.parent.name != ADVENTURE_VERSION:
        continue
    version = tuple(int(part) for part in re.findall(r"\d+", path.parent.name))
    previous = dependencies.get(artifact)
    if previous is None or version > previous[0]:
        dependencies[artifact] = (version, path)
classpath = [SERVER_API] + [entry[1] for _, entry in sorted(dependencies.items())]
java_home = os.environ.get("JAVA_HOME")
javac = str(Path(java_home) / "bin/javac") if java_home else shutil.which("javac")
if not javac:
    raise SystemExit("JDK 21+ is required; set JAVA_HOME or add javac to PATH.")
if not SERVER_API.is_file():
    raise SystemExit(f"Spigot API not found: {SERVER_API}. Set SERVER_API to the local API JAR.")

classes = ROOT / "build/classes"
shutil.rmtree(classes, ignore_errors=True)
classes.mkdir(parents=True)
sources = sorted(p for p in (ROOT / "src/main/java").rglob("*.java") if p.name != "PaperCombatListener.java")
subprocess.run([
    javac, "--release", "21", "-proc:none", "-encoding", "UTF-8", "-g:none",
    "-cp", os.pathsep.join(map(str, classpath)), "-d", str(classes),
    *map(str, sources),
], check=True)

paper_adapter_api = Path(os.environ.get("PAPER_ADAPTER_API", str(MAVEN_CACHE / "io/papermc/paper/paper-api/1.21.4-R0.1-SNAPSHOT/paper-api-1.21.4-R0.1-SNAPSHOT.jar")))
subprocess.run([javac, "--release", "21", "-proc:none", "-g:none", "-cp", os.pathsep.join(map(str, [classes, paper_adapter_api, *classpath[1:]])), "-d", str(classes), str(ROOT / "src/main/java/dev/vaileks/runeslegendaryv/compat/PaperCombatListener.java")], check=True)

output = ROOT / "LegendaryWeaponsVL.jar"
with ZipFile(output, "w", ZIP_DEFLATED) as archive:
    # Spigot does not supply Adventure; include only the text libraries we use.
    bundled = {"adventure-api", "adventure-key", "adventure-text-minimessage", "adventure-text-serializer-legacy", "examination-api", "examination-string"}
    written = set()
    for dep in classpath[1:]:
        if dep.parent.parent.name not in bundled:
            continue
        with ZipFile(dep) as library:
            for name in library.namelist():
                if name.endswith("/") or name in written or name == "module-info.class" or (name.startswith("META-INF/") and not name.startswith("META-INF/services/")):
                    continue
                archive.writestr(name, library.read(name))
                written.add(name)

    for base in (classes, ROOT / "src/main/resources"):
        for path in sorted(base.rglob("*")):
            if path.is_file():
                archive.writestr(path.relative_to(base).as_posix(), path.read_bytes())
with ZipFile(output) as archive:
    if archive.testzip() is not None:
        raise SystemExit("The generated JAR failed its archive integrity check.")
print(output)
