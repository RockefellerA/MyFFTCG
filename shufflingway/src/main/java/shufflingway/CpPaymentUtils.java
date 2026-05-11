package shufflingway;

import java.util.Map;

/** Pure static utilities for CP element-assignment during card payment. */
class CpPaymentUtils {
	private CpPaymentUtils() {}

	/** Returns the first element of {@code source} that matches one of {@code playedElems}. */
	static String contributingElement(CardData source, String[] playedElems) {
		for (String pe : playedElems)
			if (source.containsElement(pe)) return pe;
		return playedElems[0];
	}

	/**
	 * Returns the element of {@code source} that has the largest remaining deficit
	 * ({@code required - alreadyPaid}), so multi-element payment cards fill whichever
	 * requirement is still most needed rather than always defaulting to the first match.
	 */
	static String contributingElement(CardData source, String[] playedElems,
			Map<String, Integer> cpByElem, Map<String, Integer> costByElem) {
		String best = null;
		int maxDeficit = Integer.MIN_VALUE;
		for (String pe : playedElems) {
			if (source.containsElement(pe)) {
				int deficit = costByElem.getOrDefault(pe, 0) - cpByElem.getOrDefault(pe, 0);
				if (deficit > maxDeficit) {
					maxDeficit = deficit;
					best = pe;
				}
			}
		}
		return best != null ? best : playedElems[0];
	}

	/** Returns true if {@code source} contains any element from {@code playedElems}. */
	static boolean matchesAnyElement(CardData source, String[] playedElems) {
		for (String pe : playedElems)
			if (source.containsElement(pe)) return true;
		return false;
	}
}
