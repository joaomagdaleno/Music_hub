package com.joaomagdaleno.music_hub.di

import com.joaomagdaleno.music_hub.data.providers.InternalDownloadProvider
import com.joaomagdaleno.music_hub.download.DownloadWorker
import com.joaomagdaleno.music_hub.download.Downloader
import com.joaomagdaleno.music_hub.download.db.DownloadDatabase
import com.joaomagdaleno.music_hub.playback.PlayerService
import com.joaomagdaleno.music_hub.playback.PlayerState
import com.joaomagdaleno.music_hub.ui.common.SnackBarHandler
import com.joaomagdaleno.music_hub.ui.common.UiViewModel
import com.joaomagdaleno.music_hub.ui.download.DownloadViewModel
import com.joaomagdaleno.music_hub.ui.feed.FeedViewModel
import com.joaomagdaleno.music_hub.ui.main.search.SearchViewModel
import com.joaomagdaleno.music_hub.ui.media.MediaViewModel
import com.joaomagdaleno.music_hub.ui.player.PlayerViewModel
import com.joaomagdaleno.music_hub.ui.player.more.info.TrackInfoViewModel
import com.joaomagdaleno.music_hub.ui.player.more.lyrics.LyricsViewModel
import com.joaomagdaleno.music_hub.ui.playlist.create.CreatePlaylistViewModel
import com.joaomagdaleno.music_hub.ui.playlist.delete.DeletePlaylistViewModel
import com.joaomagdaleno.music_hub.ui.playlist.edit.EditPlaylistViewModel
import com.joaomagdaleno.music_hub.common.models.EchoMediaItem
import com.joaomagdaleno.music_hub.data.repository.MusicRepository
import com.joaomagdaleno.music_hub.ui.playlist.save.SaveToPlaylistViewModel
import com.joaomagdaleno.music_hub.utils.ContextUtils
import org.koin.android.ext.koin.androidApplication
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

object DI {

    private val baseModule = module {
        single { ContextUtils.getSettings(androidApplication()) }
        singleOf(::App)
        single { com.joaomagdaleno.music_hub.data.providers.LocalSource(androidApplication()) }
        single { InternalDownloadProvider(androidApplication()) }
        singleOf(::MusicRepository)
        single { com.joaomagdaleno.music_hub.data.db.MusicDatabase.create(androidApplication()) }
    }

    private val coreModule = module {
        includes(baseModule)
    }

    private val downloadModule = module {
        includes(coreModule)
        singleOf(DownloadDatabase::create)
        singleOf(::Downloader)
        workerOf(::DownloadWorker)
    }

    private val playerModule = module {
        includes(coreModule)
        singleOf(PlayerService::getCache)
        single { PlayerState() }
    }

    private val uiModules = module {
        singleOf(::SnackBarHandler)
        viewModelOf(::UiViewModel)

        viewModelOf(::PlayerViewModel)
        viewModelOf(::LyricsViewModel)
        viewModelOf(::TrackInfoViewModel)


        viewModelOf(::FeedViewModel)
        viewModelOf(::SearchViewModel)
        viewModel { (loadFeeds: Boolean, origin: String, item: EchoMediaItem, loaded: Boolean) ->
            MediaViewModel(get(), get(), get(), loadFeeds, origin, item, loaded)
        }

        viewModelOf(::CreatePlaylistViewModel)
        viewModelOf(::DeletePlaylistViewModel)
        viewModelOf(::SaveToPlaylistViewModel)
        viewModelOf(::EditPlaylistViewModel)

        viewModelOf(::DownloadViewModel)
    }

    val appModule = module {
        includes(baseModule)
        includes(coreModule)
        includes(playerModule)
        includes(downloadModule)
        includes(uiModules)
    }
}
