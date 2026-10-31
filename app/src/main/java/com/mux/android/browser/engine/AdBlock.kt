package com.mux.android.browser.engine

/** Small built-in ad / tracker host list + cosmetic CSS (hides empty ad boxes). */
object AdBlock {
    private val hosts: HashSet<String> = HashSet(
        """
        doubleclick.net googlesyndication.com googleadservices.com google-analytics.com adservice.google.com
        pagead2.googlesyndication.com 2mdn.net adnxs.com adsrvr.org taboola.com outbrain.com criteo.com criteo.net
        scorecardresearch.com quantserve.com quantcount.com hotjar.com amazon-adsystem.com moatads.com
        rubiconproject.com pubmatic.com openx.net adform.net smartadserver.com casalemedia.com yieldmo.com
        serving-sys.com advertising.com adcolony.com applovin.com mopub.com inmobi.com chartboost.com vungle.com
        ironsrc.com supersonicads.com popads.net popcash.net propellerads.com exoclick.com juicyads.com
        trafficjunky.net adsterra.com revcontent.com mgid.com zedo.com bidswitch.net lijit.com sharethrough.com
        teads.tv adroll.com bluekai.com krxd.net demdex.net everesttech.net mathtag.com agkn.com tapad.com
        rlcdn.com 33across.com id5-sync.com liadm.com bounceexchange.com mixpanel.com fullstory.com mouseflow.com
        crazyegg.com luckyorange.com clicktale.net inspectlet.com statcounter.com chartbeat.com chartbeat.net
        parsely.com sitescout.com spotxchat.com spotx.tv spotxcdn.com springserve.com tremorhub.com
        adtechus.com adtech.de contextweb.com bidtheatre.com improvedigital.com indexww.com sovrn.com
        undertone.com conversantmedia.com media.net adsafeprotected.com doubleverify.com iasds01.com
        flashtalking.com eyeota.net exelator.com nr-data.net outbrainimg.com zemanta.com nativeads.com
        adblade.com content.ad plista.com ligatus.com yandex.ru/ads an.yandex.ru mc.yandex.ru
        ads.twitter.com analytics.twitter.com ads-api.twitter.com ads.linkedin.com px.ads.linkedin.com
        ads.pinterest.com ads.tiktok.com analytics.tiktok.com log.byteoversea.com
        pixel.facebook.com an.facebook.com ads.yahoo.com adserver.yahoo.com geo.yahoo.com
        ad.doubleclick.net stats.g.doubleclick.net securepubads.g.doubleclick.net
        track.adform.net adsymptotic.com bat.bing.com clarity.ms
        popunder.net clickadu.com hilltopads.net richpush.co evadav.com adcash.com clickaine.com
        onclickads.net onclkds.com a-ads.com bitmedia.io coinzilla.com cointraffic.io
        """.trim().split(Regex("\\s+")).filter { it.isNotEmpty() && !it.contains('/') }
    )

    /** true when [host] or any parent domain is on the list. */
    fun isBlocked(host: String): Boolean {
        var h = host.lowercase()
        while (h.isNotEmpty()) {
            if (hosts.contains(h)) return true
            val dot = h.indexOf('.')
            if (dot < 0) return false
            h = h.substring(dot + 1)
        }
        return false
    }

    const val COSMETIC_JS = """(function(){try{if(document.getElementById('__hud_ab'))return;var s=document.createElement('style');s.id='__hud_ab';s.textContent='ins.adsbygoogle,.adsbygoogle,[id^="google_ads_"],[id^="div-gpt-ad"],iframe[src*="doubleclick.net"],iframe[src*="googlesyndication"],a[href*="doubleclick.net"],.ad-banner,.ad-container,.advert,.advertisement,[class*="sponsored-ad"],[data-ad-slot],[data-google-query-id]{display:none!important}';(document.head||document.documentElement).appendChild(s)}catch(e){}})();"""

    const val DARK_JS = """(function(){try{if(document.getElementById('__hud_dark'))return;var s=document.createElement('style');s.id='__hud_dark';s.textContent='html{filter:invert(1) hue-rotate(180deg)!important;background:#fff}img,video,canvas,picture,svg image{filter:invert(1) hue-rotate(180deg)!important}';(document.head||document.documentElement).appendChild(s)}catch(e){}})();"""
}
