package com.delminiusapps.rideforge.features.plans.di

import com.delminiusapps.rideforge.domain.usecase.GetPlanWorkoutsUseCase
import com.delminiusapps.rideforge.features.plans.presentation.PlanWorkoutsViewModel
import com.delminiusapps.rideforge.features.plans.presentation.PlansViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val plansModule = module {
    factoryOf(::GetPlanWorkoutsUseCase)
    viewModel { PlansViewModel(get()) }
    viewModel { (planId: String) -> PlanWorkoutsViewModel(get(), get(), planId) }
}
