package com.delminiusapps.rideforge.features.profile.di

import com.delminiusapps.rideforge.features.profile.presentation.ProfileViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val profileModule = module {
    viewModel { ProfileViewModel(get(), get(), get()) }
}
