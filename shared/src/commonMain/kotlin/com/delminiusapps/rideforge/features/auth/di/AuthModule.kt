package com.delminiusapps.rideforge.features.auth.di

import com.delminiusapps.rideforge.features.auth.presentation.LoginViewModel
import com.delminiusapps.rideforge.features.auth.presentation.RegisterViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val authModule = module {
    viewModel { LoginViewModel(get(), get()) }
    viewModel { RegisterViewModel(get(), get()) }
}
