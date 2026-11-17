package com.santi.stickers.x

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec 007: RN-1 (link → número), token y RN-3/RN-4 (qué video se baja), con respuestas reales recortadas. */
class XLinksTest {

    private fun fixture(name: String) = javaClass.getResource("/x/$name")!!.readText()

    // --- RN-1 ---

    @Test
    fun `RN-1 links de x y twitter, con lo que venga despues`() {
        assertEquals("2121685501430104275", XLinks.statusId("https://x.com/yerayherr/status/2121685501430104275?a=LfZv8Q7px8LM7rru2tQ&s=08"))
        assertEquals("20", XLinks.statusId("https://twitter.com/jack/status/20"))
        assertEquals("20", XLinks.statusId("https://mobile.twitter.com/jack/status/20/photo/1"))
        assertEquals("20", XLinks.statusId("http://www.x.com/jack/status/20/video/1"))
        assertEquals("20", XLinks.statusId("https://x.com/i/status/20"))
        assertEquals("20", XLinks.statusId("HTTPS://X.COM/Jack/STATUS/20"))
    }

    @Test
    fun `RN-1 el link puede venir dentro de un texto y se usa el primero de X`() {
        assertEquals("20", XLinks.statusId("Mirá esto https://x.com/jack/status/20 jaja https://x.com/a/status/21"))
        assertEquals("21", XLinks.statusId("https://example.com/status/5 y https://x.com/a/status/21"))
    }

    @Test
    fun `RN-2 lo que no es un link de un tweet de X da null`() {
        assertNull(XLinks.statusId("hola"))
        assertNull(XLinks.statusId("https://x.com/jack"))
        assertNull(XLinks.statusId("https://notx.com/jack/status/20"))
        assertNull(XLinks.statusId("https://x.com.evil.com/jack/status/20"))
        assertNull(XLinks.statusId("https://instagram.com/p/20"))
    }

    // --- Token (como el código de los tweets incrustados; valores sacados de Node) ---

    @Test
    fun `token igual al de javascript`() {
        val expected = mapOf(
            "20" to "6dq1a2xwd93",
            "2121685501430104275" to "555gz68wmsk",
            "2122576566987489513" to "5589r5f5yab",
            "2039473910987534599" to "4xz72cimlmx",
            "2094024294988701975" to "52qkkgig8e7",
            "1" to "bhi2ay3f28n",
            "123456789" to "ng9m4sqe3h",
            "1899283195142545585" to "4lqrv9y2zdl",
            "1950511829131600322" to "4q7pov9n15m",
        )
        expected.forEach { (id, token) -> assertEquals("id $id", token, XLinks.syndicationToken(id)) }
    }

    // --- RN-3, RN-4, D4 ---

    @Test
    fun `RN-4 video, el mejor MP4 de hasta 720p`() {
        val pick = XLinks.pickVideo(fixture("video.json")) as XVideoPick.Video
        assertEquals("https://video.twimg.com/amplify_video/2121685480903262208/vid/avc1/976x720/aarec9rSM1l-lzUr.mp4", pick.url)
        assertFalse(pick.isGif)
        assertFalse(pick.more)
    }

    @Test
    fun `RN-4 con 1080p y 4K se queda con 720p`() {
        val pick = XLinks.pickVideo(fixture("video-4k.json")) as XVideoPick.Video
        assertTrue(pick.url, pick.url.contains("/1280x720/"))
    }

    @Test
    fun `RN-3 GIF, su unico MP4`() {
        val pick = XLinks.pickVideo(fixture("gif.json")) as XVideoPick.Video
        assertEquals("https://video.twimg.com/tweet_video/HXO7XnoX0AAQqjK.mp4", pick.url)
        assertTrue(pick.isGif)
    }

    @Test
    fun `RN-3 tweet sin video ni GIF`() {
        assertEquals(XVideoPick.NoVideo, XLinks.pickVideo(fixture("sin-media.json")))
        assertEquals(XVideoPick.NoVideo, XLinks.pickVideo("""{"mediaDetails":[{"type":"photo"}]}"""))
    }

    @Test
    fun `D4 con varios videos usa el primero y lo marca`() {
        val two = """{"mediaDetails":[{"type":"photo"},
            {"type":"video","video_info":{"variants":[{"bitrate":100,"content_type":"video/mp4","url":"https://video.twimg.com/a/vid/avc1/640x360/x.mp4"}]}},
            {"type":"animated_gif","video_info":{"variants":[{"bitrate":0,"content_type":"video/mp4","url":"https://video.twimg.com/tweet_video/b.mp4"}]}}]}"""
        val pick = XLinks.pickVideo(two) as XVideoPick.Video
        assertEquals("https://video.twimg.com/a/vid/avc1/640x360/x.mp4", pick.url)
        assertTrue(pick.more)
    }

    @Test
    fun `RN-4 si todos pasan de 720p, el de menor resolucion`() {
        val json = """{"mediaDetails":[{"type":"video","video_info":{"variants":[
            {"bitrate":9,"content_type":"video/mp4","url":"https://video.twimg.com/a/vid/avc1/3840x2160/big.mp4"},
            {"bitrate":5,"content_type":"video/mp4","url":"https://video.twimg.com/a/vid/avc1/1920x1080/mid.mp4"}]}}]}"""
        assertTrue((XLinks.pickVideo(json) as XVideoPick.Video).url.endsWith("/mid.mp4"))
    }

    @Test
    fun `seguridad solo se aceptan MP4 de video twimg com por https`() {
        val json = """{"mediaDetails":[{"type":"video","video_info":{"variants":[
            {"bitrate":9,"content_type":"video/mp4","url":"https://evil.com/vid/avc1/640x360/x.mp4"},
            {"bitrate":8,"content_type":"video/mp4","url":"http://video.twimg.com/vid/avc1/640x360/y.mp4"}]}}]}"""
        assertEquals(XVideoPick.NoVideo, XLinks.pickVideo(json))
    }

    @Test
    fun `RN-6 una respuesta que no se entiende`() {
        assertEquals(XVideoPick.Unreadable, XLinks.pickVideo("<!DOCTYPE html><html>"))
        assertEquals(XVideoPick.Unreadable, XLinks.pickVideo("""{"error":"Bad request."}"""))
        assertEquals(XVideoPick.Unreadable, XLinks.pickVideo("[]"))
    }
}
