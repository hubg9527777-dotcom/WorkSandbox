#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TEST_CLASSES="$(mktemp -d)"
trap 'rm -rf "$TEST_CLASSES"' EXIT
javac -d "$TEST_CLASSES" app/src/main/java/com/mysandbox/work/UpdatePolicy.java tests/UpdatePolicyTest.java
java -cp "$TEST_CLASSES" com.mysandbox.work.UpdatePolicyTest
