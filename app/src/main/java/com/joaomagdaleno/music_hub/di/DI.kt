package com.joaomagdaleno.music_hub.di

import com.joaomagdaleno.music_hub.download.DownloadWorker
import com.joaomagdaleno.music_hub.download.Downloader
import com.joaomagdaleno.music_hub.download.db.DownloadDatabase
import com.joaomagdaleno.music_hub.extensions.ExtensionLoader
import com.joaomagdaleno.music_hub.playback.PlayerService
import com.joaomagdaleno.music_hub.playback.PlayerState
import com.joaomagdaleno.music_hub.ui.common.SnackBarHandler
import com.joaomagdaleno.music_hub.ui.common.UiViewModel
import com.joaomagdaleno.music_hub.ui.download.DownloadViewModel
import com.joaomagdaleno.music_hub.ui.extensions.ExtensionInfoViewModel
import com.joaomagdaleno.music_hub.ui.extensions.ExtensionsViewModel
import com.joaomagdaleno.music_hub.ui.extensions.add.AddViewModel
import com.joaomagdaleno.music_hub.ui.extensions.login.LoginUserListViewModel
import com.joaomagdaleno.music_hub.ui.extensions.login.LoginViewModel
import com.joaomagdaleno.music_hub.ui.feed.FeedViewModel
import com.joaomagdaleno.music_hub.ui.main.search.SearchViewModel
import com.joaomagdaleno.music_hub.ui.media.MediaViewModel
import com.joaomagdaleno.music_hub.ui.player.PlayerViewModel
import com.joaomagdaleno.music_hub.ui.player.more.info.TrackInfoViewModel
import com.joaomagdaleno.music_hub.ui.player.more.lyrics.LyricsViewModel
import com.joaomagdaleno.music_hub.ui.playlist.create.CreatePlaylistViewModel
import com.joaomagdaleno.music_hub.ui.playlist.delete.DeletePlaylistViewModel
import com.joaomagdaleno.music_hub.ui.playlist.edit.EditPlaylistViewModel
import com.joaomagdaleno.music_hub.ui.playlist.save.SaveToPlaylistViewModel
import com.joaomagdaleno.music_hub.utils.ContextUtils.getSettings
import org.koin.android.ext.koin.androidApplication
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

object DI {

    private val baseModule = module {
        single { androidApplication().getSettings() }
        singleOf(::App)
    }

    private val extensionModule = module {
        includes(baseModule)
        singleOf(::ExtensionLoader)
    }

    private val downloadModule = module {
        includes(extensionModule)
        singleOf(DownloadDatabase::create)
        singleOf(::Downloader)
        workerOf(::DownloadWorker)
    }

    private val playerModule = module {
        includes(extensionModule)
        singleOf(PlayerService::getCache)
        single { PlayerState() }
    }

    private val uiModules = module {
        singleOf(::SnackBarHandler)
        viewModelOf(::UiViewModel)

        viewModelOf(::PlayerViewModel)
        viewModelOf(::LyricsViewModel)
        viewModelOf(::TrackInfoViewModel)

        viewModelOf(::ExtensionsViewModel)
        viewModelOf(::ExtensionInfoViewModel)
        viewModelOf(::LoginUserListViewModel)
        viewModelOf(::AddViewModel)
        viewModelOf(::LoginViewModel)

        viewModelOf(::FeedViewModel)
        viewModelOf(::SearchViewModel)
        viewModelOf(::MediaViewModel)

        viewModelOf(::CreatePlaylistViewModel)
        viewModelOf(::DeletePlaylistViewModel)
        viewModelOf(::SaveToPlaylistViewModel)
        viewModelOf(::EditPlaylistViewModel)

        viewModelOf(::DownloadViewModel)
    }

    val appModule = module {
        includes(baseModule)
        includes(extensionModule)
        includes(playerModule)
        includes(downloadModule)
        includes(uiModules)
    }
}