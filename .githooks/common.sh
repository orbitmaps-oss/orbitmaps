# SPDX-License-Identifier: GPL-3.0-or-later
# Shared setup for the hooks in this folder. Sourced by them; git never runs it directly.

cd "$(git rev-parse --show-toplevel)"

# Gradle needs a JDK. Android Studio doesn't pass its bundled JDK to git hooks, so fall back to it.
if [ -z "${JAVA_HOME:-}" ] && ! command -v java >/dev/null 2>&1; then
    for jbr in \
        "/c/Program Files/Android/Android Studio/jbr" \
        "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
        "/opt/android-studio/jbr" \
        "$HOME/android-studio/jbr"; do
        if [ -x "$jbr/bin/java" ] || [ -x "$jbr/bin/java.exe" ]; then
            export JAVA_HOME="$jbr"
            break
        fi
    done
fi

# Prints the first Python 3 that runs. On Windows "python3" can be a Microsoft Store stub that fails.
python_cmd() {
    for candidate in python3 python; do
        if "$candidate" -c "import sys; sys.exit(sys.version_info < (3,))" >/dev/null 2>&1; then
            echo "$candidate"
            return 0
        fi
    done
    echo "Python 3 not found on PATH; it is needed for the policy checks." >&2
    return 1
}

fail() {
    echo "$1" >&2
    echo "(To skip the hooks for one commit or push: --no-verify. CI runs the same checks.)" >&2
    exit 1
}
