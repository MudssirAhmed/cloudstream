package com.lagradost.cloudstream3.movieproviders

import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.get
import com.lagradost.cloudstream3.network.post
import com.lagradost.cloudstream3.network.text
import com.lagradost.cloudstream3.network.url
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import okio.Buffer
import org.jsoup.Jsoup

// referer = https://vf-film.org, USERAGENT ALSO REQUIRED
class VfFilm : MainAPI() {
    override val mainUrl: String
        get() = "https://vf-film.org"
    override val name: String
        get() = "vf-film.org"

    override val lang: String = "fr"

    override val hasQuickSearch: Boolean
        get() = false

    override val hasMainPage: Boolean
        get() = false

    override val hasChromecastSupport: Boolean
        get() = false

    override val supportedTypes: Set<TvType>
        get() = setOf(
            TvType.Movie,
        )


    override fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val response = get(url).text
        val document = Jsoup.parse(response)
        val items = document.select("ul.MovieList > li > article > a")
        if (items.isNullOrEmpty()) return ArrayList()

        val returnValue = ArrayList<SearchResponse>()
        for (item in items) {
            val href = item.attr("href")

            val poster = item.selectFirst("> div.Image > figure > img").attr("src").replace("//image", "https://image")

            val name = item.selectFirst("> h3.Title").text()

            val year = item.selectFirst("> span.Year").text()?.toIntOrNull()

            returnValue.add(MovieSearchResponse(name, href, this.name, TvType.Movie, poster, year))
        }
        return returnValue
    }


    override fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (data == "") return false
        callback.invoke(
            ExtractorLink(
                this.name,
                this.name,
                data,
                "",
                Qualities.P720.value,
                false
            )
        )
        return true
    }


    private fun getDirect(original: String): String {  // original data, https://vf-film.org/?trembed=1&trid=55313&trtype=1 for example
        val response = get(original).text
        val url = "iframe .*src=\\\"(.*?)\\\"".toRegex().find(response)?.groupValues?.get(1).toString()  // https://vudeo.net/embed-uweno86lzx8f.html for example
        val vudoResponse = get(url).text
        val document = Jsoup.parse(vudoResponse)
        val vudoUrl = Regex("sources: \\[\\\"(.*?)\\\"\\]").find(document.html())?.groupValues?.get(1).toString()  // direct mp4 link, https://m11.vudeo.net/2vp3ukyw2avjdohilpebtzuct42q5jwvpmpsez3xjs6d7fbs65dpuey2rbra/v.mp4 for exemple
        return vudoUrl
    }



    override fun load(url: String): LoadResponse {
        val response = get(url).text
        val document = Jsoup.parse(response)
        val title = document?.selectFirst("div.SubTitle")?.text()
            ?: throw ErrorLoadingException("Service might be unavailable")
        

        val year = document.select("span.Date").text()?.toIntOrNull()

        val rating = document.select("span.AAIco-star").text()

        val duration = document.select("span.Time").text()?.toIntOrNull()

        val poster = document.selectFirst("div.Image > figure > img").attr("src").replace("//image", "https://image")

        val descript = document.selectFirst("div.Description > p").text()


        val players = document.select("ul.TPlayerNv > li")
        var number_player = 0
        var found = false
        for (player in players) {
            if (player.selectFirst("> span").text() == "Vudeo") {
                found = true
                break
            } else {
                number_player += 1
            }
        }
        if (found == false) {
            number_player = 0
        }
        val i = number_player.toString()
        val trid = Regex("iframe .*trid=(.*?)&").find(document.html())?.groupValues?.get(1)

        val data = getDirect("https://vf-film.org/?trembed=$i&trid=$trid&trtype=1")


        return MovieLoadResponse(
            title,
            url,
            this.name,
            TvType.Movie,
            data,
            poster,
            year,
            descript,
            rating,
            duration
        )
    }
}
