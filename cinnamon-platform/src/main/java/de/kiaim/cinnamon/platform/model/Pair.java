package de.kiaim.cinnamon.platform.model;

/**
 * Record class to group two objects together
 * @param element0 First object of Type K
 * @param element1 Second object of Type V
 * @param <K> The Type of element0
 * @param <V> The Type of element1
 */
public record Pair<K, V>(K element0, V element1) {

	/**
	 * Builder method to create a new Pair object.
	 * For compatibility with other Pari libraries.
	 *
	 * @param element0 First object of Type K
	 * @param element1 Second object of Type V
	 * @return A new Pair object
	 * @param <K> Type of the first object
	 * @param <V> Type of the second object
	 */
	public static <K, V> Pair<K, V> of(K element0, V element1) {
		return new Pair<>(element0, element1);
	}
}
