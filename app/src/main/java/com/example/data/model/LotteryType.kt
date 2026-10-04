package com.example.data.model

enum class LotteryType(
    val code: String,
    val displayName: String,
    val shortName: String,
    val totalNumbers: Int,
    val pickCount: Int,
    val hasBonusBall: Boolean,
    val defaultFileName: String,
    val rawGithubUrl: String,
    val blobGithubUrl: String
) {
    MEGA_645(
        code = "MEGA_645",
        displayName = "Mega 6/45",
        shortName = "6/45",
        totalNumbers = 45,
        pickCount = 6,
        hasBonusBall = false,
        defaultFileName = "power645.jsonl",
        rawGithubUrl = "https://raw.githubusercontent.com/thanhnhu/vietlott/master/data/power645.jsonl",
        blobGithubUrl = "https://github.com/thanhnhu/vietlott/blob/master/data/power645.jsonl"
    ),
    POWER_655(
        code = "POWER_655",
        displayName = "Power 6/55",
        shortName = "6/55",
        totalNumbers = 55,
        pickCount = 6,
        hasBonusBall = true,
        defaultFileName = "power655.jsonl",
        rawGithubUrl = "https://raw.githubusercontent.com/thanhnhu/vietlott/master/data/power655.jsonl",
        blobGithubUrl = "https://github.com/thanhnhu/vietlott/blob/master/data/power655.jsonl"
    );

    val theoreticalProbability: Double
        get() = pickCount.toDouble() / totalNumbers.toDouble()

    companion object {
        fun fromCode(code: String): LotteryType =
            entries.find { it.code.equals(code, ignoreCase = true) } ?: MEGA_645
    }
}
