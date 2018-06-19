//
//  SettingsController.kt
//  MyMonero
//
//  Copyright (c) 2014-2018, MyMonero.com
//
//  All rights reserved.
//
//  Redistribution and use in source and binary forms, with or without modification, are
//  permitted provided that the following conditions are met:
//
//  1. Redistributions of source code must retain the above copyright notice, this list of
//	conditions and the following disclaimer.
//
//  2. Redistributions in binary form must reproduce the above copyright notice, this list
//	of conditions and the following disclaimer in the documentation and/or other
//	materials provided with the distribution.
//
//  3. Neither the name of the copyright holder nor the names of its contributors may be
//	used to endorse or promote products derived from this software without specific
//	prior written permission.
//
//  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
//  EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
//  MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
//  THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
//  SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
//  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
//  INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
//  STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF
//  THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
//
//
package com.mymonero.Settings

import android.content.Context
import android.util.Log
import com.mymonero.KotlinUtils.EventEmitter
import com.mymonero.Currencies.Currency
import com.mymonero.Currencies.CurrencySymbol
import com.mymonero.KotlinUtils.BuiltDependency
import com.mymonero.Passwords.ChangePasswordRegistrant
import com.mymonero.Passwords.DeleteEverythingRegistrant
import com.mymonero.Passwords.PasswordController
import com.mymonero.Persistence.*
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.newSingleThreadContext
import kotlinx.coroutines.experimental.runBlocking
import java.util.*

