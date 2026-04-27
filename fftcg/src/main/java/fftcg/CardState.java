package fftcg;

enum CardState {
    ACTIVE,       // card is undulled and can act
    DULLED,       // card is dulled and cannot act
    BRAVE_ATTACKED // Brave Forward: attacked this turn but stays active
}
