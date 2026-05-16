package com.delminiusapps.rideforge.features.home.di

import com.delminiusapps.rideforge.features.home.presentation.HomeViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val homeModule = module {
    viewModel { HomeViewModel(get(), get()) }
}
