package com.delminiusapps.rideforge.features.auth.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import com.delminiusapps.rideforge.presentation.components.AppCard
import com.delminiusapps.rideforge.presentation.components.PrimaryButton
import com.delminiusapps.rideforge.presentation.components.SecondaryButton
import com.delminiusapps.rideforge.theme.ForgeBackground
import com.delminiusapps.rideforge.theme.ForgeMuted
import com.delminiusapps.rideforge.theme.ForgeOrange
import org.jetbrains.compose.resources.stringResource
import rideforge.composeapp.generated.resources.Res
import rideforge.composeapp.generated.resources.auth_create_account
import rideforge.composeapp.generated.resources.auth_email_label
import rideforge.composeapp.generated.resources.auth_error_email_required
import rideforge.composeapp.generated.resources.auth_error_name_required
import rideforge.composeapp.generated.resources.auth_error_password_required
import rideforge.composeapp.generated.resources.auth_error_password_short
import rideforge.composeapp.generated.resources.auth_error_request_failed
import rideforge.composeapp.generated.resources.auth_login_subtitle
import rideforge.composeapp.generated.resources.auth_login_title
import rideforge.composeapp.generated.resources.auth_password_label
import rideforge.composeapp.generated.resources.auth_sign_in
import rideforge.composeapp.generated.resources.auth_signing_in
import rideforge.composeapp.generated.resources.auth_use_demo
import rideforge.composeapp.generated.resources.common_back

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onRegister: () -> Unit,
    onBack: () -> Unit,
    viewModel: LoginViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) {
            onLoginSuccess()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ForgeBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(Res.string.auth_login_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
        Text(stringResource(Res.string.auth_login_subtitle), color = ForgeMuted)
        Spacer(Modifier.height(22.dp))
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = state.email,
                    onValueChange = { viewModel.onAction(LoginAction.EmailChanged(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.auth_email_label)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.password,
                    onValueChange = { viewModel.onAction(LoginAction.PasswordChanged(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.auth_password_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                if (state.error != null) {
                    Text(errorText(state.error), color = ForgeOrange, fontWeight = FontWeight.SemiBold)
                }
                PrimaryButton(
                    text = if (state.isLoading) stringResource(Res.string.auth_signing_in) else stringResource(Res.string.auth_sign_in),
                    onClick = { viewModel.onAction(LoginAction.Submit) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.AutoMirrored.Rounded.Login,
                )
                SecondaryButton(
                    text = stringResource(Res.string.auth_use_demo),
                    onClick = { viewModel.onAction(LoginAction.UseDemoCredentials) },
                    modifier = Modifier.fillMaxWidth(),
                )
                SecondaryButton(stringResource(Res.string.auth_create_account), onRegister, Modifier.fillMaxWidth())
                SecondaryButton(stringResource(Res.string.common_back), onBack, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
internal fun errorText(error: AuthFormError?): String = when (error) {
    AuthFormError.NameRequired -> stringResource(Res.string.auth_error_name_required)
    AuthFormError.EmailRequired -> stringResource(Res.string.auth_error_email_required)
    AuthFormError.PasswordRequired -> stringResource(Res.string.auth_error_password_required)
    AuthFormError.PasswordTooShort -> stringResource(Res.string.auth_error_password_short)
    AuthFormError.RequestFailed -> stringResource(Res.string.auth_error_request_failed)
    null -> ""
}
