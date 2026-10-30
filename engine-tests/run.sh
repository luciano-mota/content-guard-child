#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p .testbuild
javac -encoding UTF-8 -d .testbuild app/src/main/java/br/com/lcmtech/contentguard/RuleMatcher.java engine-tests/RuleMatcherTest.java
java -cp .testbuild RuleMatcherTest
rm -rf .testbuild
