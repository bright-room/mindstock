package net.brightroom.mindstock.frontend.feature.onboarding.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import mindstock.frontend.generated.resources.Res
import mindstock.frontend.generated.resources.household_suggest_1
import mindstock.frontend.generated.resources.household_suggest_2
import mindstock.frontend.generated.resources.household_suggest_3
import mindstock.frontend.generated.resources.onboarding_confirm_empty
import mindstock.frontend.generated.resources.onboarding_confirm_eyebrow
import mindstock.frontend.generated.resources.onboarding_confirm_household_label
import mindstock.frontend.generated.resources.onboarding_confirm_name_label
import mindstock.frontend.generated.resources.onboarding_confirm_note
import mindstock.frontend.generated.resources.onboarding_confirm_title
import mindstock.frontend.generated.resources.onboarding_edit
import mindstock.frontend.generated.resources.onboarding_finish
import mindstock.frontend.generated.resources.onboarding_finishing
import mindstock.frontend.generated.resources.onboarding_household_eyebrow
import mindstock.frontend.generated.resources.onboarding_household_placeholder
import mindstock.frontend.generated.resources.onboarding_household_sub
import mindstock.frontend.generated.resources.onboarding_household_title
import mindstock.frontend.generated.resources.onboarding_name_eyebrow
import mindstock.frontend.generated.resources.onboarding_name_placeholder
import mindstock.frontend.generated.resources.onboarding_name_sub
import mindstock.frontend.generated.resources.onboarding_name_title
import mindstock.frontend.generated.resources.onboarding_next
import mindstock.frontend.generated.resources.onboarding_progress
import mindstock.frontend.generated.resources.onboarding_skip
import mindstock.frontend.generated.resources.onboarding_start
import mindstock.frontend.generated.resources.onboarding_to_confirm
import mindstock.frontend.generated.resources.onboarding_welcome_item1
import mindstock.frontend.generated.resources.onboarding_welcome_item1_sub
import mindstock.frontend.generated.resources.onboarding_welcome_item2
import mindstock.frontend.generated.resources.onboarding_welcome_item2_sub
import mindstock.frontend.generated.resources.onboarding_welcome_sub
import mindstock.frontend.generated.resources.onboarding_welcome_title
import net.brightroom.mindstock.frontend.designsystem.atom.AppButton
import net.brightroom.mindstock.frontend.designsystem.atom.AppIcon
import net.brightroom.mindstock.frontend.designsystem.atom.AppIconName
import net.brightroom.mindstock.frontend.designsystem.atom.AppText
import net.brightroom.mindstock.frontend.designsystem.atom.ButtonSize
import net.brightroom.mindstock.frontend.designsystem.atom.SuggestionChips
import net.brightroom.mindstock.frontend.designsystem.atom.TextInput
import net.brightroom.mindstock.frontend.designsystem.atom.WizardProgress
import net.brightroom.mindstock.frontend.designsystem.theme.LocalMindstockTokens
import net.brightroom.mindstock.frontend.designsystem.theme.MindstockType
import net.brightroom.mindstock.frontend.feature.onboarding.OnboardingStep
import net.brightroom.mindstock.frontend.feature.onboarding.OnboardingUiState
import org.jetbrains.compose.resources.stringResource

@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onName: (String) -> Unit,
    onHouseholdName: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalMindstockTokens.current
    Column(modifier = modifier.fillMaxSize().background(tokens.surface2).padding(horizontal = 24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 6.dp).height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val showProgress = state.step == OnboardingStep.Name || state.step == OnboardingStep.Household
            if (showProgress) {
                Box(
                    modifier =
                        Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, tokens.line, RoundedCornerShape(12.dp))
                            .background(tokens.surface)
                            .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(AppIconName.Back, contentDescription = null, size = 19.dp, tint = tokens.ink)
                }
                val current = if (state.step == OnboardingStep.Name) 1 else 2
                WizardProgress(total = 2, current = current, modifier = Modifier.weight(1f))
                AppText(
                    stringResource(Res.string.onboarding_progress, current, 2),
                    style = MindstockType.sectionMeta(),
                    color = tokens.faint,
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
        }

        Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            when (state.step) {
                OnboardingStep.Welcome -> {
                    WelcomeStep()
                }

                OnboardingStep.Name -> {
                    FormStep(
                        icon = AppIconName.User,
                        eyebrow = stringResource(Res.string.onboarding_name_eyebrow),
                        title = stringResource(Res.string.onboarding_name_title),
                        sub = stringResource(Res.string.onboarding_name_sub),
                        value = state.name,
                        onChange = onName,
                        placeholder = stringResource(Res.string.onboarding_name_placeholder),
                    )
                }

                OnboardingStep.Household -> {
                    FormStep(
                        icon = AppIconName.Home,
                        eyebrow = stringResource(Res.string.onboarding_household_eyebrow),
                        title = stringResource(Res.string.onboarding_household_title),
                        sub = stringResource(Res.string.onboarding_household_sub),
                        value = state.householdName,
                        onChange = onHouseholdName,
                        placeholder = stringResource(Res.string.onboarding_household_placeholder),
                        suggestions =
                            listOf(
                                stringResource(Res.string.household_suggest_1),
                                stringResource(Res.string.household_suggest_2),
                                stringResource(Res.string.household_suggest_3),
                            ),
                        onPickSuggestion = onHouseholdName,
                    )
                }

                OnboardingStep.Confirm -> {
                    ConfirmStep(name = state.name, householdName = state.householdName)
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (state.step) {
                OnboardingStep.Welcome -> {
                    AppButton(onClick = onNext, size = ButtonSize.Lg, modifier = Modifier.fillMaxWidth()) {
                        AppText(stringResource(Res.string.onboarding_start))
                    }
                }

                OnboardingStep.Name -> {
                    AppButton(
                        onClick = onNext,
                        size = ButtonSize.Lg,
                        enabled = state.name.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AppText(stringResource(Res.string.onboarding_next))
                    }
                }

                OnboardingStep.Household -> {
                    AppButton(
                        onClick = onNext,
                        size = ButtonSize.Lg,
                        enabled = state.householdName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AppText(stringResource(Res.string.onboarding_to_confirm))
                    }
                    Spacer(Modifier.height(12.dp))
                    GhostTextButton(text = stringResource(Res.string.onboarding_skip), onClick = onSubmit)
                }

                OnboardingStep.Confirm -> {
                    AppButton(
                        onClick = onSubmit,
                        size = ButtonSize.Lg,
                        enabled = !state.submitting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AppText(stringResource(if (state.submitting) Res.string.onboarding_finishing else Res.string.onboarding_finish))
                    }
                    Spacer(Modifier.height(12.dp))
                    GhostTextButton(text = stringResource(Res.string.onboarding_edit), onClick = onBack)
                }
            }
        }
    }
}

