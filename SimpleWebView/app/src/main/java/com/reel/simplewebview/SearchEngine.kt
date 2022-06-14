package com.reel.simplewebview

data class SearchEngine(
    var name: String,
    var url: String,
    var selected: Boolean = false
)