package com.delminiusapps.rideforge.features.trainer.di

import com.delminiusapps.rideforge.presentation.trainer.TrainerViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val trainerModule = module {
    viewModel { TrainerViewModel(get(), get()) }
}