@Composable
private fun GhostTextButton(
    text: String,
    onClick: () -> Unit,
) {
    val tokens = LocalMindstockTokens.current
    Box(modifier = Modifier.clickable(onClick = onClick).padding(8.dp)) {
        AppText(text, style = MindstockType.sectionMeta(), color = tokens.faint)
    }
}

@Composable
private fun WelcomeStep() {
    val tokens = LocalMindstockTokens.current
    Column(modifier = Modifier.fillMaxWidth().padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Box(
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(tokens.accent),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(AppIconName.Box, contentDescription = null, size = 34.dp, tint = tokens.onAccent)
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AppText(stringResource(Res.string.onboarding_welcome_title), style = MindstockType.screenTitle(), color = tokens.ink)
            AppText(stringResource(Res.string.onboarding_welcome_sub), style = MindstockType.summarySub(), color = tokens.sub)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WelcomeItem(
                icon = AppIconName.User,
                title = stringResource(Res.string.onboarding_welcome_item1),
                sub = stringResource(Res.string.onboarding_welcome_item1_sub),
            )
            WelcomeItem(
                icon = AppIconName.Home,
                title = stringResource(Res.string.onboarding_welcome_item2),
                sub = stringResource(Res.string.onboarding_welcome_item2_sub),
            )
        }
    }
}

@Composable
private fun WelcomeItem(
    icon: AppIconName,
    title: String,
    sub: String,
) {
    val tokens = LocalMindstockTokens.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tokens.surface)
                    .border(1.dp, tokens.line, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, contentDescription = null, size = 19.dp, tint = tokens.accent)
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            AppText(title, style = MindstockType.sectionMeta(), color = tokens.ink)
            AppText(sub, style = MindstockType.summarySub(), color = tokens.faint)
        }
    }
}

@Composable
private fun FormStep(
    icon: AppIconName,
    eyebrow: String,
    title: String,
    sub: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    suggestions: List<String> = emptyList(),
    onPickSuggestion: (String) -> Unit = {},
) {
    val tokens = LocalMindstockTokens.current
    Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(tokens.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, contentDescription = null, size = 26.dp, tint = tokens.accent)
        }
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            AppText(eyebrow, style = MindstockType.sectionMeta(), color = tokens.accent)
            AppText(title, style = MindstockType.summaryTitle(), color = tokens.ink)
            AppText(sub, style = MindstockType.summarySub(), color = tokens.sub)
        }
        TextInput(value = value, onValueChange = onChange, placeholder = placeholder, modifier = Modifier.fillMaxWidth())
        if (suggestions.isNotEmpty()) {
            SuggestionChips(suggestions = suggestions, onPick = onPickSuggestion)
        }
    }
}

@Composable
private fun ConfirmStep(
    name: String,
    householdName: String,
) {
    val tokens = LocalMindstockTokens.current
    val empty = stringResource(Res.string.onboarding_confirm_empty)
    Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            AppText(stringResource(Res.string.onboarding_confirm_eyebrow), style = MindstockType.sectionMeta(), color = tokens.accent)
            AppText(stringResource(Res.string.onboarding_confirm_title), style = MindstockType.summaryTitle(), color = tokens.ink)
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(tokens.surface)
                    .border(1.dp, tokens.lineSoft, RoundedCornerShape(18.dp)),
        ) {
            ConfirmRow(label = stringResource(Res.string.onboarding_confirm_name_label), value = name.ifBlank { empty })
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(tokens.lineSoft))
            ConfirmRow(label = stringResource(Res.string.onboarding_confirm_household_label), value = householdName.ifBlank { empty })
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppIcon(AppIconName.Check, contentDescription = null, size = 16.dp, tint = tokens.statusOk)
            AppText(stringResource(Res.string.onboarding_confirm_note), style = MindstockType.summarySub(), color = tokens.faint)
        }
    }
}

@Composable
private fun ConfirmRow(
    label: String,
    value: String,
) {
    val tokens = LocalMindstockTokens.current
    Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        AppText(label, style = MindstockType.sectionMeta(), color = tokens.faint)
        AppText(value, style = MindstockType.summaryTitle(), color = tokens.ink)
    }
}
