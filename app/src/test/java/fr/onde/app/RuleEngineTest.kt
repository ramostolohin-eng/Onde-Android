package fr.onde.app

import org.junit.Assert.*
import org.junit.Test

class RuleEngineTest {
    private val apps = listOf(AppIdentity("video", 1001), AppIdentity("mail", 1002))
    @Test fun defaultRulesAllowEverything() {
        NetworkKind.entries.forEach { assertTrue(RuleEngine.blockedPackages(apps, emptyMap(), it).isEmpty()) }
    }
    @Test fun mobileBlockDoesNotBlockWifi() {
        val rules = mapOf("video" to Rule(wifi = true, mobile = false))
        assertEquals(setOf("video"), RuleEngine.blockedPackages(apps, rules, NetworkKind.MOBILE))
        assertTrue(RuleEngine.blockedPackages(apps, rules, NetworkKind.WIFI).isEmpty())
    }
    @Test fun wifiBlockDoesNotBlockMobile() {
        val rules = mapOf("mail" to Rule(wifi = false))
        assertEquals(setOf("mail"), RuleEngine.blockedPackages(apps, rules, NetworkKind.WIFI))
        assertTrue(RuleEngine.blockedPackages(apps, rules, NetworkKind.MOBILE).isEmpty())
    }
    @Test fun unknownTransportUsesStricterRule() {
        val rules = mapOf("video" to Rule(mobile = false), "mail" to Rule(wifi = false))
        assertEquals(setOf("video", "mail"), RuleEngine.blockedPackages(apps, rules, NetworkKind.OTHER))
    }
    @Test fun sharedUidCannotBeIsolated() {
        val shared = apps + AppIdentity("video.helper", 1001)
        val rules = mapOf("video" to Rule(mobile = false))
        assertEquals(setOf("video", "video.helper"),
            RuleEngine.blockedPackages(shared, rules, NetworkKind.MOBILE))
    }
    @Test fun staleRuleForRemovedAppIsIgnored() {
        assertTrue(RuleEngine.blockedPackages(apps,
            mapOf("removed" to Rule(false, false)), NetworkKind.MOBILE).isEmpty())
    }
    @Test fun emptyInstalledListNeverBlocksEverything() {
        assertTrue(RuleEngine.blockedPackages(emptyList(), emptyMap(), NetworkKind.WIFI).isEmpty())
    }
    @Test fun ruleDecisionMatrix() {
        val rule = Rule(wifi = true, mobile = false)
        assertTrue(rule.allowed(NetworkKind.WIFI))
        assertFalse(rule.allowed(NetworkKind.MOBILE))
        assertFalse(rule.allowed(NetworkKind.OTHER))
    }
    @Test fun fullyBlockedAppIsBlockedOnEveryNetwork() {
        val rules = mapOf("video" to Rule(wifi = false, mobile = false))
        NetworkKind.entries.forEach {
            assertEquals(setOf("video"), RuleEngine.blockedPackages(apps, rules, it))
        }
    }
    @Test fun sharedUidFollowsRuleOfAnyMember() {
        val shared = apps + AppIdentity("video.helper", 1001)
        val rules = mapOf("video.helper" to Rule(wifi = false))
        assertEquals(setOf("video", "video.helper"),
            RuleEngine.blockedPackages(shared, rules, NetworkKind.WIFI))
        assertTrue(RuleEngine.blockedPackages(shared, rules, NetworkKind.MOBILE).isEmpty())
    }
}
