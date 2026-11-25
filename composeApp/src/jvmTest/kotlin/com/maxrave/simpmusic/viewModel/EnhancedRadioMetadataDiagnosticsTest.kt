package com.maxrave.simpmusic.viewModel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnhancedRadioMetadataDiagnosticsTest {
    private fun track(id: String, title: String, artist: String = "Depeche Mode", rank: Int = 1, seconds: Int? = 255) =
        RadioMetadataCandidate(id, title, listOf(artist), seconds, rank)

    @Test
    fun presentationLabelsCaseAndPunctuationMatchButVideoIdIsNotCountedTwice() {
        val a = listOf(track("a", "Enjoy the Silence (Official Video)"), track("same", "Other"))
        val b = listOf(track("b", "ENJOY THE SILENCE!"), track("same", "Other"))
        val report = EnhancedRadioMetadataDiagnostics.compare(a, b)
        assertEquals(1, report.sameTitleCount)
        assertEquals(0, report.versionCount)
        assertEquals("b", report.sameTitleExamples.single().b.videoId)
    }

    @Test
    fun remixesAndRemastersAreOnlyPossibleVersionMatches() {
        val report = EnhancedRadioMetadataDiagnostics.compare(
            listOf(track("a", "Enjoy the Silence (2006 Remaster)")),
            listOf(track("b", "Enjoy the Silence (Techno Remix)"), track("c", "Enjoy the Silence")),
        )
        assertEquals(0, report.sameTitleCount)
        assertEquals(2, report.versionCount)
    }

    @Test
    fun sameRemixWithDifferentPresentationMatchesStrictly() {
        val report = EnhancedRadioMetadataDiagnostics.compare(
            listOf(track("a", "Enjoy the Silence (Techno Remix) [Official Audio]")),
            listOf(track("b", "Enjoy the Silence (Techno Remix)")),
        )
        assertEquals(1, report.sameTitleCount)
        assertEquals(0, report.versionCount)
    }

    @Test
    fun differentOrMissingArtistDoesNotMatchEvenWithSameTitle() {
        val report = EnhancedRadioMetadataDiagnostics.compare(
            listOf(track("a", "Enjoy the Silence"), track("missing", "Enjoy the Silence", "")),
            listOf(track("b", "Enjoy the Silence", "Lacuna Coil")),
        )
        assertEquals(0, report.sameTitleCount + report.versionCount)
    }

    @Test
    fun examplesLimitedToFivePerCategoryButCountsIncludeAllAndBestRanksFirst() {
        val report = EnhancedRadioMetadataDiagnostics.compare(
            listOf(track("a", "Song", rank = 2)),
            (1..8).map { track("strict$it", "Song", rank = 9 - it) } +
                (1..7).map { track("version$it", "Song (Live)", rank = 8 - it) },
        )
        assertEquals(8, report.sameTitleCount)
        assertEquals(7, report.versionCount)
        assertEquals(5, report.sameTitleExamples.size)
        assertEquals(5, report.versionExamples.size)
        assertEquals(1, report.sameTitleExamples.first().b.rank)
        assertEquals(1, report.versionExamples.first().b.rank)
    }

    @Test
    fun originalTitlesArtistsRanksAndDurationDifferenceAreVisible() {
        val report = EnhancedRadioMetadataDiagnostics.compare(
            listOf(track("a", "Song (Official Video)", rank = 17, seconds = 255)),
            listOf(track("b", "Song", rank = 54, seconds = 256)),
        )
        val text = report.sameTitleExamples.single().asText()
        assertTrue(text.contains("#17 Song (Official Video) — Depeche Mode [4:15]"))
        assertTrue(text.contains("#54 Song — Depeche Mode [4:16]"))
        assertTrue(text.contains("duration difference 1 s"))
    }

    @Test
    fun sharedArtistCanHaveDifferentSongsAndCountsDistinctTracks() {
        val a = listOf(track("a1", "One", "Dua Lipa", 2), track("a2", "Two", "DUA LIPA", 4),
            track("a1", "One", "Dua Lipa", 7))
        val b = listOf(track("b1", "Three", "dua lipa", 3), track("b2", "Four", "Dua Lipa", 8),
            track("b3", "Five", "Someone else", 1))
        val report = EnhancedRadioMetadataDiagnostics.compare(a, b)
        assertEquals(1, report.commonArtistCount)
        assertEquals(1, report.commonArtistDifferentSongCount)
        val artist = report.commonArtistExamples.single()
        assertEquals(2, artist.tracksA.size)
        assertEquals(2, artist.tracksB.size)
        assertEquals("b1", report.bestDifferentSongArtistExample?.bestDifferentSong?.b?.videoId)
        assertTrue(artist.asText().contains("#2 One"))
    }

    @Test
    fun sameSongOrRemixDoesNotQualifyAsDifferentBaseTitle() {
        val report = EnhancedRadioMetadataDiagnostics.compare(
            listOf(track("a", "Song"), track("same", "Song (Live)")),
            listOf(track("same", "Song (Live)"), track("b", "Song (Official Audio)")),
        )
        assertEquals(1, report.commonArtistCount)
        assertEquals(0, report.commonArtistDifferentSongCount)
        assertEquals(null, report.bestDifferentSongArtistExample)
    }

    @Test
    fun individualCollaboratorsCanMatchWithoutIdenticalArtistSets() {
        val report = EnhancedRadioMetadataDiagnostics.compare(
            listOf(RadioMetadataCandidate("a", "One", listOf("Artist A", "Artist B"), null, 1)),
            listOf(RadioMetadataCandidate("b", "Two", listOf("ARTIST B", "Artist C"), null, 2)),
        )
        assertEquals(1, report.commonArtistCount)
        assertEquals("Artist B", report.commonArtistExamples.single().name)
        assertEquals(1, report.commonArtistDifferentSongCount)
    }

    @Test
    fun sharedArtistExamplesAreLimitedButTotalCountsAll() {
        val a = (1..7).map { track("a$it", "One", "Artist $it", it) }
        val b = (1..7).map { track("b$it", "Two", "Artist $it", it) }
        val report = EnhancedRadioMetadataDiagnostics.compare(a, b)
        assertEquals(7, report.commonArtistCount)
        assertEquals(7, report.commonArtistDifferentSongCount)
        assertEquals(5, report.commonArtistExamples.size)
        assertEquals("Artist 1", report.commonArtistExamples.first().name)
    }
}
