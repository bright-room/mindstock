package net.brightroom.mindstock.frontend.feature.onboarding

enum class OnboardingStep { Welcome, Name, Household, Confirm }

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val name: String = "",
    val householdName: String = "",
    val submitting: Boolean = false,
)
