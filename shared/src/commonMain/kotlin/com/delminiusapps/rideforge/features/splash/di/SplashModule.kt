package com.delminiusapps.rideforge.features.splash.di

import com.delminiusapps.rideforge.features.splash.presentation.SplashViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val splashModule = module {
    viewModel { SplashViewModel(get()) }
}
