package shufflingway;

enum CardState {
    ACTIVE,       // card is undulled and can act
    DULL,       // card is dulled and cannot act
    BRAVE_ATTACKED // Brave Forward: attacked this turn but stays active
}
