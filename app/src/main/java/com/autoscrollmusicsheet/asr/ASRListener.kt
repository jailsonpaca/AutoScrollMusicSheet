package com.autoscrollmusicsheet.asr

interface ASRListener {
    fun onResultReceived(result: String)
    fun onError(error: String)
}
