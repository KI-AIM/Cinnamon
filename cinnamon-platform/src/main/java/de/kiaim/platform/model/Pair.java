package de.kiaim.platform.model;

/**
 * Record class to group two objects together
 * @param element0 First object of Type K
 * @param element1 Second object of Type V
 * @param <K> The Type of element0
 * @param <V> The Type of element1
 */
public record Pair<K, V>(K element0, V element1) {
}

