package com.oniolla.interviewer.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oniolla.interviewer.data.ThemeMode
import com.oniolla.interviewer.data.model.SubscriptionResponse
import com.oniolla.interviewer.data.model.MeResponse
import com.oniolla.interviewer.data.model.DashboardUsage
import com.oniolla.interviewer.data.model.Specialization
import com.oniolla.interviewer.domain.ContentResult
import com.oniolla.interviewer.domain.GetPreparationLanguagesUseCase
import com.oniolla.interviewer.domain.ManageProfileUseCase
import com.oniolla.interviewer.ui.AuthViewModel.AuthViewState
import com.oniolla.interviewer.ui.UiEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ProfilePage { CLOSED, PROFILE, SPECIALIZATION, LANGUAGE, ACCOUNT }

data class ProfileViewState(
    val page: ProfilePage = ProfilePage.CLOSED,
    val profile: ContentViewState<MeResponse> = ContentViewState.NotLoaded,
    val usage: ContentViewState<DashboardUsage> = ContentViewState.NotLoaded,
    val streakDays: Int? = null,
    val subscription: SubscriptionResponse? = null,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val themeDialog: Boolean = false,
    val specialization: AuthViewState.SpecializationSelection = AuthViewState.SpecializationSelection(),
    val language: AuthViewState.PreparationLanguageSelection = AuthViewState.PreparationLanguageSelection(Specialization.ANDROID),
    val isSigningOut: Boolean = false,
    val deleteAccountDialog: Boolean = false,
    val isDeletingAccount: Boolean = false,
    val deleteAccountError: Boolean = false,
    val signedOut: Boolean = false,
    val sessionExpired: Boolean = false
) {
    val isSaving get() = isSigningOut || isDeletingAccount || specialization.isLoading || language.isSaving
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val manage: ManageProfileUseCase,
    private val getLanguages: GetPreparationLanguagesUseCase,
    private val savedState: SavedStateHandle
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileViewState(
        page = ProfilePage.entries.firstOrNull { it.name == savedState.get<String>("profilePage") } ?: ProfilePage.CLOSED,
        theme = manage.theme.value
    ))
    val state = _state.asStateFlow()
    private val _events = MutableSharedFlow<UiEvent>()
    val events = _events.asSharedFlow()
    private var languagesJob: Job? = null
    private var profileJob: Job? = null
    private var usageJob: Job? = null
    private var changed = savedState.get<Boolean>("profileChanged") ?: false

    init {
        viewModelScope.launch { manage.cachedSubscription().collect { subscription ->
            _state.update { it.copy(subscription = subscription) }
        } }
        viewModelScope.launch { manage.cachedUsage().collect { usage ->
            _state.update { state ->
                state.copy(usage = usage?.let { ContentViewState.Ready(it) }
                    ?: if (state.usage is ContentViewState.Ready) ContentViewState.NotLoaded else state.usage)
            }
        } }
        viewModelScope.launch { manage.cachedStats().collect { stats ->
            _state.update { it.copy(streakDays = stats?.summary?.streakDays) }
        } }
        viewModelScope.launch { manage.theme.collect { theme -> _state.update { it.copy(theme = theme) } } }
        if (_state.value.page != ProfilePage.CLOSED) {
            loadProfile()
            loadUsage(forceRefresh = false)
        }
    }

    private fun show(page: ProfilePage) {
        savedState["profilePage"] = page.name
        _state.update { it.copy(page = page) }
    }

    fun open(profile: MeResponse? = null) {
        show(ProfilePage.PROFILE)
        if (profile != null) _state.update { it.copy(profile = ContentViewState.Ready(profile)) }
        else if (_state.value.profile !is ContentViewState.Ready) loadProfile()
        val cachedUsage = manage.currentUsage()
        if (cachedUsage != null) _state.update { it.copy(usage = ContentViewState.Ready(cachedUsage)) }
        else if (_state.value.usage !is ContentViewState.Ready) loadUsage(forceRefresh = false)
    }

    fun loadUsage(forceRefresh: Boolean = true) {
        if (_state.value.isSaving || usageJob?.isActive == true) return
        _state.update { it.copy(usage = ContentViewState.Loading) }
        usageJob = viewModelScope.launch {
            val result = manage.usage(forceRefresh)
            currentCoroutineContext().ensureActive()
            _state.update { it.copy(usage = result.toViewState(),
                sessionExpired = it.sessionExpired || result == ContentResult.SessionExpired) }
        }
    }

    fun openAccount() {
        if (!_state.value.isSaving) show(ProfilePage.ACCOUNT)
    }

    fun finishAccount(profile: MeResponse?, accountChanged: Boolean) {
        if (profile != null) _state.update { it.copy(profile = ContentViewState.Ready(profile)) }
        if (accountChanged) {
            changed = true
            savedState["profileChanged"] = true
        }
        show(ProfilePage.PROFILE)
    }

    fun loadProfile() {
        if (_state.value.isSaving || profileJob?.isActive == true) return
        _state.update { it.copy(profile = ContentViewState.Loading) }
        profileJob = viewModelScope.launch {
            val result = manage.profile()
            _state.update { it.copy(profile = result.toViewState(),
                sessionExpired = it.sessionExpired || result == ContentResult.SessionExpired) }
            if (result is ContentResult.Success) {
                when (_state.value.page) {
                    ProfilePage.SPECIALIZATION -> prepareSpecialization(result.content)
                    ProfilePage.LANGUAGE -> prepareLanguage(result.content)
                    else -> Unit
                }
            }
            if (result is ContentResult.Error) _events.emit(UiEvent.Toast(result.message))
        }
    }

    private fun specialization(profile: MeResponse) =
        Specialization.entries.firstOrNull { it.code == profile.specialization } ?: Specialization.ANDROID

    private fun prepareSpecialization(profile: MeResponse) {
        _state.update { it.copy(specialization = AuthViewState.SpecializationSelection(specialization(profile))) }
    }

    fun editSpecialization() {
        if (_state.value.isSaving) return
        val profile = (_state.value.profile as? ContentViewState.Ready)?.content ?: return
        prepareSpecialization(profile)
        show(ProfilePage.SPECIALIZATION)
    }

    fun selectSpecialization(value: Specialization) {
        if (_state.value.isSaving) return
        _state.update { it.copy(specialization = it.specialization.copy(selectedSpecialization = value, error = null)) }
    }

    fun saveSpecialization() {
        val selection = _state.value.specialization
        if (_state.value.page != ProfilePage.SPECIALIZATION || _state.value.isSaving) return
        _state.update { it.copy(specialization = selection.copy(isLoading = true, error = null)) }
        viewModelScope.launch { handleSave(manage.specialization(selection.selectedSpecialization)) }
    }

    private fun prepareLanguage(profile: MeResponse) {
        _state.update { it.copy(language = AuthViewState.PreparationLanguageSelection(
            specialization(profile), selectedLanguage = profile.preparationLanguage
        )) }
        loadLanguages()
    }

    fun editLanguage() {
        if (_state.value.isSaving) return
        val profile = (_state.value.profile as? ContentViewState.Ready)?.content ?: return
        show(ProfilePage.LANGUAGE)
        prepareLanguage(profile)
    }

    fun loadLanguages() {
        if (_state.value.page != ProfilePage.LANGUAGE || _state.value.isSaving || languagesJob?.isActive == true) return
        _state.update { it.copy(language = it.language.copy(isLoadingLanguages = true, languagesError = null)) }
        languagesJob = viewModelScope.launch {
            val result = getLanguages(Unit)
            currentCoroutineContext().ensureActive()
            if (_state.value.page != ProfilePage.LANGUAGE) return@launch
            when (result) {
                is ContentResult.Success -> _state.update {
                    val selected = result.content.firstOrNull { option -> option.code == it.language.selectedLanguage }
                        ?: result.content.firstOrNull()
                    it.copy(language = it.language.copy(languages = result.content,
                        selectedLanguage = selected?.code, isLoadingLanguages = false))
                }
                is ContentResult.Error -> {
                    _state.update { it.copy(language = it.language.copy(isLoadingLanguages = false, languagesError = result.message)) }
                    _events.emit(UiEvent.Toast(result.message))
                }
                ContentResult.SessionExpired -> _state.update { it.copy(sessionExpired = true) }
            }
        }
    }

    fun selectLanguage(code: String) {
        if (_state.value.isSaving || _state.value.language.isLoadingLanguages) return
        if (_state.value.language.languages.none { it.code == code }) return
        _state.update { it.copy(language = it.language.copy(selectedLanguage = code, error = null)) }
    }

    fun saveLanguage() {
        val state = _state.value
        if (state.page != ProfilePage.LANGUAGE || state.isSaving || state.language.isLoadingLanguages) return
        val selected = state.language.languages.firstOrNull { it.code == state.language.selectedLanguage } ?: return
        _state.update { it.copy(language = it.language.copy(isSaving = true, error = null)) }
        viewModelScope.launch { handleSave(manage.language(selected.code)) }
    }

    private suspend fun handleSave(result: ContentResult<MeResponse>) {
        when (result) {
            is ContentResult.Success -> {
                changed = true
                savedState["profileChanged"] = true
                _state.update { it.copy(profile = ContentViewState.Ready(result.content),
                    specialization = it.specialization.copy(isLoading = false),
                    language = it.language.copy(isSaving = false)) }
                show(ProfilePage.PROFILE)
            }
            is ContentResult.Error -> {
                _state.update { it.copy(specialization = it.specialization.copy(isLoading = false, error = result.message),
                    language = it.language.copy(isSaving = false, error = result.message)) }
                _events.emit(UiEvent.Toast(result.message))
            }
            ContentResult.SessionExpired -> _state.update { it.copy(sessionExpired = true) }
        }
    }

    fun showThemeDialog() {
        if (!_state.value.isSaving) _state.update { it.copy(themeDialog = true) }
    }
    fun dismissThemeDialog() { _state.update { it.copy(themeDialog = false) } }
    fun selectTheme(theme: ThemeMode) {
        if (_state.value.isSaving) return
        manage.setTheme(theme)
        dismissThemeDialog()
    }

    /** Returns true when Home must reload after profile changes. */
    fun back(): Boolean {
        if (_state.value.isSaving) return false
        if (_state.value.themeDialog) { dismissThemeDialog(); return false }
        if (_state.value.deleteAccountDialog) { dismissDeleteAccountDialog(); return false }
        languagesJob?.cancel()
        when (_state.value.page) {
            ProfilePage.SPECIALIZATION, ProfilePage.LANGUAGE -> show(ProfilePage.PROFILE)
            ProfilePage.PROFILE -> {
                show(ProfilePage.CLOSED)
                val reload = changed
                changed = false
                savedState["profileChanged"] = false
                return reload
            }
            else -> Unit
        }
        return false
    }

    fun showDeleteAccountDialog() {
        if (_state.value.page != ProfilePage.ACCOUNT || _state.value.isSaving || _state.value.sessionExpired) return
        _state.update { it.copy(deleteAccountDialog = true, deleteAccountError = false) }
    }

    fun dismissDeleteAccountDialog() {
        if (_state.value.isDeletingAccount) return
        _state.update { it.copy(deleteAccountDialog = false, deleteAccountError = false) }
    }

    fun deleteAccount() {
        if (_state.value.page != ProfilePage.ACCOUNT || !_state.value.deleteAccountDialog || _state.value.isSaving) return
        languagesJob?.cancel()
        profileJob?.cancel()
        usageJob?.cancel()
        _state.update { it.copy(isDeletingAccount = true, deleteAccountError = false) }
        viewModelScope.launch {
            when (manage.deleteAccount()) {
                is ContentResult.Success -> {
                    show(ProfilePage.CLOSED)
                    _state.update { it.copy(isDeletingAccount = false, deleteAccountDialog = false,
                        signedOut = true, profile = ContentViewState.NotLoaded, usage = ContentViewState.NotLoaded,
                        subscription = null, streakDays = null) }
                }
                is ContentResult.Error -> _state.update {
                    it.copy(isDeletingAccount = false, deleteAccountError = true)
                }
                ContentResult.SessionExpired -> _state.update { it.copy(isDeletingAccount = false, sessionExpired = true) }
            }
        }
    }

    fun signOut() {
        if (_state.value.isSaving) return
        languagesJob?.cancel()
        usageJob?.cancel()
        _state.update { it.copy(isSigningOut = true) }
        viewModelScope.launch {
            when (val result = manage.signOut()) {
                is ContentResult.Success -> {
                    show(ProfilePage.CLOSED)
                    _state.update { it.copy(isSigningOut = false, signedOut = true, profile = ContentViewState.NotLoaded) }
                }
                is ContentResult.Error -> {
                    _state.update { it.copy(isSigningOut = false) }
                    _events.emit(UiEvent.Toast(result.message))
                }
                ContentResult.SessionExpired -> _state.update { it.copy(sessionExpired = true) }
            }
        }
    }
}
