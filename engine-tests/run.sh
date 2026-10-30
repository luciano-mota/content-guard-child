#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p .testbuild
javac -encoding UTF-8 -d .testbuild \
  app/src/main/java/br/com/lcmtech/contentguard/RuleMatcher.java \
  app/src/main/java/br/com/lcmtech/contentguard/KeywordBatchParser.java \
  app/src/main/java/br/com/lcmtech/contentguard/SocialSearchRules.java \
  app/src/main/java/br/com/lcmtech/contentguard/TvDpadDecision.java \
  engine-tests/RuleMatcherTest.java engine-tests/KeywordBatchParserTest.java \
  engine-tests/SocialSearchRulesTest.java engine-tests/TvDpadDecisionTest.java
java -cp .testbuild RuleMatcherTest
java -cp .testbuild KeywordBatchParserTest
java -cp .testbuild SocialSearchRulesTest
java -cp .testbuild TvDpadDecisionTest
rm -rf .testbuild
