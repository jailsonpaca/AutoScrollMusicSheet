package com.autoscrollmusicsheet.asr

interface IWhisperListener {
    fun onUpdateReceived(message: String)
    fun onResultReceived(result: String)
}
