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

assert "RADIO_CANDIDATE_LIMIT" not in planner
assert ".filter { id(it) !in usedIds }.distinctBy(id)" in planner, "Exclude original and used tracks before selection"
assert "best?.track ?: b.firstOrNull()" in planner
assert "fun <T> selectForAllPairs(" in planner
assert "analyzePair(" in progressive
assert "val neighbours =" not in progressive, "Do not borrow other pairs' radio for progressive loading"
assert "buildEnhancedBatchProgressively(likedBatch, songRepository, excludedIds)" in prefetch, "Prefetch must use the SAME pair selector"
assert "if (firstDecision.matches.isEmpty())" in progressive
assert "ENHANCED_RADIO_MAX_TRACKS = 100" in progressive
assert "ENHANCED_RADIO_MAX_PARALLEL_PAIRS = 4" in progressive
assert "Semaphore(ENHANCED_RADIO_MAX_PARALLEL_REQUESTS)" in progressive
assert "async(start = CoroutineStart.UNDISPATCHED)" in progressive
assert "orderedExclusions[index].await()" in progressive
assert "if (potential.matches.isEmpty()) loadExtra()" in progressive
assert "withTimeoutOrNull" not in prefetch
assert "EnhancedRadioMetadataDiagnostics.compare(" in prefetch
assert "EnhancedRadioMetadataDiagnostics" not in progressive, "Metadata must never alter radio scheduling or selection"
assert "eligibleRadioA = allA.filter { id(it) !in used }.distinctBy(id)" in progressive
assert "body = forkRelease.notes," in shared, "Fork update dialog should show clean release body"
assert "body = forkRelease.notes +" not in shared, "No literal escaped newline or duplicate changelog link"
assert 'isRelevanceV2Experiment -> ".dev.relevance2"' in gradle
assert "isRelevanceV2Experiment" in gradle
print("PASS: full eligible returned radio lists with B-only fallback in both loading paths and clean update text")
