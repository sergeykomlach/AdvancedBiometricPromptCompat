package com.kieronquinn.monetcompat_light.extensions

fun List<*>.deepEquals(other: List<*>) =
    this.size == other.size && this.mapIndexed { index, element -> element == other[index] }
        .all { it }