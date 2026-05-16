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
import androidx.compose.material.icons.rounded.PersonAdd
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
import com.delminiusapps.rideforge.presentation.components.AppCard
import com.delminiusapps.rideforge.presentation.components.PrimaryButton
import com.delminiusapps.rideforge.presentation.components.SecondaryButton
import com.delminiusapps.rideforge.theme.ForgeBackground
import com.delminiusapps.rideforge.theme.ForgeMuted
import com.delminiusapps.rideforge.theme.ForgeOrange
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import rideforge.composeapp.generated.resources.Res
import rideforge.composeapp.generated.resources.auth_already_have_account
import rideforge.composeapp.generated.resources.auth_create_account
import rideforge.composeapp.generated.resources.auth_creating_account
import rideforge.composeapp.generated.resources.auth_email_label
import rideforge.composeapp.generated.resources.auth_login_title
import rideforge.composeapp.generated.resources.auth_name_label
import rideforge.composeapp.generated.resources.auth_password_label
import rideforge.composeapp.generated.resources.auth_register_subtitle
import rideforge.composeapp.generated.resources.common_back

@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onLogin: () -> Unit,
    onBack: () -> Unit,
    viewModel: RegisterViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.isSuccess) {
        if (state.isSuccess) {
            onRegisterSuccess()
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
        Text(stringResource(Res.string.auth_register_subtitle), color = ForgeMuted)
        Spacer(Modifier.height(22.dp))
        AppCard {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { viewModel.onAction(RegisterAction.NameChanged(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.auth_name_label)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.email,
                    onValueChange = { viewModel.onAction(RegisterAction.EmailChanged(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.auth_email_label)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.password,
                    onValueChange = { viewModel.onAction(RegisterAction.PasswordChanged(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(Res.string.auth_password_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                if (state.error != null) {
                    Text(errorText(state.error), color = ForgeOrange, fontWeight = FontWeight.SemiBold)
                }
                PrimaryButton(
                    text = if (state.isLoading) {
                        stringResource(Res.string.auth_creating_account)
                    } else {
                        stringResource(Res.string.auth_create_account)
                    },
                    onClick = { viewModel.onAction(RegisterAction.Submit) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Rounded.PersonAdd,
                )
                SecondaryButton(stringResource(Res.string.auth_already_have_account), onLogin, Modifier.fillMaxWidth())
                SecondaryButton(stringResource(Res.string.common_back), onBack, Modifier.fillMaxWidth())
            }
        }
    }
}
