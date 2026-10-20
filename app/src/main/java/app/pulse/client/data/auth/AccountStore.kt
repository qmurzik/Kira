package app.pulse.client.data.auth

import app.pulse.client.core.security.SecureStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Multi-account storage. The whole list (with tokens) is encrypted by [SecureStore]. */
class AccountStore(private val store: SecureStore, private val json: Json) {

    private val _accounts = MutableStateFlow(load())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _activeId = MutableStateFlow(store.getString(KEY_ACTIVE)?.toLongOrNull())
    val activeId: StateFlow<Long?> = _activeId.asStateFlow()

    val active: Account?
        get() = _accounts.value.firstOrNull { it.userId == _activeId.value } ?: _accounts.value.firstOrNull()

    fun get(userId: Long): Account? = _accounts.value.firstOrNull { it.userId == userId }

    @Synchronized
    fun upsert(account: Account, makeActive: Boolean = false) {
        val list = _accounts.value.filterNot { it.userId == account.userId } + account
        save(list)
        if (makeActive) setActive(account.userId)
    }

    @Synchronized
    fun update(userId: Long, transform: (Account) -> Account) {
        val current = get(userId) ?: return
        save(_accounts.value.map { if (it.userId == userId) transform(current) else it })
    }

    @Synchronized
    fun remove(userId: Long) {
        save(_accounts.value.filterNot { it.userId == userId })
        if (_activeId.value == userId) setActive(_accounts.value.firstOrNull()?.userId)
    }

    @Synchronized
    fun setActive(userId: Long?) {
        store.putString(KEY_ACTIVE, userId?.toString())
        _activeId.value = userId
    }

    private fun load(): List<Account> {
        val raw = store.getString(KEY_ACCOUNTS) ?: return emptyList()
        return runCatching { json.decodeFromString(ListSerializer(Account.serializer()), raw) }.getOrDefault(emptyList())
    }

    private fun save(list: List<Account>) {
        store.putString(KEY_ACCOUNTS, json.encodeToString(ListSerializer(Account.serializer()), list))
        _accounts.value = list
    }

    private companion object {
        const val KEY_ACCOUNTS = "accounts_v1"
        const val KEY_ACTIVE = "active_user"
    }
}
