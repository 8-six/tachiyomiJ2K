package eu.kanade.tachiyomi.ui.setting.track

import android.net.Uri

/** Where ComicK sends the user back to after they signed in, see [eu.kanade.tachiyomi.data.track.comick.ComicKApi.REDIRECT_URI]. */
class ComicKLoginActivity : BaseOAuthLoginActivity() {
    override fun handleResult(data: Uri?) {
        val state = data?.getQueryParameter("state")
        val code = data?.getQueryParameter("code")
        if (code != null && state != null && trackManager.comicK.verifyOAuthState(state)) {
            login { trackManager.comicK.login(code) }
        } else {
            trackManager.comicK.logout()
            returnToSettings()
        }
    }
}
