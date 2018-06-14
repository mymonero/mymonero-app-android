//
//  PasswordController.kt
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
package com.mymonero.mymonero

import android.content.Context
import android.util.Log
import kotlinx.coroutines.experimental.*
import java.util.Timer
import kotlin.concurrent.schedule
import java.lang.ref.WeakReference
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

typealias Password = String

public enum class PasswordType(val rawValue: String)
{
	PIN("PIN"),
	password("password");
	//
	var humanReadableString: String = this.rawValue // TODO: return localized
	var capitalized_humanReadableString: String = this.humanReadableString.capitalize()
	fun invalidEntry_humanReadableString(context: Context): String
	{
		val code = if (this == PIN) R.string.incorrect_PIN else R.string.incorrect_password
		//
		return context.resources.getString(code)
	}
	//
	companion object
	{
		fun new_detectedFromPassword(password: Password): PasswordType
		{
			val numbers = "0123456789"
			val passwordWithoutNumbers = password.filterNot { c -> numbers.contains(c) }
			if (passwordWithoutNumbers.isEmpty()) { // and contains only numbers
				return PasswordType.PIN
			}
			return PasswordType.password
		}
	}
}
//
interface PasswordProvider
{ // you can use this type for dependency-injecting a PasswordController implementation; see PersistableObject
	val password: Password?
}
//
interface PasswordControllerEventParticipant // abstract interface - implement with anoter interface
{
	fun identifier(): String // To support isEqual
}
fun isEqual(
	l: PasswordControllerEventParticipant,
	r: PasswordControllerEventParticipant
): Boolean {
	return l.identifier() == r.identifier()
}
class WeakRefTo_EventParticipant( // TODO: a class is slightly heavyweight for this - anything more like a struct?
	var value: WeakReference<PasswordControllerEventParticipant>? = null
) {} // use this to construct arrays of event participants w/o having to hold strong references to them
fun isEqual(
	l: WeakRefTo_EventParticipant,
	r: WeakRefTo_EventParticipant
): Boolean {
	if (l.value == null && r.value == null) {
		return true // null == null
	} else if (l.value == null || r.value == null) {
		return false // null != !null
	}
	val l_ref = l.value!!.get()
	val r_ref = r.value!!.get()
	if (l_ref == null && r_ref == null) {
		return true // null == null
	} else if (l_ref == null || r_ref == null) {
		return false // null != !null
	}
	return l_ref!!.identifier() == r_ref!!.identifier()
}
//
interface PasswordEntryDelegate: PasswordControllerEventParticipant
{
	fun getUserToEnterExistingPassword(
		isForChangePassword: Boolean,
		isForAuthorizingAppActionOnly: Boolean, // normally no - this is for things like SendFunds
		customNavigationBarTitle: String?,
		enterExistingPassword_cb: (
			didCancel_orNull: Boolean?,
			obtainedPasswordString: Password?
		) -> Unit
	)
	fun getUserToEnterNewPasswordAndType(
		isForChangePassword: Boolean,
		enterNewPasswordAndType_cb: (
			didCancel_orNull: Boolean?,
			obtainedPasswordString: Password?,
			passwordType: PasswordType?
		) -> Unit
	)
}
interface ChangePasswordRegistrant: PasswordControllerEventParticipant
{
	// Implement this function to support change-password events as well as revert-from-failed-change-password
	fun passwordController_ChangePassword(): String? // return err_str:String if error - it will abort and try to revert the changepassword process. at time of writing, this was able to be kept synchronous.
}
interface DeleteEverythingRegistrant: PasswordControllerEventParticipant
{
	fun passwordController_DeleteEverything(): String? // return err_str:String if error. at time of writing, this was able to be kept synchronous.
}
//
class PasswordControllerInitParams(
	context: Context,
	userIdleController: UserIdleController
) {
	val context = context
	val userIdleController = userIdleController
}
class PasswordController: PasswordProvider
{
	//
	// Class
	companion object
	{
		val appInstance by lazy { ForApp.instance }
		//
		val minPasswordLength = 6
		val maxLegal_numberOfTriesDuringThisTimePeriod = 5
	}
	// Singleton - Application - Interal
	private object ForApp {
		val instance = PasswordController(
			PasswordControllerInitParams(
				context = MainApplication.applicationContext(),
				userIdleController = UserIdleController.appInstance
			)
		)
	}
	//
	enum class DictKey(val rawValue: String)
	{
		_id("_id"),
		passwordType("passwordType"),
		messageAsEncryptedDataForUnlockChallenge_base64String("messageAsEncryptedDataForUnlockChallenge_base64String")
	}
	//
	// Interface - Properties - Events
	val setFirstPasswordDuringThisRuntime_fns = EventEmitter<PasswordController, String>()
	val registrantsAllChangedPassword_fns = EventEmitter<PasswordController, String>() // not really used anymore - never use for critical things
	//
	val obtainedNewPassword_fns = EventEmitter<PasswordController, String>()
	val obtainedCorrectExistingPassword_fns = EventEmitter<PasswordController, String>()
	//
	val erroredWhileSettingNewPassword_fns = EventEmitter<PasswordController, String>()
	val erroredWhileGettingExistingPassword_fns = EventEmitter<PasswordController, String>()
	val canceledWhileEnteringExistingPassword_fns = EventEmitter<PasswordController, String>()
	val canceledWhileEnteringNewPassword_fns = EventEmitter<PasswordController, String>()
	//
	val canceledWhileChangingPassword_fns = EventEmitter<PasswordController, String>()
	val errorWhileChangingPassword_fns = EventEmitter<PasswordController, String>()
	//
	val errorWhileAuthorizingForAppAction_fns = EventEmitter<PasswordController, String>()
	val successfullyAuthenticatedForAppAction_fns = EventEmitter<PasswordController, String>()
	//
	val willDeconstructBootedStateAndClearPassword_fns = EventEmitter<PasswordController, Boolean>() // Boolean parameter is isForADeleteEverything
	val didDeconstructBootedStateAndClearPassword_fns = EventEmitter<PasswordController, String>()
	val didErrorWhileDeletingEverything_fns = EventEmitter<PasswordController, String>()
	val havingDeletedEverything_didDeconstructBootedStateAndClearPassword_fns = EventEmitter<PasswordController, String>()
	//
	// Properties - Dependencies
	val context: Context
	val userIdleController: UserIdleController
	//
	// Synchronization
	val syncThreadContext_threadName = "PasswordController-newSingleThreadContext"
	val syncThreadContext = newSingleThreadContext(syncThreadContext_threadName)
	val isOnSyncThread: Boolean
		get() = Thread.currentThread().name === syncThreadContext_threadName
	fun throwUnlessOnSyncThread() {
		if (this.isOnSyncThread == false) {
			throw AssertionError("This must only be called from the synchronization thread")
		}
	}
	fun runSync(fn: (self: PasswordController) -> Unit)
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
	// Properties - State
	private var _hasBooted = false
	val hasBooted: Boolean
		get() {
			var value: Boolean? = null
			this.runSync { self ->
				value = self._hasBooted
			}
			return value!!
		}
	private var _id: DocumentId? = null
	override val password: Password?
		get() {
			var value: Password? = null
			this.runSync { self ->
				value = self._password
			}
			return value
		}
	val passwordType: PasswordType?
		get() {
			var value: PasswordType? = null
			this.runSync { self ->
				value = self._passwordType
			}
			return value
		}
	private var _password: Password? = null
	private var _passwordType: PasswordType? = null // it will default to .password per init
	val hasUserSavedAPassword: Boolean // we /probably/ don't need to synchronize this ... but we might
		get() { // this obviously has a file I/O hit, which is not optimal; alternatives are use sparingly or cache at appropriate locations
			val (err_str, ids) = DocumentPersister.IdsOfAllDocuments(
				collectionName = this.collectionName
			)
			if (err_str != null) {
				throw AssertionError(".hasUserSavedAPassword: ${err_str}")
			}
			val numberOfIds = ids!!.count()
			if (numberOfIds > 1) {
				throw AssertionError("Illegal: Should be only one document")
			} else if (numberOfIds == 0) {
				return false
			}
			return true
		}
	private var messageAsEncryptedDataForUnlockChallenge_base64String: String? = null
	private var isAlreadyGettingExistingOrNewPWFromUser: Boolean? = null
	private var passwordEntryDelegate: PasswordEntryDelegate? = null // someone in the app must set this by calling setPasswordEntryDelegate(to:); TODO: would we like this to be weak?
	fun setPasswordEntryDelegate(to_delegate: PasswordEntryDelegate)
	{
		if (this.passwordEntryDelegate != null) {
			throw AssertionError("setPasswordEntryDelegate called but this.passwordEntryDelegate already exists") // fatal
		}
		this.passwordEntryDelegate = to_delegate
	}
	fun clearPasswordEntryDelegate(from_existingDelegate: PasswordEntryDelegate)
	{
		this.runSync { self ->
			if (self.passwordEntryDelegate == null) {
				throw AssertionError("clearPasswordEntryDelegate called but no passwordEntryDelegate exists") // fatal
			}
			if (isEqual(self.passwordEntryDelegate!!, from_existingDelegate) == false) {
				// fatal
				throw AssertionError("clearPasswordEntryDelegate called but passwordEntryDelegate does not match")
			} else {
				self.passwordEntryDelegate = null
			}
		}
	}
	//
	// genericizing the member type on these does come with the downside of loosening type safety:
	private var weakRefsTo_changePasswordRegistrants = mutableListOf<WeakRefTo_EventParticipant>()
	private var weakRefsTo_deleteEverythingRegistrants = mutableListOf<WeakRefTo_EventParticipant>()
	//
	// Accessors - Runtime - Derived properties
	private val _hasUserEnteredValidPasswordYet: Boolean
		get() = this._password != null
	private val _isUserChangingPassword: Boolean
		get() = this._hasUserEnteredValidPasswordYet == true && this.isAlreadyGettingExistingOrNewPWFromUser == true
	// TODO: synchronize these:
	val hasUserEnteredValidPasswordYet: Boolean
		get() {
			var value: Boolean? = null
			this.runSync { self ->
				value = self._hasUserEnteredValidPasswordYet
			}
			return value!!
		}
	val isUserChangingPassword: Boolean
		get() {
			var value: Boolean? = null
			this.runSync { self ->
				value = self._isUserChangingPassword
			}
			return value!!
		}
	//
	fun _new_incorrectPasswordValidationErrorMessageString(context: Context): String
	{
		return this._passwordType!!.invalidEntry_humanReadableString(context)
	}
	//
	// Accessors - Common
	private fun _isCorrect(existingPassword: Password, enteredPassword: String): Boolean
	{ // NOTE: This function should most likely remain fileprivate so that it is not cheap to check PW and must be done through the PW entry UI (by way of methods on PasswordController)
		// FIXME/TODO: is this check too weak? is it better to try decrypt and check hmac mismatch?
		//
		return existingPassword == enteredPassword // force unwrap this.password so it cannot be equal to a null passed as arg despite present method sig decl
	}
	//
	// Internal - Properties - Constants - Persistence
	val collectionName = "PasswordMeta"
	val plaintextMessageToSaveForUnlockChallenges = "this is just a string that we'll use for checking whether a given password can unlock an encrypted version of this very message"
	//
	// Internal - Lifecycle
	constructor(
		initParams: PasswordControllerInitParams
	) {
		this.context = initParams.context
		this.userIdleController = initParams.userIdleController
		//
		this.setup()
	}
	fun setup()
	{
		this.startObserving_userIdle()
		this.initializeRuntimeAndBoot()
	}
	fun startObserving_userIdle()
	{
		this.userIdleController.userDidBecomeIdle_fns.startObserving({ emitter, dummy ->
			this._userDidBecomeIdle()
		})
	}
	private fun initializeRuntimeAndBoot() {
		this.runSync { self -> // is blocking
			self._inThread_initializeRuntimeAndBoot()
		}
	}
	private fun _inThread_initializeRuntimeAndBoot()
	{
		this.throwUnlessOnSyncThread()
		if (this._hasBooted == true) { // fatal
			throw AssertionError("initializeRuntimeAndBoot called while already booted")
		}
		val (err_str, documentContentStrings) = DocumentPersister.AllDocuments(
			collectionName = this.collectionName
		)
		if (err_str != null) {
			val errorDescription = "Fatal error while loading ${this.collectionName}: ${err_str!!}"
			Log.e("Passwords", errorDescription)
			throw AssertionError(errorDescription)
		}
		val documentContentStrings_count = documentContentStrings!!.count()
		if (documentContentStrings_count > 1) {
			throw AssertionError("Unexpected state while loading ${this.collectionName}: more than one saved doc.")
		}
		fun _proceedTo_load(documentJSON: DocumentJSON) {
			this._id = documentJSON[DictKey._id.rawValue] as? DocumentId
			val existing_passwordType = documentJSON[DictKey.passwordType.rawValue] as? String
			val passwordType_rawValue = if (existing_passwordType != null) existing_passwordType else PasswordType.password.rawValue
			this._passwordType = PasswordType.valueOf(passwordType_rawValue)
			this.messageAsEncryptedDataForUnlockChallenge_base64String = documentJSON[DictKey.messageAsEncryptedDataForUnlockChallenge_base64String.rawValue] as? String
			if (this._id != null) { // existing doc
				if (this.messageAsEncryptedDataForUnlockChallenge_base64String == null || this.messageAsEncryptedDataForUnlockChallenge_base64String == "") {
					// ^-- but it was saved w/o an encrypted challenge str
					// TODO: not sure how to handle this case. delete all local info? would suck but otoh when would this happen if not for a cracking attempt, some odd/fatal code fault, or a known migration?
					val err_str = "Found undefined encrypted msg for unlock challenge in saved password model document"
					Log.e("Passwords", err_str)
					throw AssertionError(err_str)
				}
			}
			//
			this._hasBooted = true
			this._inThread_callAndFlushAllBlocksWaitingForBootToExecute()
			//			Log.d("Passwords", "Booted \(this) and called all waiting blocks. Waiting for unlock.")
		}
		if (documentContentStrings_count == 0) {
			val fabricated_documentJSON = mutableMapOf<String, Any>()
			fabricated_documentJSON[DictKey.passwordType.rawValue] = PasswordType.password.rawValue // default (at least for now)
			//
			_proceedTo_load(fabricated_documentJSON)
			return
		}
		val sample_documentContentString = documentContentStrings!![0]
		val documentJSON = PersistableObject.new_plaintextDocumentDictFromJSONString(sample_documentContentString)
		_proceedTo_load(documentJSON)
	}
	//
	// Imperatives - Execution deferment
	var __blocksWaitingForBootToExecute = mutableListOf<(self: PasswordController) -> Unit>()
	// NOTE: onceBooted() exists because even though init()->setup() is synchronous, we need to be able to tear down and reconstruct the passwordController booted state, e.g. on user idle and delete everything
	fun onceBooted(
		fn: ((self: PasswordController) -> Unit) // this will always call back on the single thread
	) {
		this.runSync { self ->
			self._inThread_onceBooted(fn)
		}
	}
	fun _inThread_onceBooted(fn: ((self: PasswordController) -> Unit))
	{
		if (this._hasBooted == true) {
			fn(this)
			return
		}
		if (this.__blocksWaitingForBootToExecute == null) {
			this.__blocksWaitingForBootToExecute = mutableListOf<(self: PasswordController) -> Unit>()
		}
		this.__blocksWaitingForBootToExecute.add(fn)
	}
	private fun _inThread_callAndFlushAllBlocksWaitingForBootToExecute()
	{
		if (this.__blocksWaitingForBootToExecute == null) {
			return
		}
		//
		// could check list of blocks empty here but not a huge win
		//
		val blocks = mutableListOf<(self: PasswordController) -> Unit>()
		this.__blocksWaitingForBootToExecute = mutableListOf<(self: PasswordController) -> Unit>() // flash so the old blocks get freed - and we can do this before calling the blocks
		for (block in blocks) {
			block(this)
		}
	}
	//
	// Accessors - Deferring execution convenience methods
	fun OnceBootedAndPasswordObtained(
		fn: (password: Password, passwordType: PasswordType) -> Unit,
		userCanceled_fn: (() -> Unit)?
	) {
		fun callBackHavingObtainedPassword()
		{
			this.runSync { self ->
				fn(self._password!!, self._passwordType!!)
			}
		}
		fun callBackHavingCanceled()
		{
			userCanceled_fn?.let {
				it()
			}
		}
		this.runSync { self ->
			if (self._hasUserEnteredValidPasswordYet) {
				callBackHavingObtainedPassword()
				return@runSync
			}
		}
		// then we have to wait for it
		var hasCalledBack = false
		var token__obtainedNewPassword: EventSubscriptionToken? = null
		var token__obtainedCorrectExistingPassword: EventSubscriptionToken? = null
		var token__canceledWhileEnteringExistingPassword: EventSubscriptionToken? = null
		var token__canceledWhileEnteringNewPassword: EventSubscriptionToken? = null
		fun ___guardAllCallBacks(): Boolean
		{
			if (hasCalledBack) {
				val msg = "PasswordController/OnceBootedAndPasswordObtained hasCalledBack already true"
				Log.e("Passwords", msg)
				throw AssertionError(msg)
			}
			hasCalledBack = true
			return true
		}
		fun __stopListening()
		{
			this.obtainedNewPassword_fns.stopObserving(token__obtainedNewPassword!!)
			this.obtainedCorrectExistingPassword_fns.stopObserving(token__obtainedCorrectExistingPassword!!)
			this.canceledWhileEnteringExistingPassword_fns.stopObserving(token__canceledWhileEnteringExistingPassword!!)
			this.canceledWhileEnteringNewPassword_fns.stopObserving(token__canceledWhileEnteringNewPassword!!)
			token__obtainedNewPassword = null
			token__obtainedCorrectExistingPassword = null
			token__canceledWhileEnteringExistingPassword = null
			token__canceledWhileEnteringNewPassword = null
		}
		fun _aPasswordWasObtained()
		{
			if (___guardAllCallBacks() != false) {
				__stopListening() // immediately unsubscribe
				callBackHavingObtainedPassword()
			}
		}
		fun _obtainingPasswordWasCanceled()
		{
			if (___guardAllCallBacks() != false) {
				__stopListening() // immediately unsubscribe
				callBackHavingCanceled()
			}
		}
		this.runSync { self ->
			self._inThread_onceBooted({ // still on the thread
				// hang onto tokens so we can unsub
				token__obtainedNewPassword = self.obtainedNewPassword_fns.startObserving { _, s ->
					_aPasswordWasObtained()
				}
				token__obtainedCorrectExistingPassword = self.obtainedCorrectExistingPassword_fns.startObserving { _, s ->
					_aPasswordWasObtained()
				}
				token__canceledWhileEnteringExistingPassword = self.canceledWhileEnteringExistingPassword_fns.startObserving { _, s ->
					_obtainingPasswordWasCanceled()
				}
				token__canceledWhileEnteringNewPassword = self.canceledWhileEnteringNewPassword_fns.startObserving { _, s ->
					_obtainingPasswordWasCanceled()
				}
				// now that we're subscribed, initiate the pw request
				self._inThread_givenBooted_initiateGetNewOrExistingPasswordFromUserAndEmitIt()
			})
		}
	}
	private fun _inThread_givenBooted_initiateGetNewOrExistingPasswordFromUserAndEmitIt()
	{
		if (this._hasUserEnteredValidPasswordYet) {
			Log.w("Passwords", "givenBooted_initiateGetNewOrExistingPasswordFromUserAndEmitIt asked to givenBooted_initiateGetNewOrExistingPasswordFromUserAndEmitIt but already has password.")
			return // already got it
		}
		//
		// guard
		if (this.isAlreadyGettingExistingOrNewPWFromUser == true) {
			return // only need to wait for it to be obtained
		}
		this._inThread_guard_getNewOrExistingPassword()
		//
		// we'll use this in a couple places
		val isForChangePassword = false // this is simply for requesting to have the existing or a new password from the user
		val isForAuthorizingAppActionOnly = false // "
		//
		if (this._id == null) { // if the user is not unlocking an already pw-protected app
			// then we need to get a new PW from the user
			this._inThread_obtainNewPasswordFromUser( // this will also call this.unguard_getNewOrExistingPassword()
				isForChangePassword
			)
			return
		} else { // then we need to get the existing PW and check it against the encrypted message
			if (this.messageAsEncryptedDataForUnlockChallenge_base64String == null) {
				val err_str = "Code fault: Existing document but no messageAsEncryptedDataForUnlockChallenge_base64String"
				Log.e("Passwords", err_str)
				this._inThread_unguard_getNewOrExistingPassword()
				throw AssertionError(err_str) // fatal
			}
			this._inThread_getUserToEnterTheirExistingPassword(
				isForChangePassword = isForChangePassword,
				isForAuthorizingAppActionOnly = isForAuthorizingAppActionOnly // false
			) cb@{ didCancel_orNull, validationErr_orNull, obtainedPasswordString ->
				this.throwUnlessOnSyncThread()
				// we're back on the sync thread here
				if (validationErr_orNull != null) { // takes precedence over cancel
					this._inThread_unguard_getNewOrExistingPassword()
					this.erroredWhileGettingExistingPassword_fns.invoke(this, validationErr_orNull)
					return@cb
				}
				if (didCancel_orNull!!) {
					this.canceledWhileEnteringExistingPassword_fns(this, "")
					this._inThread_unguard_getNewOrExistingPassword()
					return@cb // just silently exit after unguarding
				}
				//
				var plaintextString: String?
				try {
					plaintextString = PersistableObject.new_plaintextStringFrom(
						this.messageAsEncryptedDataForUnlockChallenge_base64String!!,
						obtainedPasswordString!!
					)
				} catch (e: Exception) {
					this._inThread_unguard_getNewOrExistingPassword()
					Log.e("Passwords", "Error while decrypting message for unlock challenge: ${e} ${e.localizedMessage}")
					val err_str = this._new_incorrectPasswordValidationErrorMessageString(this.context)
					this.erroredWhileGettingExistingPassword_fns.invoke(this, err_str)
					return@cb
				}
				if (plaintextString != this.plaintextMessageToSaveForUnlockChallenges) {
					this._inThread_unguard_getNewOrExistingPassword()
					val err_str = this._new_incorrectPasswordValidationErrorMessageString(this.context)
					this.erroredWhileGettingExistingPassword_fns.invoke(this, err_str)
					return@cb
				}
				// then it's correct
				// hang onto pw and set state
				this._password = obtainedPasswordString!!
				// all done
				this._inThread_unguard_getNewOrExistingPassword()
				//
				this.obtainedCorrectExistingPassword_fns(this, "")
			}
		}
	}
	//
	// Runtime - Imperatives - Private - Requesting password from user
	private fun _inThread_guard_getNewOrExistingPassword()
	{
		this.isAlreadyGettingExistingOrNewPWFromUser = true
	}
	private fun _inThread_unguard_getNewOrExistingPassword()
	{
		this.isAlreadyGettingExistingOrNewPWFromUser = false
	}
	//
	private fun _inThread_getUserToEnterTheirExistingPassword(
		isForChangePassword: Boolean,
		isForAuthorizingAppActionOnly: Boolean,
		customNavigationBarTitle: String? = null,
		fn: ( // this will be called back on the single thread .... TODO: consider converting this into a future
			didCancel_orNull: Boolean?,
			validationErr_orNull: String?,
			obtainedPasswordString: Password?
		) -> Unit
	) {
		var _isCurrentlyLockedOut: Boolean = false
		var _unlockTimer_executor: ScheduledThreadPoolExecutor? = null
		var _unlockTimer_scheduledFuture: ScheduledFuture<*>? = null
		var _numberOfTriesDuringThisTimePeriod: Int = 0
		var _dateOf_firstPWTryDuringThisTimePeriod: Long? = System.currentTimeMillis() // initialized to current time
		fun __cancelAnyAndRebuildUnlockTimer()
		{
			val wasAlreadyLockedOut = _unlockTimer_executor != null || _unlockTimer_scheduledFuture != null
			if (_unlockTimer_scheduledFuture != null) {
				// Log.d("Passwords", "clearing existing unlock timer")
				_unlockTimer_scheduledFuture!!.cancel(true) // stop and terminate timer thread .. probably necessary every time we're done with the timer
				_unlockTimer_scheduledFuture = null // not strictly necessary
			}
			val unlockInT_s: Long = 10 // allows them to try again every T sec, but resets timer if they submit w/o waiting
			Log.d("Passwords", "\uD83D\uDEAB  Too many password entry attempts within ${unlockInT_s}s. ${if (!wasAlreadyLockedOut) "Locking out" else "Extending lockout."}.")
			if (_unlockTimer_executor == null) {
				_unlockTimer_executor = ScheduledThreadPoolExecutor(1)
			}
			val runnable = Runnable() {
				Log.d("Passwords", "⭕️  Unlocking password entry.")
				_unlockTimer_scheduledFuture!!.cancel(true) // stop and terminate timer thread .. probably necessary every time we're done with the timer
				_unlockTimer_scheduledFuture = null // ok to modify this inside the block? retain cycle?
				_unlockTimer_executor!!.shutdown() // and must be sure to shut this down too
				_unlockTimer_executor = null
				//
				_isCurrentlyLockedOut = false
				fn(null, "", null) // this is _sort_ of a hack and should be made more explicit in API but I'm sending an empty string, and not even an err_str, to clear the validation error so the user knows to try again
			}
			_unlockTimer_scheduledFuture = _unlockTimer_executor!!.schedule(
				runnable,
				unlockInT_s,
				TimeUnit.SECONDS
			)
		}
		if (isForChangePassword && isForAuthorizingAppActionOnly) {
			// both shouldn't be true
			throw AssertionError("Unexpected isForChangePassword && isForAuthorizingAppActionOnly")
		}
		// Now put request out
		this.passwordEntryDelegate!!.getUserToEnterExistingPassword(
			isForChangePassword = isForChangePassword,
			isForAuthorizingAppActionOnly = isForAuthorizingAppActionOnly,
			customNavigationBarTitle = customNavigationBarTitle
		) { didCancel_orNull, obtainedPasswordString ->
			runSync { self -> // returning to the synchronized thread for the fn callback below
				var validationErr_orNull: String? = null // so far…
				if (didCancel_orNull != true) { // so user did NOT cancel
					// user did not cancel… let's check if we need to send back a pre-emptive validation err (such as because they're trying too much)
					if (_isCurrentlyLockedOut == false) {
						if (_numberOfTriesDuringThisTimePeriod == 0) {
							_dateOf_firstPWTryDuringThisTimePeriod = System.currentTimeMillis()
						}
						_numberOfTriesDuringThisTimePeriod += 1
						if (_numberOfTriesDuringThisTimePeriod > PasswordController.maxLegal_numberOfTriesDuringThisTimePeriod) { // rhs must be > 0
							_numberOfTriesDuringThisTimePeriod = 0
							// ^- no matter what, we're going to need to reset the above state for the next 'time period'
							//
							val ms_since_firstPWTryDuringThisTimePeriod = System.currentTimeMillis() - _dateOf_firstPWTryDuringThisTimePeriod!!
							val noMoreThanNTriesWithin_ms = 30 * 1000
							if (ms_since_firstPWTryDuringThisTimePeriod > noMoreThanNTriesWithin_ms) { // enough time has passed since this group began - only reset the "time period" with tries->0 and val this pass through as valid check
								_dateOf_firstPWTryDuringThisTimePeriod = null // not strictly necessary to do here as we reset the number of tries during this time period to zero just above
								Log.d("Passwords", "There were more than ${PasswordController.maxLegal_numberOfTriesDuringThisTimePeriod} password entry attempts during this time period but the last attempt was more than ${noMoreThanNTriesWithin_ms/1000}s ago, so letting this go.")
							} else { // simply too many tries!…
								// lock it out for the next time (supposing this try does not pass)
								_isCurrentlyLockedOut = true
							}
						}
					}
					if (_isCurrentlyLockedOut == true) { // do not try to check pw - return as validation err
						Log.d("Passwords", "🚫  Received password entry attempt but currently locked out.")
						validationErr_orNull = this.context.resources.getString(R.string.as_a_security_precaution_please_wait)
						// setup or extend unlock timer - NOTE: this is pretty strict - we don't strictly need to extend the timer each time to prevent spam unlocks
						__cancelAnyAndRebuildUnlockTimer()
					}
				}
				// then regardless of whether user canceled…
				fn(
					didCancel_orNull,
					validationErr_orNull,
					obtainedPasswordString
				)
			}
		}
	}
	//
	// Runtime - Imperatives - Private - Setting/changing Password
	private fun _inThread_obtainNewPasswordFromUser(isForChangePassword: Boolean)
	{
		val wasFirstSetOfPasswordAtRuntime = this._hasUserEnteredValidPasswordYet == false // it's ok if we derive this here instead of in obtainNewPasswordFromUser because this fn will only be called, if setting the pw for the first time, if we have not yet accepted a valid PW yet
		// for possible revert:
		val old_password = this._password // this may be undefined
		val old_passwordType = this._passwordType
		//
		val captured_this = this
		this.passwordEntryDelegate!!.getUserToEnterNewPasswordAndType(
			isForChangePassword = isForChangePassword,
			enterNewPasswordAndType_cb =
			cb@{ didCancel_orNull, obtainedPasswordString, userSelectedTypeOfPassword ->
				this.runSync runSync@{ self ->
					// must get back onto sync thread
					if (didCancel_orNull == true) {
						self.canceledWhileEnteringNewPassword_fns.invoke(self, "")
						self._inThread_unguard_getNewOrExistingPassword()
						return@runSync // just silently exit after unguarding
					}
					//
					// I. Validate features of pw before trying and accepting
					if (userSelectedTypeOfPassword == PasswordType.PIN) {
						if (obtainedPasswordString!!.count() < PasswordController.minPasswordLength) { // self is too short. get back to them with a validation err by re-entering obtainPasswordFromUser_cb
							self._inThread_unguard_getNewOrExistingPassword()
							val err_str = self.context.resources.getString(R.string.enter_longer_pin)
							self.erroredWhileSettingNewPassword_fns(self, err_str)
							return@runSync // bail
						}
						// TODO: check if all numbers
						// TODO: check that numbers are not all just one number
					} else if (userSelectedTypeOfPassword == PasswordType.password) {
						if (obtainedPasswordString!!.count() < PasswordController.minPasswordLength) { // this is too short. get back to them with a validation err by re-entering obtainPasswordFromUser_cb
							self._inThread_unguard_getNewOrExistingPassword()
							val err_str = self.context.resources.getString(R.string.enter_longer_password)
							self.erroredWhileSettingNewPassword_fns.invoke(self, err_str)
							return@runSync // bail
						}
						// TODO: check if password content too weak?
					} else { // this is weird - code fault or cracking attempt?
						self._inThread_unguard_getNewOrExistingPassword()
						val err_str = self.context.resources.getString(R.string.unrecognized_password_type)
						self.erroredWhileSettingNewPassword_fns.invoke(self, err_str)
						throw AssertionError(err_str) // consider fatal
					}
					if (isForChangePassword) {
						if (self._password == obtainedPasswordString) { // they are disallowed from using change pw to enter the same pw… despite that being convenient for dev ;)
							self._inThread_unguard_getNewOrExistingPassword()
							//
							var err_str: String
							if (userSelectedTypeOfPassword == PasswordType.password) {
								err_str = self.context.resources.getString(R.string.enter_fresh_password)
							} else if (userSelectedTypeOfPassword == PasswordType.PIN) {
								err_str = self.context.resources.getString(R.string.enter_fresh_pin)
							} else {
								err_str = self.context.resources.getString(R.string.unrecognized_password_type)
								throw AssertionError(err_str) // considered fatal
							}
							self.erroredWhileSettingNewPassword_fns.invoke(self, err_str)
							return@runSync // bail
						}
					}
					//
					// II. hang onto new pw, pw type, and state(s)
					Log.d("Passwords", "Obtained ${userSelectedTypeOfPassword!!} ${obtainedPasswordString!!.length} chars long")
					self._password = obtainedPasswordString!!
					self._passwordType = userSelectedTypeOfPassword!!
					//
					// III. finally, save doc (and unlock on success) so we know a pw has been entered once before
					val err_str = self._inThread_saveToDisk()
					if (err_str != null) {
						self._inThread_unguard_getNewOrExistingPassword()
						if (wasFirstSetOfPasswordAtRuntime && self._password != null) { // is this correct?
							throw AssertionError("Unexpected wasFirstSetOfPasswordAtRuntime && self._password != null")
						}
						self._password = old_password // they'll have to try again - and revert to old pw rather than null for changePassword (should be null for first pw set)
						self._passwordType = old_passwordType
						//
						self.erroredWhileSettingNewPassword_fns.invoke(self, err_str!!)
						return@runSync
					}
					// detecting & emiting first set or handling result of change saves
					if (wasFirstSetOfPasswordAtRuntime) {
						self._inThread_unguard_getNewOrExistingPassword()
						// specific emit
						self.setFirstPasswordDuringThisRuntime_fns.invoke(self, "")
						// general purpose emit
						self.obtainedNewPassword_fns.invoke(self, "")
						return@runSync // prevent fallthough
					}
					// then, it's a change password
					val changePassword_err_orNull = self._inThread_tellRegistrants_doChangePassword() // returns error
					if (changePassword_err_orNull == null) { // actual success - we can return early
						self._inThread_unguard_getNewOrExistingPassword()
						// specific emit
						self.registrantsAllChangedPassword_fns.invoke(self, "")
						// general purpose emit
						self.obtainedNewPassword_fns.invoke(self, "")
						//
						return@runSync
					}
					// try to revert save files to old password...
					self._password = old_password // first revert, so consumers can read reverted value
					self._passwordType = old_passwordType
					//
					val revert_save_errStr_orNull = self._inThread_saveToDisk()
					if (revert_save_errStr_orNull != null) {
						assert(false) // Couldn't saveToDisk to revert failed changePassword... in debug mode, trigger
						// ^-- asserts aren't enabled in testing nor debug mode .. what can this be replaced with?
					} else { // continue trying to revert
						val revert_registrantsChangePw_err_orNull = self._inThread_tellRegistrants_doChangePassword() // this may well fail
						if (revert_registrantsChangePw_err_orNull != null) {
							assert(false) // Some registrants couldn't revert failed changePassword; in debug mode, treat this as fatal
							// ^-- asserts aren't enabled in testing nor debug mode .. what can this be replaced with?
						} else {
							// revert successful
						}
					}
					// finally, notify of error while changing password
					self._inThread_unguard_getNewOrExistingPassword() // important
					self.erroredWhileSettingNewPassword_fns.invoke(self, changePassword_err_orNull) // the original changePassword_err_orNull
				}
			}
		)
	}
	//
	// Imperatives - Persistence
	private fun _inThread_saveToDisk(): String? // err_str?
	{
		var err_str: String? = null
		runSync runSync@{ self ->
			if (this._password == null) {
				err_str = "Code fault: _inThread_saveToDisk musn't be called until a password has been set"
				return@runSync
			} else {
				val plaintextString = this.plaintextMessageToSaveForUnlockChallenges
				val encrypted_base64String = PersistableObject.new_encryptedStringFrom(plaintextString, this._password!!)
				this.messageAsEncryptedDataForUnlockChallenge_base64String = encrypted_base64String // it's important that we hang onto this in memory so we can access it if we need to change the password later
				if (this._id == null) {
					this._id = DocumentFileDescription.new_documentId()
				}
				val persistableDocument = mapOf<String, Any>(
					DictKey._id.rawValue to this._id!!,
					DictKey.passwordType.rawValue to this._passwordType!!.rawValue,
					DictKey.messageAsEncryptedDataForUnlockChallenge_base64String.rawValue to this.messageAsEncryptedDataForUnlockChallenge_base64String!!
				)
				val documentFileString = PersistableObject.new_plaintextJSONStringFromDocumentDict(persistableDocument)
				err_str = DocumentPersister.Write(
					documentFileWithString = documentFileString,
					id = this._id!!,
					collectionName = this.collectionName
				)
				if (err_str != null) {
					Log.e("Passwords", "Error while persisting ${this}: ${err_str}")
				}
			}
		}
		return err_str
	}
	//
	// Imperatives - Coordinated Events - Registration
	fun addRegistrantForChangePassword(registrant: ChangePasswordRegistrant)
	{
		runSync { self ->
			self._inThread_addRegistrantTo(
				registrant = registrant,
				mutable_weakRefsTo_registrants = this.weakRefsTo_changePasswordRegistrants
			)
		}
	}
	fun removeRegistrantForChangePassword(registrant: ChangePasswordRegistrant)
	{
		runSync { self ->
			self._inThread_removeRegistrantFrom(
				registrant = registrant,
				mutable_weakRefsTo_registrants = this.weakRefsTo_changePasswordRegistrants
			)
		}
	}
	fun addRegistrantForDeleteEverything(registrant: DeleteEverythingRegistrant)
	{
		runSync { self ->
			self._inThread_addRegistrantTo(
				registrant = registrant,
				mutable_weakRefsTo_registrants = this.weakRefsTo_deleteEverythingRegistrants
			)
		}
	}
	fun removeRegistrantForDeleteEverything(registrant: DeleteEverythingRegistrant)
	{
		runSync { self ->
			self._inThread_removeRegistrantFrom(
				registrant = registrant,
				mutable_weakRefsTo_registrants = this.weakRefsTo_deleteEverythingRegistrants
			)
		}
	}
	private fun _inThread_addRegistrantTo(
		registrant: PasswordControllerEventParticipant,
		mutable_weakRefsTo_registrants: MutableList<WeakRefTo_EventParticipant>
	) {
		mutable_weakRefsTo_registrants.add(
			WeakRefTo_EventParticipant(value = WeakReference(registrant))
		)
	}
	private fun _inThread_removeRegistrantFrom(
		registrant: PasswordControllerEventParticipant,
		mutable_weakRefsTo_registrants: MutableList<WeakRefTo_EventParticipant>
	): Boolean {
		var index: Int? = null
		this._inThread_iterateRegistrants(
			registrants = mutable_weakRefsTo_registrants,
			fn = { this_index, this_registrant ->
				if (isEqual(registrant, this_registrant)) {
					index = this_index
					false
				} else {
					true
				}
			}
		)
		if (index == null) {
			throw AssertionError("Registrant is not registered")
		}
		Log.d("Passwords", "Removing registrant for 'ChangePassword': ${registrant}")
		mutable_weakRefsTo_registrants.removeAt(index!!)
		//
		return true
	}
	private fun _inThread_iterateRegistrants(
		registrants: List<WeakRefTo_EventParticipant>,
		fn: (this_index: Int, this_registrant: PasswordControllerEventParticipant) -> Boolean
	) {
		var index: Int? = null
		for (this_index in registrants.indices) {
			val this_weakRefTo_registrant = registrants[this_index]
			if (this_weakRefTo_registrant.value == null) {
				continue // skip - has dealloced somewhere (TODO: maybe remove from list?)
			}
			val this_registrant = this_weakRefTo_registrant.value!!.get()
			if (this_registrant == null) {
				continue // skip - has dealloced somewhere (TODO: maybe remove from list?)
			}
			val shouldNotBreak = fn(this_index, this_registrant)
			if (shouldNotBreak == false) {
				break
			}
		}
	}
	private fun _inThread_tellRegistrants_doDeleteEverything(): String? // first encountered err_str
	{
		var err_str: String? = null
		this._inThread_iterateRegistrants(
			registrants = this.weakRefsTo_deleteEverythingRegistrants,
			fn = { this_index, this_registrant ->
				val this__err_str = (this_registrant as DeleteEverythingRegistrant).passwordController_DeleteEverything()
				if (this__err_str != null) {
					err_str = this__err_str
					false // break
				} else {
					true
				}
			}
		)
		return err_str
	}
	private fun _inThread_tellRegistrants_doChangePassword(): String? // err_str
	{
		var err_str: String? = null
		this._inThread_iterateRegistrants(
			registrants = this.weakRefsTo_changePasswordRegistrants,
			fn = { this_index, this_registrant ->
				val this__err_str = (this_registrant as ChangePasswordRegistrant).passwordController_ChangePassword()
				if (this__err_str != null) {
					err_str = this__err_str
					false // break
				} else {
					true
				}
			}
		)
		return err_str
	}
	//
	// Imperatives - Coordinated Events
	fun initiate_changePassword()
	{
		this.onceBooted cb@{ self -> // now we're on thread
			if (self._hasUserEnteredValidPasswordYet == false) {
				val err_str = "initiate_changePassword called but hasUserEnteredValidPasswordYet == false. This should be disallowed in the UI"
				throw AssertionError(err_str)
			}
			// guard
			if (self.isAlreadyGettingExistingOrNewPWFromUser == true) {
				val err_str = "initiate_changePassword called but isAlreadyGettingExistingOrNewPWFromUser == true. This should be precluded in the UI"
				throw AssertionError(err_str)
				// though only need to wait for it to be obtained
			}
			self._inThread_guard_getNewOrExistingPassword()
			//
			// ^-- we're relying on having checked above that user has entered a valid pw already
			val isForChangePassword = true // we'll use this in a couple places
			self._inThread_getUserToEnterTheirExistingPassword(
				isForChangePassword = isForChangePassword,
				isForAuthorizingAppActionOnly = false,
				fn = cb2@{ didCancel_orNull, validationErr_orNull, entered_existingPassword ->
					this.throwUnlessOnSyncThread()
					// back on the sync thread here..
					if (validationErr_orNull != null) { // takes precedence over cancel
						self._inThread_unguard_getNewOrExistingPassword()
						self.errorWhileChangingPassword_fns.invoke(self, validationErr_orNull)
						return@cb2
					}
					if (didCancel_orNull == true) {
						self._inThread_unguard_getNewOrExistingPassword()
						self.canceledWhileChangingPassword_fns.invoke(self, "")
						return@cb2 // just silently exit after unguarding
					}
					val isGoodEnteredPassword = self._isCorrect(
						existingPassword = self._password!!,
						enteredPassword = entered_existingPassword!!
					)
					if (isGoodEnteredPassword == false) {
						self._inThread_unguard_getNewOrExistingPassword()
						val err_str = self._new_incorrectPasswordValidationErrorMessageString(self.context)
						self.errorWhileChangingPassword_fns.invoke(self, err_str)
						return@cb2
					}
					// passwords match checked as necessary, we can proceed
					self._inThread_obtainNewPasswordFromUser(
						isForChangePassword = isForChangePassword
					)
				}
			)
		}
	}
	fun initiate_deleteEverything()
	{ // this is used as a central initiation/sync point for delete everything like user idle
		// maybe it should be moved, maybe not.
		// And note we're assuming here the PW has been entered already.
		if (this.hasUserSavedAPassword != true) {
			throw AssertionError("initiateDeleteEverything() called but hasUserSavedAPassword != true. This should be disallowed in the UI.")
		}
		runSync { self ->
			self._inThread_deconstructBootedStateAndClearPassword(
				isForADeleteEverything = true,
				optl__hasFiredWill_fn = hasFiredWill_cb@{ cb ->
					this.throwUnlessOnSyncThread()
					// we're back on the sync thread here
					// reset state cause we're going all the way back to pre-boot
					self._hasBooted = false // require this pw controller to boot
					self._password = null // this is redundant but is here for clarity
					self._id = null
					self.messageAsEncryptedDataForUnlockChallenge_base64String = null
					//
					// first try to have registrants delete everything
					val registrant__first_err_str = self._inThread_tellRegistrants_doDeleteEverything()
					if (registrant__first_err_str != null) {
						cb(registrant__first_err_str)
						return@hasFiredWill_cb
					}
					//
					// now we can go ahead and delete the pw record
					val (err_str, _) = DocumentPersister.RemoveAllDocuments(collectionName = self.collectionName)
					if (err_str != null) {
						cb(err_str)
						return@hasFiredWill_cb
					}
					Log.d("Passwords", "Deleted password record.")
					//
					self.initializeRuntimeAndBoot() // now trigger a boot before we call cb (tho we could do it after - consumers will wait for boot)
					// ^-- this will block and also re-enter the sync thread context .. not 100% optimal but should be fine
					cb(null)
				},
				optl__fn = fn_cb@{ err_str ->
					this.throwUnlessOnSyncThread()
					// this is called on the sync thread
					if (err_str != null) {
						Log.e("Passwords", "Error while deleting everything: ${err_str}")
						this.didErrorWhileDeletingEverything_fns.invoke(this, err_str)
						// v---- this is commented because while it would work well enough for production, it breaks tests looking for a failed deleteEverything
//						throw AssertionError("Errored while deleting everything. Restart app. Error: ${err_str}")
						// just fatalError here so app can be relaunched and regain sane state
						return@fn_cb // must exit here!
					}
					this.havingDeletedEverything_didDeconstructBootedStateAndClearPassword_fns.invoke(this, "")
				}
			)
		}
	}
	//
	// Runtime - Imperatives - Boot-state deconstruction/teardown
	fun _inThread_deconstructBootedStateAndClearPassword(
		isForADeleteEverything: Boolean,
		optl__hasFiredWill_fn: ((
			cb: (err_str: String?) -> Unit
		) -> Unit)?,
		optl__fn: (( // this will be called on the sync thread
			err_str: String?
		) -> Unit)?
	) {
		var hasFiredWill_fn: (cb: ((err_str: String?) -> Unit)) -> Unit
		var fn: (err_str: String?) -> Unit
		if (optl__hasFiredWill_fn != null) {
			hasFiredWill_fn = optl__hasFiredWill_fn!!
		} else {
			hasFiredWill_fn = { cb -> cb(null) /* must call cb even in dummy fn */ }
		}
		if (optl__fn != null) {
			fn = optl__fn!!
		} else {
			fn = { err_str -> }
		}
		//
		// TODO:? do we need to cancel any waiting functions here? not sure it would be possible to have any (unless code fault)…… we'd only deconstruct the booted state and pop the enter pw screen here if we had already booted before - which means there shouldn't be such waiting functions - so maybe assert that here - which requires hanging onto those functions somehow
		// indicate to consumers they should tear down and await the "did" event to re-request
		this.willDeconstructBootedStateAndClearPassword_fns.invoke(this, isForADeleteEverything)
		hasFiredWill_fn(
			cb@{ err_str ->
				runSync inner_runSync@{ innerSelf -> // must get back onto sync thread
					if (err_str != null) {
						fn(err_str)
						return@inner_runSync
					}
					// trigger deconstruction of booted state and require password
					innerSelf._password = null // clear pw in memory
					innerSelf._hasBooted = false // require this pw controller to boot
					innerSelf._id = null
					innerSelf.messageAsEncryptedDataForUnlockChallenge_base64String = null
					//
					// we're not going to call WhenBootedAndPasswordObtained_PasswordAndType because consumers will call it for us after they tear down their booted state with the "will" event and try to boot/decrypt again when they get this "did" event
					innerSelf.didDeconstructBootedStateAndClearPassword_fns.invoke(innerSelf, "")
					//
					innerSelf.initializeRuntimeAndBoot() // now trigger a boot before we call cb (tho we could do it after - consumers will wait for boot)
					//
					fn(null)
				}
			}
		)
	}
	//
	// Runtime - Imperatives - Password verification
	fun initiate_verifyUserAuthenticationForAction(
		customNavigationBarTitle: String? = null,
		canceled_fn: (() -> Unit)?, // NOTE: this compiles b/c optional closures are treated as @escaping
		entryAttempt_succeeded_fn: (() -> Unit) // required
	) {
		this.onceBooted(
			fn = cb@{ self -> // now we're on the threa
				if (self._hasUserEnteredValidPasswordYet == false) {
					val err_etr = "initiate_verifyUserAuthenticationForAction called but hasUserEnteredValidPasswordYet == false. This should be disallowed in the UI"
					throw AssertionError(err_etr)
//					return@cb
				}
				// guard
				if (self.isAlreadyGettingExistingOrNewPWFromUser == true) {
					val err_str = "initiate_changePassword called but isAlreadyGettingExistingOrNewPWFromUser == true. This should be precluded in the UI"
					throw AssertionError(err_str)
					// only need to wait for it to be obtained
//					return@cb
				}
				self._inThread_guard_getNewOrExistingPassword()
				//
				// ^-- we're relying on having checked above that user has entered a valid pw already
				fun _proceedTo_verifyVia_passphrase()
				{
					self._inThread_getUserToEnterTheirExistingPassword(
						isForChangePassword = false,
						isForAuthorizingAppActionOnly = true,
						customNavigationBarTitle = customNavigationBarTitle,
						fn = enterExisting_fn@{ didCancel_orNull, validationErr_orNull, entered_existingPassword ->
							this.throwUnlessOnSyncThread()
							// we're back on the sync thread here
							if (validationErr_orNull != null) { // takes precedence over cancel
								self._inThread_unguard_getNewOrExistingPassword()
								self.errorWhileAuthorizingForAppAction_fns.invoke(self, validationErr_orNull)
								return@enterExisting_fn
							}
							if (didCancel_orNull == true) {
								self._inThread_unguard_getNewOrExistingPassword()
								//
								// currently there's no need of a .canceledWhileAuthorizingForAppAction note post here
								canceled_fn?.let { it() } // but must call cb
								//
								return@enterExisting_fn // just silently exit after unguarding
							}
							val isGoodEnteredPassword = self._isCorrect(
								existingPassword = self._password!!,
								enteredPassword = entered_existingPassword!!
							)
							if (isGoodEnteredPassword == false) {
								self._inThread_unguard_getNewOrExistingPassword()
								val err_str = self._new_incorrectPasswordValidationErrorMessageString(self.context)
								self.errorWhileAuthorizingForAppAction_fns.invoke(self, err_str)
								return@enterExisting_fn
							}
							//
							self._inThread_unguard_getNewOrExistingPassword() // must be called
							self.successfullyAuthenticatedForAppAction_fns.invoke(self, "") // this must be posted so the PresentationController can dismiss the entry modal
							entryAttempt_succeeded_fn()
						}
					)
				}
/*				val tryBiometrics = SettingsController.shared.authentication__tryBiometric
					// now see if we can use biometrics
					if tryBiometrics == false {
*/
						_proceedTo_verifyVia_passphrase()
/*
						return // so we don't have to wrap the whole following branch in an if
					}
				if #available(iOS 8.0, macOS 10.12.1, *) {
					func _handle(receivedLAError error: NSError)
					{
						val code = LAError.Code(rawValue: error.code)!
						switch code {
							case .biometryNotEnrolled, // this case, go straight to pw
							.biometryNotAvailable, // straight to pw
							.biometryLockout, // this case, because we want to present a fallback method plus the cancel button
							.authenticationFailed, // is including this correct?
							.passcodeNotSet, // go straight to pw?
							.notInteractive,
							.userFallback
							:
							_proceedTo_verifyVia_passphrase()
							break
							// compiler says these will never be executed, that a default won't either, /and/ that switch must be exhaustive. so, opted to just enumerate the cases here to retain compiler check for exhaustiveness
							case .touchIDNotEnrolled,
							.touchIDNotAvailable,
							.touchIDLockout: // this case, because we want to present a fallback method plus the cancel button
							_proceedTo_verifyVia_passphrase()
							break
							case .systemCancel,
							.appCancel,
							.userCancel:
							this.unguard_getNewOrExistingPassword() // must be called at function terminus
							canceled_fn?()
							break
							case .invalidContext: // error.. fatal?
							fatalError("LAContext passed to this call has been previously invalidated.")
							break
						}
					}
					val laContext = LAContext()
					val policy: LAPolicy = .deviceOwnerAuthenticationWithBiometrics
					var authError: NSError?
					if laContext.canEvaluatePolicy(policy, error: &authError) {
					val reason_localizedString = NSLocalizedString(customNavigationBarTitle ?? "Authenticate to allow MyMonero to perform this action.", comment: "")
					laContext.evaluatePolicy(policy, localizedReason: reason_localizedString)
					{ [weak this] (success, evaluateError) in
						guard val thisthis = this else {
						return
					}
						func ___proceed()
						{
							if success {
								thisthis.unguard_getNewOrExistingPassword() // must be called at function terminus
								entryAttempt_succeeded_fn() // consider this an authentication
							} else { // User did not authenticate successfully
								_handle(receivedLAError: evaluateError! as NSError)
							}
						}
						if Thread.isMainThread == false { // has a tendency to call back on a bg thread
							DispatchQueue.main.async {
								___proceed()
							}
						} else {
							___proceed()
						}
					}
				} else { // Could not evaluate policy
					_handle(receivedLAError: authError!)
					return
				}
				} else {
					_proceedTo_verifyVia_passphrase()
				}
				*/
			}
		)
	}
	//
	// Delegation - User Idle
	private fun _userDidBecomeIdle()
	{
		this.runSync runSync@{ self ->
			if (self.hasUserSavedAPassword == false) {
				// nothing to do here because the app is not unlocked and/or has no data which would be locked
				Log.d("Passwords", "User became idle but no password has ever been entered/no saved data should exist.")
				return@runSync
			} else if (self._hasUserEnteredValidPasswordYet == false) {
				// user has saved data but hasn't unlocked the app yet
				Log.d("Passwords", "User became idle and saved data/pw exists, but user hasn't unlocked app yet.")
				return@runSync
			}
			// User having become idle -> teardown booted state and require pw
			self._inThread_deconstructBootedStateAndClearPassword(
				isForADeleteEverything = false,
				optl__hasFiredWill_fn = null,
				optl__fn = null
			)
		}
	}
}