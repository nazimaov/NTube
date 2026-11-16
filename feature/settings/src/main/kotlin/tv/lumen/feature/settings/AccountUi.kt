package tv.lumen.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import tv.lumen.core.designsystem.focus.FocusMemory
import tv.lumen.core.designsystem.focus.rememberFocus
import tv.lumen.core.designsystem.theme.LumenColors
import tv.lumen.data.account.AccountState

/** Вход в аккаунт Google: код на экране, подтверждение на телефоне. Пароль приложению не передаётся. */
@Composable
internal fun AccountSection(
    state: AccountState,
    onSignIn: () -> Unit,
    onCancel: () -> Unit,
    onSignOut: () -> Unit,
    focusMemory: FocusMemory,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        when (state) {
            AccountState.SignedOut, is AccountState.Failed -> {
                if (state is AccountState.Failed) {
                    Text(
                        stringResource(failureText(state.reason)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                SettingCard(
                    title = stringResource(R.string.settings_account_sign_in),
                    value = null,
                    onClick = onSignIn,
                    modifier = Modifier.rememberFocus(focusMemory, "account:in", isFirst = true),
                )
                Text(stringResource(R.string.settings_account_warning), style = MaterialTheme.typography.bodySmall, color = LumenColors.TextSecondary)
            }
            AccountState.Starting -> {
                Text(stringResource(R.string.settings_account_starting), style = MaterialTheme.typography.titleMedium)
                SettingCard(
                    stringResource(R.string.settings_cancel), null, onCancel,
                    Modifier.rememberFocus(focusMemory, "account:cancel", isFirst = true),
                )
            }
            is AccountState.WaitingForCode -> {
                Text(stringResource(R.string.settings_account_open_page, state.verificationUrl), style = MaterialTheme.typography.titleMedium)
                Text(
                    state.userCode,
                    style = MaterialTheme.typography.displayMedium,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                Text(stringResource(R.string.settings_account_enter_code), style = MaterialTheme.typography.bodyMedium, color = LumenColors.TextSecondary)
                SettingCard(
                    stringResource(R.string.settings_cancel), null, onCancel,
                    Modifier.rememberFocus(focusMemory, "account:cancel", isFirst = true),
                )
            }
            AccountState.SignedIn -> {
                Text(stringResource(R.string.settings_account_signed_in_text), style = MaterialTheme.typography.titleMedium)
                SettingCard(
                    stringResource(R.string.settings_account_sign_out), null, onSignOut,
                    Modifier.rememberFocus(focusMemory, "account:out", isFirst = true),
                )
                Text(stringResource(R.string.settings_account_revoke_hint), style = MaterialTheme.typography.bodySmall, color = LumenColors.TextSecondary)
            }
        }
    }
}

private fun failureText(reason: AccountState.Reason): Int = when (reason) {
    AccountState.Reason.NETWORK -> R.string.settings_account_err_network
    AccountState.Reason.UNAVAILABLE -> R.string.settings_account_err_unavailable
    AccountState.Reason.DENIED -> R.string.settings_account_err_denied
    AccountState.Reason.EXPIRED -> R.string.settings_account_err_expired
}