//
interface SettingsProvider {} // for specific domains
interface IdleTimeoutAfterS_SettingsProvider
{
	//
	// Constants - Default values
	val default_appTimeoutAfterS: Long
	//
	// Constants - Special states
	val appTimeoutAfterS_neverValue: Long// = -1
	//
	// Properties
	val appTimeoutAfterS_nullForDefault_orNeverValue: Long?
}
//
class SettingsController: BuiltDependency, IdleTimeoutAfterS_SettingsProvider, ChangePasswordRegistrant, DeleteEverythingRegistrant
{
	//
	// Class
	companion object
	{
		val collectionName = "Settings" // so that it can be accessed prior to class instantiation
		//
		// Properties - Internal & Exposed for Testing (Not necessary for app integration)
		fun hasExisting_saved_document(documentPersister: DocumentPersister): Boolean // exposed only for testing - not used internally
		{
			//
			// Here, this is not synchronized, because it's assumed that whoever is calling it is not doing so from an application code context, and is probably calling it prior to the construction of any instance
			return this._inThread_existing_saved_documentContentString(documentPersister = documentPersister) != null
		}
		private fun _inThread_existing_saved_documentContentString(documentPersister: DocumentPersister): String?
		{
			val (err_str, documentContentStrings) = documentPersister.AllDocuments(collectionName = collectionName)
			if (err_str != null) {
				throw AssertionError(err_str)
			}
			val documentContentStrings_count = documentContentStrings!!.count()
			if (documentContentStrings_count > 1) {
				throw AssertionError("Invalid Settings data state - more than one document")
			}
			if (documentContentStrings_count < 1) {
				return null
			}
			return documentContentStrings[0]
		}
	}
	//
	// Protocols - Registrants
	val uuid = UUID.randomUUID().toString()
	override fun identifier(): String {
		return this.uuid
	}
	//
	// Constants - Default values
	override val default_appTimeoutAfterS: Long = 90 // s …… 30 was a bit short for new users
	val default_displayCurrencySymbol: CurrencySymbol
		get() {
			return Currency.XMR.symbol
		}
	val default_authentication__requireWhenSending = true
	val default_authentication__requireToShowWalletSecrets = true
	val default_authentication__tryBiometric = true
	//
	// Constants - Special state values
	override val appTimeoutAfterS_neverValue: Long = -1 // would preferably declare this in the SettingsProvider interface
	//
	// Constants - Persistence
	enum class DictKey(val rawValue: String)
	{
		_id("_id"),
		specificAPIAddressURLAuthority("specificAPIAddressURLAuthority"),
		appTimeoutAfterS_nullForDefault_orNeverValue("appTimeoutAfterS_nullForDefault_orNeverValue"),
		displayCurrencySymbol("displayCurrencySymbol"),
		authentication__requireWhenSending("authentication__requireWhenSending"),
		authentication__requireToShowWalletSecrets("authentication__requireToShowWalletSecrets"),
		authentication__tryBiometric("authentication__tryBiometric");
		//
		val key: String = this.rawValue
		companion object {
			val setForbidden_DictKeys: List<DictKey> = listOf(_id)
		}
	}
	//
	// Synchronization
	val syncThreadContext_threadName = "SettingsController-newSingleThreadContext"
	val syncThreadContext = newSingleThreadContext(syncThreadContext_threadName)
	val isOnSyncThread: Boolean
		get() = Thread.currentThread().name === syncThreadContext_threadName
	fun throwUnlessOnSyncThread() {
		if (this.isOnSyncThread == false) {
			throw AssertionError("This must only be called from the synchronization thread")
		}
	}
	fun runSync(fn: (self: SettingsController) -> Unit)
	{
		if (this.isOnSyncThread) { // there may be a more idiomatic way to detect the context we're running in
			fn(this)
			return // already on the thread - we don't want to deadlock
		}
		val self = this
		runBlocking {
			async(self.syncThreadContext) {
				fn(self)
			}.await() // must await or completion will not be synchronous, and must use await instead of join so exception gets percolated
		}
	}
	//
	// Properties - Initial
	private lateinit var applicationContext: Context
	private lateinit var documentPersister: DocumentPersister
	private lateinit var passwordController: PasswordController
	//
	// Properties - Internal
	private var _hasBooted = false
	//
	private var _id: DocumentId? = null
	private var _specificAPIAddressURLAuthority: String? = null
	private var _appTimeoutAfterS_nullForDefault_orNeverValue: Long? = null
	private var _authentication__requireWhenSending: Boolean = default_authentication__requireWhenSending
	private var _authentication__requireToShowWalletSecrets: Boolean = default_authentication__requireToShowWalletSecrets
	private var _authentication__tryBiometric: Boolean = default_authentication__tryBiometric
	private var _displayCurrencySymbol: CurrencySymbol = default_displayCurrencySymbol
	//
	// Properties - Interface
	val hasBooted: Boolean
		get() {
			var value: Boolean? = null
			this.runSync { self ->
				value = self._hasBooted
			}
			return value!!
		}
	val specificAPIAddressURLAuthority: String?
		get() {
			var value: String? = null
			this.runSync { self ->
				value = self._specificAPIAddressURLAuthority
			}
			return value
		}
	override val appTimeoutAfterS_nullForDefault_orNeverValue: Long?
		get() {
			var value: Long? = null
			this.runSync { self ->
				value = self._appTimeoutAfterS_nullForDefault_orNeverValue
			}
			return value
		}
	val authentication__requireWhenSending: Boolean
		get() {
			var value: Boolean? = null
			this.runSync { self ->
				value = self._authentication__requireWhenSending
			}
			return value!!
		}
	val authentication__requireToShowWalletSecrets: Boolean
		get() {
			var value: Boolean? = null
			this.runSync { self ->
				value = self._authentication__requireToShowWalletSecrets
			}
			return value!!
		}
	val authentication__tryBiometric: Boolean
		get() {
			var value: Boolean? = null
			this.runSync { self ->
				value = self._authentication__tryBiometric
			}
			return value!!
		}
	val displayCurrencySymbol: CurrencySymbol
		get() {
			var value: CurrencySymbol? = null
			this.runSync { self ->
				value = self._displayCurrencySymbol
			}
			return value!!
		}
	val displayCurrency: Currency
		get() {
			return Currency.valueOf(this.displayCurrencySymbol) // synchronized
		}
	//
	// Properties - Events
	val changed_specificAPIAddressURLAuthority_fns = EventEmitter<SettingsController, String?>()
	val changed_appTimeoutAfterS_nullForDefault_orNeverValue_fns = EventEmitter<SettingsController, String?>()
	val changed_displayCurrencySymbol_fns = EventEmitter<SettingsController, String?>()
	val changed_authentication__requireWhenSending_fns = EventEmitter<SettingsController, String?>()
	val changed_authentication__requireToShowWalletSecrets_fns = EventEmitter<SettingsController, String?>()
	val changed_authentication__tryBiometric_fns = EventEmitter<SettingsController, String?>()
	//
	// Lifecycle - Init
	constructor() {}
	fun init_applicationContext(dep: Context)
	{
		this.applicationContext = dep
	}
	fun init_documentPersister(dep: DocumentPersister)
	{
		this.documentPersister = dep
	}
	fun init_passwordController(dep: PasswordController)
	{
		this.passwordController = dep
	}
	override fun setup()
	{
		if (this.applicationContext == null || this.passwordController == null || this.documentPersister == null) {
			throw AssertionError("SettingsController missing dependency")
		}
		this.runSync runSync@{ self ->
			launch { // allow this registration to be asynchronous or we'll get a dependency cycle (pw -> useridle -> settings [self])
				self.passwordController.addRegistrantForDeleteEverything(self)
				self.passwordController.addRegistrantForChangePassword(self)
			}
			//
			val documentContentString = SettingsController._inThread_existing_saved_documentContentString(this.documentPersister)
			if (documentContentString == null) {
				self._inThread_initWithDefaults()
				return@runSync
			}
			val documentJSON = PersistableObject.new_plaintextDocumentDictFromJSONString(documentContentString)
			val _id = documentJSON[DictKey._id.key] as DocumentId
			val specificAPIAddressURLAuthority = documentJSON[DictKey.specificAPIAddressURLAuthority.key] as? String
			val existingValueFor_appTimeoutAfterS = documentJSON[DictKey.appTimeoutAfterS_nullForDefault_orNeverValue.rawValue]
			val appTimeoutAfterS_nullForDefault_orNeverValue = if (existingValueFor_appTimeoutAfterS != null) (existingValueFor_appTimeoutAfterS as Double).toLong() else null
			val authentication__requireWhenSending = documentJSON[DictKey.authentication__requireWhenSending.rawValue] as? Boolean
			val authentication__requireToShowWalletSecrets = documentJSON[DictKey.authentication__requireToShowWalletSecrets.rawValue] as? Boolean
			val authentication__tryBiometric = documentJSON[DictKey.authentication__tryBiometric.rawValue] as? Boolean
			val displayCurrencySymbol = documentJSON[DictKey.displayCurrencySymbol.rawValue] as? CurrencySymbol
			self._inThread_setup_loadState(
				_id = _id,
				specificAPIAddressURLAuthority = specificAPIAddressURLAuthority,
				appTimeoutAfterS_nullForDefault_orNeverValue = appTimeoutAfterS_nullForDefault_orNeverValue,
				authentication__requireWhenSending = if (authentication__requireWhenSending != null) authentication__requireWhenSending else default_authentication__requireWhenSending,
				authentication__requireToShowWalletSecrets = if (authentication__requireToShowWalletSecrets != null) authentication__requireToShowWalletSecrets else default_authentication__requireToShowWalletSecrets,
				authentication__tryBiometric = if (authentication__tryBiometric != null) authentication__tryBiometric else default_authentication__tryBiometric,
				displayCurrencySymbol = displayCurrencySymbol ?: default_displayCurrencySymbol
			)
		}
	}
	private fun _inThread_initWithDefaults()
	{
		this._inThread_setup_loadState(
			_id = null,
			specificAPIAddressURLAuthority = null,
			appTimeoutAfterS_nullForDefault_orNeverValue = this.default_appTimeoutAfterS,
			authentication__requireWhenSending = this.default_authentication__requireWhenSending,
			authentication__requireToShowWalletSecrets = this.default_authentication__requireToShowWalletSecrets,
			authentication__tryBiometric = this.default_authentication__tryBiometric,
			displayCurrencySymbol = this.default_displayCurrencySymbol
		)
	}
	private fun _inThread_setup_loadState(
		_id: DocumentId?,
		specificAPIAddressURLAuthority: String?,
		appTimeoutAfterS_nullForDefault_orNeverValue: Long?,
		authentication__requireWhenSending: Boolean,
		authentication__requireToShowWalletSecrets: Boolean,
		authentication__tryBiometric: Boolean,
		displayCurrencySymbol: CurrencySymbol
	) {
		this.throwUnlessOnSyncThread()
		//
		this._id = _id
		this._specificAPIAddressURLAuthority = specificAPIAddressURLAuthority
		this._appTimeoutAfterS_nullForDefault_orNeverValue = appTimeoutAfterS_nullForDefault_orNeverValue
		this._authentication__requireWhenSending = authentication__requireWhenSending
		this._authentication__requireToShowWalletSecrets = authentication__requireToShowWalletSecrets
		this._authentication__tryBiometric = authentication__tryBiometric
		this._displayCurrencySymbol = displayCurrencySymbol
		this._hasBooted = true
	}

//	deinit {
//		this.teardown()
//	}
//	fun teardown() {
//		this.stopObserving()
//	}
//	fun stopObserving() {
//		PasswordController.shared.removeRegistrantForDeleteEverything(this)
//		PasswordController.shared.removeRegistrantForChangePassword(this)
//	}
	//
	// Accessors - Persistence
	val shouldInsertNotUpdate: Boolean
		get() = this._id == null
	private fun _inThread_new_dictRepresentation() : DocumentJSON
	{
		this.throwUnlessOnSyncThread()
		//
		var dict: MutableMap<String, Any> = mutableMapOf()
		dict[DictKey._id.key] = this._id!!
		run {
			val value = this._specificAPIAddressURLAuthority
			if (value != null) {
				dict[DictKey.specificAPIAddressURLAuthority.key] = value
			}
		}
		run {
			val value = this._appTimeoutAfterS_nullForDefault_orNeverValue
			if (value != null) {
				dict[DictKey.appTimeoutAfterS_nullForDefault_orNeverValue.key] = value
			}
		}
		run {
			val value = this._authentication__requireWhenSending
			if (value != null) {
				dict[DictKey.authentication__requireWhenSending.rawValue] = value
			}
		}
		run {
			val value = this._authentication__requireToShowWalletSecrets
			if (value != null) {
				dict[DictKey.authentication__requireToShowWalletSecrets.rawValue] = value
			}
		}
		run {
			val value = this._authentication__tryBiometric
			if (value != null) {
				dict[DictKey.authentication__tryBiometric.rawValue] = value
			}
		}
		run {
			val value = this._displayCurrencySymbol
			if (value != null) {
				dict[DictKey.displayCurrencySymbol.rawValue] = value
			}
		}
		return dict as DocumentJSON
	}
	//
	// Imperatives - State
	fun set(valuesByDictKey: Map<DictKey, Any>) : String?
	{
		var err_str: String? = null
		this.runSync runSync@{ self ->
			for ((key, raw_value) in valuesByDictKey) {
				if (DictKey.setForbidden_DictKeys.contains(key) == true) {
					throw AssertionError("It's not legal to set the key ${key}")
				}
				val value: Any? = raw_value
				self._inThread_set(value = value, dictKey = key)
			}
			err_str = self.inThread_saveToDisk()
			if (err_str != null) {
				return@runSync
			}
			launch { // spin this off in an async routine so we don't block returning nor the synchronization thread
				for ((key, _) in valuesByDictKey) {
					self.invokeEmitterFor_changed(key)
				}
			}
		}
		return err_str
	}
	fun invokeEmitterFor_changed(key: DictKey)
	{
		when (key) {
			DictKey.specificAPIAddressURLAuthority -> {
				this.changed_specificAPIAddressURLAuthority_fns.invoke(this, null)
			}
			DictKey.appTimeoutAfterS_nullForDefault_orNeverValue -> {
				this.changed_appTimeoutAfterS_nullForDefault_orNeverValue_fns.invoke(this, null)
			}
			DictKey.authentication__requireWhenSending -> {
				this.changed_authentication__requireWhenSending_fns.invoke(this, null)
			}
			DictKey.authentication__requireToShowWalletSecrets -> {
				this.changed_authentication__requireToShowWalletSecrets_fns.invoke(this, null)
			}
			DictKey.authentication__tryBiometric -> {
				this.changed_authentication__tryBiometric_fns.invoke(this, null)
			}
			DictKey.displayCurrencySymbol -> {
				this.changed_displayCurrencySymbol_fns.invoke(this, null)
			}
			DictKey.specificAPIAddressURLAuthority -> {
				this.changed_specificAPIAddressURLAuthority_fns.invoke(this, null)
			}
		}
	}
	private fun _inThread_set(value: Any?, dictKey: DictKey)
	{
		this.throwUnlessOnSyncThread()
		//
		when (dictKey) {
			DictKey._id -> {
				throw AssertionError("Setting this field is not allowed")
			}
			DictKey.appTimeoutAfterS_nullForDefault_orNeverValue -> {
				this._appTimeoutAfterS_nullForDefault_orNeverValue = if (value != null) value as Long else null
			}
			DictKey.authentication__requireWhenSending -> {
				this._authentication__requireWhenSending = value as? Boolean ?: this.default_authentication__requireWhenSending
			}
			DictKey.authentication__requireToShowWalletSecrets -> {
				this._authentication__requireToShowWalletSecrets = value as? Boolean ?: this.default_authentication__requireToShowWalletSecrets
			}
			DictKey.authentication__tryBiometric -> {
				this._authentication__tryBiometric = value as? Boolean ?: this.default_authentication__tryBiometric
			}
			DictKey.displayCurrencySymbol -> {
				this._displayCurrencySymbol = value as CurrencySymbol
			}
			DictKey.specificAPIAddressURLAuthority -> {
				this._specificAPIAddressURLAuthority = value as? String
			}
		}
	}
	//
	fun set_appTimeoutAfterS_nullForDefault_orNeverValue(value: Long?) : String? =
		this.set(valuesByDictKey = mapOf(DictKey.appTimeoutAfterS_nullForDefault_orNeverValue to value as Any))
	fun set_authentication__requireWhenSending(value: Boolean) : String? =
		this.set(valuesByDictKey = mapOf(DictKey.authentication__requireWhenSending to value as Any))
	fun set_authentication__requireToShowWalletSecrets(value: Boolean) : String? =
		this.set(valuesByDictKey = mapOf(DictKey.authentication__requireToShowWalletSecrets to value as Any))
	fun set_authentication__tryBiometric(value: Boolean) : String? =
		this.set(valuesByDictKey = mapOf(DictKey.authentication__tryBiometric to value as Any))
	fun set_displayCurrencySymbol(value: CurrencySymbol?) : String? =
		this.set(valuesByDictKey = mapOf(DictKey.displayCurrencySymbol to value as Any))
	fun set_specificAPIAddressURLAuthority(value: String?) : String? =
		this.set(valuesByDictKey = mapOf(DictKey.specificAPIAddressURLAuthority to value as Any))
	//
	// Imperatives - Persistence
	private fun inThread_saveToDisk() : String?
	{
		this.throwUnlessOnSyncThread()
		//
		if (this.shouldInsertNotUpdate == true) {
			return this._inThread_saveToDisk_insert()
		}
		return this._inThread_saveToDisk_update()
	}
	private fun _inThread_saveToDisk_insert() : String?
	{
		this.throwUnlessOnSyncThread()
		//
		if (this._id != null) {
			throw AssertionError("non-nil _id in _saveToDisk_insert")
		}
		this._id = DocumentFileDescription.new_documentId()
		return this.__inThread_saveToDisk_write()
	}
	private fun _inThread_saveToDisk_update() : String?
	{
		this.throwUnlessOnSyncThread()
		//
		if (this._id == null) {
			throw AssertionError("nil _id in _saveToDisk_update")
		}
		return this.__inThread_saveToDisk_write()
	}
	private fun __inThread_saveToDisk_write() : String?
	{
		this.throwUnlessOnSyncThread()
		//
		val dict = this._inThread_new_dictRepresentation()
		val documentFileString = PersistableObject.new_plaintextJSONStringFromDocumentDict(dict)
		val err_str = this.documentPersister.Write(
			documentFileWithString = documentFileString,
			id = this._id!!,
			collectionName = collectionName
		)
		if (err_str != null) {
			Log.e("Settings", "Error while saving ${this}: ${err_str}")
			return err_str
		}
		Log.d("Persistence", "Saved ${this}.")
		return null
	}
	//
	// Delegation - PasswordController Registered Events
	override fun passwordController_DeleteEverything() : String?
	{
		var err_str: String? = null
		this.runSync { self -> // so as not to race with any saves
			val (inner_err_str, _) = this.documentPersister.RemoveAllDocuments(collectionName = collectionName)
			if (inner_err_str != null) {
				err_str = inner_err_str
			} else { // if delete succeeded
				self._inThread_initWithDefaults()
			}
		}
		return err_str
	}
	override fun passwordController_ChangePassword() : String?
	{
		var err_str: String? = null
		this.runSync { self ->
			if (this._hasBooted != true) {
				Log.w("Settings", "${this} asked to change password but not yet booted.")
				err_str = "Asked to change password but not yet booted"
			} else {
				err_str = this.inThread_saveToDisk()
			}
		}
		return err_str
	}
}