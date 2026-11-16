#!/usr/bin/env python3
"""Guard the two user-requested changes in the Relevance V2 experiment.

This complements actual JVM tests and ensures that progressive startup and
near-end prefetch do not accidentally use different radio-selection rules.
"""
from pathlib import Path

root = Path("composeApp/src/commonMain/kotlin/com/maxrave/simpmusic/viewModel")
planner = (root / "EnhancedShufflePlanner.kt").read_text()
progressive = (root / "EnhancedProgressiveQueue.kt").read_text()
prefetch = (root / "EnhancedShuffleRecommendations.kt").read_text()
shared = (root / "SharedViewModel.kt").read_text()
gradle = Path("androidApp/build.gradle.kts").read_text()

assert "RADIO_CANDIDATE_LIMIT = 15" in planner
assert "return b.firstOrNull()" in planner
assert "fun <T> selectForAllPairs(" in planner
assert "selectForPair(" in progressive
assert "val neighbours =" not in progressive, "Do not borrow other pairs' radio for progressive loading"
assert "selectForAllPairs(" in prefetch, "Prefetch must use the SAME pair selector"
assert "body = forkRelease.notes," in shared, "Fork update dialog should show clean release body"
assert "body = forkRelease.notes +" not in shared, "No literal escaped newline or duplicate changelog link"
assert 'isRelevanceV2Experiment -> ".dev.relevance2"' in gradle
assert "isRelevanceV2Experiment" in gradle
print("PASS: top-15 B-only relevance in both loading paths and clean update text")
