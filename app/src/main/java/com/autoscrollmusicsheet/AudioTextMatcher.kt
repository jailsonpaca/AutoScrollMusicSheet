package com.autoscrollmusicsheet

import java.util.Locale

class AudioTextMatcher {
    companion object {
        const val MATCH_THRESHOLD = 0.7
    }

    data class MatchResult(
        val position: Int,
        val score: Double
    )

    /**
     * Preprocesses text by removing punctuation, converting to lowercase,
     * and splitting into words
     */
    fun preprocessText(text: String): List<String> {
        return text.lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9 ]"), "")
            .split(" ")
            .filter { it.isNotEmpty() }
    }

    /**
     * Finds the best matching position in the content for the recognized words
     */
    fun findBestMatch(recognizedWords: List<String>, content: List<String>): MatchResult {
        var bestPosition = 0
        var bestScore = 0.0
        val windowSize = 40

        // Convert content lines to single string and preprocess
        val contentText = content.joinToString(" ")
        val contentWords = preprocessText(contentText)

        // Slide through content with window
        for (i in 0..contentWords.size - windowSize) {
            val windowWords = contentWords.subList(i, i + windowSize)
            val score = calculateMatchScore(recognizedWords, windowWords)

            if (score > bestScore) {
                bestScore = score
                bestPosition = findLinePosition(i, content)
            }
        }

        return MatchResult(bestPosition, bestScore)
    }

    /**
     * Calculates match score between recognized words and content window
     */
    private fun calculateMatchScore(recognized: List<String>, content: List<String>): Double {
        if (recognized.isEmpty() || content.isEmpty()) return 0.0

        var totalScore = 0.0
        val maxLen = minOf(recognized.size, content.size)

        for (i in 0 until maxLen) {
            val recognizedWord = recognized[i]
            val contentWord = content[i]

            // Combine exact match and Levenshtein similarity
            val exactMatch = if (recognizedWord == contentWord) 1.0 else 0.0
            val levenshteinScore = 1.0 - (levenshteinDistance(recognizedWord, contentWord).toDouble() /
                    maxOf(recognizedWord.length, contentWord.length))
            val phoneticScore = if (soundex(recognizedWord) == soundex(contentWord)) 1.0 else 0.0

            // Weighted combination of matching techniques
            totalScore += (0.4 * exactMatch + 0.4 * levenshteinScore + 0.2 * phoneticScore)
        }

        return totalScore / maxLen
    }

    /**
     * Calculates Levenshtein distance between two strings
     */
    private fun levenshteinDistance(str1: String, str2: String): Int {
        val dp = Array(str1.length + 1) { IntArray(str2.length + 1) }

        for (i in 0..str1.length) dp[i][0] = i
        for (j in 0..str2.length) dp[0][j] = j

        for (i in 1..str1.length) {
            for (j in 1..str2.length) {
                dp[i][j] = if (str1[i-1] == str2[j-1]) {
                    dp[i-1][j-1]
                } else {
                    minOf(
                        dp[i-1][j] + 1,    // deletion
                        dp[i][j-1] + 1,    // insertion
                        dp[i-1][j-1] + 1   // substitution
                    )
                }
            }
        }

        return dp[str1.length][str2.length]
    }

    /**
     * Implements Soundex algorithm for phonetic matching
     */
    private fun soundex(str: String): String {
        if (str.isEmpty()) return ""

        val soundexMap = mapOf(
            'b' to '1', 'f' to '1', 'p' to '1', 'v' to '1',
            'c' to '2', 'g' to '2', 'j' to '2', 'k' to '2', 'q' to '2', 's' to '2', 'x' to '2', 'z' to '2',
            'd' to '3', 't' to '3',
            'l' to '4',
            'm' to '5', 'n' to '5',
            'r' to '6'
        )

        val result = StringBuilder().append(str[0].uppercaseChar())
        var previous = soundexMap[str[0].lowercaseChar()] ?: '0'

        for (i in 1 until str.length) {
            val current = soundexMap[str[i].lowercaseChar()] ?: '0'
            if (current != '0' && current != previous) {
                result.append(current)
                if (result.length == 4) break
            }
            previous = current
        }

        while (result.length < 4) result.append('0')

        return result.toString()
    }

    /**
     * Finds the corresponding line position in original content
     */
    private fun findLinePosition(wordIndex: Int, content: List<String>): Int {
        var currentWordCount = 0
        for (i in content.indices) {
            val lineWords = preprocessText(content[i])
            currentWordCount += lineWords.size
            if (currentWordCount > wordIndex) {
                return i
            }
        }
        return content.size - 1
    }
}