package bd.safeplay

/** Starter list; production builds should use a trusted, regularly reviewed feed. */
object Blocklist {
    private val blockedDomains = setOf(
        "1xbet.com", "22bet.com", "bet365.com", "betway.com",
        "dafabet.com", "melbet.com", "mostbet.com", "parimatch.com",
        "stake.com", "bc.game", "casino.com", "888casino.com", "pokerstars.com"
    )

    fun isBlocked(domain: String): Boolean {
        val value = domain.trim().trimEnd('.').lowercase()
        return blockedDomains.any { value == it || value.endsWith(".$it") }
    }
}
