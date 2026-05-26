package com.delminiusapps.rideforge.features.trends.di

import com.delminiusapps.rideforge.features.trends.presentation.TrendsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val trendsModule = module {
    viewModel { TrendsViewModel(get(), get()) }
}
