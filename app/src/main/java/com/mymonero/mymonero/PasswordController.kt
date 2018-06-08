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

import android.util.Log
import junit.framework.Assert
import tgio.rncryptor.RNCryptorNative
import java.util.Timer
import kotlin.concurrent.schedule
import java.lang.ref.WeakReference

typealias Password = String

public enum class PasswordType(val rawValue: String)
{
	PIN("PIN"),
	password("password");
	//
	var humanReadableString: String = this.rawValue // TODO: return localized
	var capitalized_humanReadableString: String = this.humanReadableString.capitalize()
	val invalidEntry_humanReadableString: String
		get() {
			val code = if (this == PIN) R.string.incorrect_PIN else R.string.incorrect_password
			//
			return PasswordController.context.resources.getString(code) // TODO: pass context by injection somehow?
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
	var password: Password?
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
object PasswordController: PasswordProvider
{
	//
	// Constants
	val minPasswordLength = 6
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
	// Properties
	var hasBooted = false
	var _id: DocumentId? = null
	override var password: Password? = null
	lateinit var passwordType: PasswordType // it will default to .password per init
	val hasUserSavedAPassword: Boolean
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
	var messageAsEncryptedDataForUnlockChallenge_base64String: String? = null
	var isAlreadyGettingExistingOrNewPWFromUser: Boolean? = null
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
		if (this.passwordEntryDelegate == null) {
			throw AssertionError("clearPasswordEntryDelegate called but no passwordEntryDelegate exists") // fatal
		}
		if (isEqual(this.passwordEntryDelegate!!, from_existingDelegate) == false) {
			// fatal
			throw AssertionError("clearPasswordEntryDelegate called but passwordEntryDelegate does not match")
			return
		}
		this.passwordEntryDelegate = null
	}
	//
	// genericizing the member type on these does come with the downside of loosening type safety:
	private var weakRefsTo_changePasswordRegistrants = mutableListOf<WeakRefTo_EventParticipant>()
	private var weakRefsTo_deleteEverythingRegistrants = mutableListOf<WeakRefTo_EventParticipant>()
	//
	// Properties - Convenience
	val context = MainApplication.applicationContext()
	//
	// Accessors - Runtime - Derived properties
	val hasUserEnteredValidPasswordYet: Boolean
		get() = this.password != null
	val isUserChangingPassword: Boolean
		get() = this.hasUserEnteredValidPasswordYet == true && this.isAlreadyGettingExistingOrNewPWFromUser == true

	val new_incorrectPasswordValidationErrorMessageString: String
		get() = this.passwordType.invalidEntry_humanReadableString
	//
	// Accessors - Common
	private fun withExistingPassword_isCorrect(enteredPassword: String): Boolean
	{ // NOTE: This function should most likely remain fileprivate so that it is not cheap to check PW and must be done through the PW entry UI (by way of methods on PasswordController)
		// FIXME/TODO: is this check too weak? is it better to try decrypt and check hmac mismatch?
		//
		return this.password!! == enteredPassword // force unwrap this.password so it cannot be equal to a null passed as arg despite present method sig decl
	}
	//
	// Internal - Properties - Constants - Persistence
	val collectionName = "PasswordMeta"
	val plaintextMessageToSaveForUnlockChallenges = "this is just a string that we'll use for checking whether a given password can unlock an encrypted version of this very message"
	//
	// Internal - Lifecycle - Singleton Init
	init {
		this.setup()
	}
	fun setup()
	{
		this.startObserving_userIdle()
		this.initializeRuntimeAndBoot()
	}
	fun startObserving_userIdle()
	{
		// TODO:
//		NotificationCenter.default.addObserver(this, selector: #selector(UserIdle_userDidBecomeIdle), name: UserIdle.NotificationNames.userDidBecomeIdle.notificationName, object: null)
	}
	fun initializeRuntimeAndBoot()
	{
		if (this.hasBooted == true) { // fatal
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
		if (documentContentStrings_count  > 1) {
			throw AssertionError("Unexpected state while loading ${this.collectionName}: more than one saved doc.")
		}
		fun _proceedTo_load(documentJSON: DocumentJSON)
		{
			this._id = documentJSON[DictKey._id.rawValue] as? DocumentId
			val existing_passwordType = documentJSON[DictKey.passwordType.rawValue] as? String
			val passwordType_rawValue = if (existing_passwordType != null) existing_passwordType else PasswordType.password.rawValue
			this.passwordType = PasswordType.valueOf(passwordType_rawValue)
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
			this.hasBooted = true
			this._callAndFlushAllBlocksWaitingForBootToExecute()
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
	var __blocksWaitingForBootToExecute = mutableListOf<() -> Unit>()
	// NOTE: onceBooted() exists because even though init()->setup() is synchronous, we need to be able to tear down and reconstruct the passwordController booted state, e.g. on user idle and delete everything
	fun onceBooted(fn: (() -> Unit))
	{
		if (this.hasBooted == true) {
			fn()
			return
		}
		if (this.__blocksWaitingForBootToExecute == null) {
			this.__blocksWaitingForBootToExecute = mutableListOf<() -> Unit>()
		}
		this.__blocksWaitingForBootToExecute.add(fn)
	}
	fun _callAndFlushAllBlocksWaitingForBootToExecute()
	{
		if (this.__blocksWaitingForBootToExecute == null) {
			return
		}
		//
		// could check list of blocks empty here but not a huge win
		//
		val blocks = mutableListOf<() -> Unit>()
		this.__blocksWaitingForBootToExecute = mutableListOf<() -> Unit>() // flash so the old blocks get freed - and we can do this before calling the blocks
		for (block in blocks) {
			block()
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
			fn(this.password!!, this.passwordType)
		}
		fun callBackHavingCanceled()
		{
			userCanceled_fn?.let {
				it()
			}
		}
		if (this.hasUserEnteredValidPasswordYet) {
			callBackHavingObtainedPassword()
			return
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
		this.onceBooted({
			// hang onto tokens so we can unsub
			token__obtainedNewPassword = this.obtainedNewPassword_fns.startObserving { _, s ->
				_aPasswordWasObtained()
			}
			token__obtainedCorrectExistingPassword = this.obtainedCorrectExistingPassword_fns.startObserving { _, s ->
				_aPasswordWasObtained()
			}
			token__canceledWhileEnteringExistingPassword = canceledWhileEnteringExistingPassword_fns.startObserving { _, s ->
				_obtainingPasswordWasCanceled()
			}
			token__canceledWhileEnteringNewPassword = this.canceledWhileEnteringNewPassword_fns.startObserving { _, s ->
				_obtainingPasswordWasCanceled()
			}
			// now that we're subscribed, initiate the pw request
			this.givenBooted_initiateGetNewOrExistingPasswordFromUserAndEmitIt()
		})
	}
	fun givenBooted_initiateGetNewOrExistingPasswordFromUserAndEmitIt()
	{
		if (this.hasUserEnteredValidPasswordYet) {
			Log.w("Passwords", "givenBooted_initiateGetNewOrExistingPasswordFromUserAndEmitIt asked to givenBooted_initiateGetNewOrExistingPasswordFromUserAndEmitIt but already has password.")
			return // already got it
		}
		//
		// guard
		if (this.isAlreadyGettingExistingOrNewPWFromUser == true) {
			return // only need to wait for it to be obtained
		}
		this.guard_getNewOrExistingPassword()
		//
		// we'll use this in a couple places
		val isForChangePassword = false // this is simply for requesting to have the existing or a new password from the user
		val isForAuthorizingAppActionOnly = false // "
		//
		if (this._id == null) { // if the user is not unlocking an already pw-protected app
			// then we need to get a new PW from the user
			this.obtainNewPasswordFromUser( // this will also call this.unguard_getNewOrExistingPassword()
				isForChangePassword
			)
			return
		} else { // then we need to get the existing PW and check it against the encrypted message
			//
			if (this.messageAsEncryptedDataForUnlockChallenge_base64String == null) {
				val err_str = "Code fault: Existing document but no messageAsEncryptedDataForUnlockChallenge_base64String"
				Log.e("Passwords", err_str)
				this.unguard_getNewOrExistingPassword()
				throw AssertionError(err_str) // fatal
			}
			this._getUserToEnterTheirExistingPassword(
				isForChangePassword = isForChangePassword,
				isForAuthorizingAppActionOnly = isForAuthorizingAppActionOnly // false
			) cb@ { didCancel_orNull, validationErr_orNull, obtainedPasswordString ->
				if (validationErr_orNull != null) { // takes precedence over cancel
					this.unguard_getNewOrExistingPassword()
					this.erroredWhileGettingExistingPassword_fns.invoke(this, validationErr_orNull)
					return@cb
				}
				if (didCancel_orNull!!) {
					this.canceledWhileEnteringExistingPassword_fns(this, "")
					this.unguard_getNewOrExistingPassword()
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
					this.unguard_getNewOrExistingPassword()
					Log.e("Passwords", "Error while decrypting message for unlock challenge: ${e} ${e.localizedMessage}")
					val err_str = this.new_incorrectPasswordValidationErrorMessageString
					this.erroredWhileGettingExistingPassword_fns.invoke(this, err_str)
					return@cb
				}
				if (plaintextString != this.plaintextMessageToSaveForUnlockChallenges) {
					this.unguard_getNewOrExistingPassword()
					val err_str = this.new_incorrectPasswordValidationErrorMessageString
					this.erroredWhileGettingExistingPassword_fns.invoke(this, err_str)
					return@cb
				}
				// then it's correct
				// hang onto pw and set state
				this.password = obtainedPasswordString!!
				// all done
				this.unguard_getNewOrExistingPassword()
				//
				this.obtainedCorrectExistingPassword_fns(this, "")
			}
		}
	}
	//
	// Runtime - Imperatives - Private - Requesting password from user
	fun guard_getNewOrExistingPassword()
	{
		this.isAlreadyGettingExistingOrNewPWFromUser = true
	}
	fun unguard_getNewOrExistingPassword()
	{
		this.isAlreadyGettingExistingOrNewPWFromUser = false
	}
	fun _getUserToEnterTheirExistingPassword(
		isForChangePassword: Boolean,
		isForAuthorizingAppActionOnly: Boolean,
		customNavigationBarTitle: String? = null,
		fn: (
			didCancel_orNull: Boolean?,
			validationErr_orNull: String?,
			obtainedPasswordString: Password?
		) -> Unit
	) {
		var _isCurrentlyLockedOut: Boolean = false
		var _unlockTimer: Timer? = null
		var _numberOfTriesDuringThisTimePeriod: Int = 0
		var _dateOf_firstPWTryDuringThisTimePeriod: Long? = System.currentTimeMillis() // initialized to current time
		fun __cancelAnyAndRebuildUnlockTimer()
		{
			val wasAlreadyLockedOut = _unlockTimer != null
			if (_unlockTimer != null) {
				// Log.d("Passwords", "clearing existing unlock timer")
				_unlockTimer!!.cancel() // stop and terminate timer thread .. probably necessary every time we're done with the timer
				_unlockTimer!!.purge()
				_unlockTimer = null // not strictly necessary
			}
			val unlockInT_s: Long = 10 // allows them to try again every T sec, but resets timer if they submit w/o waiting
			Log.d("Passwords", "Too many password entry attempts within ${unlockInT_s}s. ${if (!wasAlreadyLockedOut) "Locking out" else "Extending lockout."}.")
			_unlockTimer = Timer("unlockTimer", false)
			_unlockTimer!!.schedule(delay = unlockInT_s, action = {
				Log.d("Passwords", "⭕️  Unlocking password entry.")
				_unlockTimer!!.cancel() // stop and terminate timer thread .. probably necessary every time we're done with the timer
				_unlockTimer!!.purge()
				_unlockTimer = null // ok to modify this inside the block? retain cycle?

				_isCurrentlyLockedOut = false
				fn(null, "", null) // this is _sort_ of a hack and should be made more explicit in API but I'm sending an empty string, and not even an err_str, to clear the validation error so the user knows to try again
			}
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
			var validationErr_orNull: String? = null // so far…
			if (didCancel_orNull != true) { // so user did NOT cancel
				// user did not cancel… let's check if we need to send back a pre-emptive validation err (such as because they're trying too much)
				if (_isCurrentlyLockedOut == false) {
					if (_numberOfTriesDuringThisTimePeriod == 0) {
						_dateOf_firstPWTryDuringThisTimePeriod = System.currentTimeMillis()
					}
					_numberOfTriesDuringThisTimePeriod += 1
					val maxLegal_numberOfTriesDuringThisTimePeriod = 5
					if (_numberOfTriesDuringThisTimePeriod > maxLegal_numberOfTriesDuringThisTimePeriod) { // rhs must be > 0
						_numberOfTriesDuringThisTimePeriod = 0
						// ^- no matter what, we're going to need to reset the above state for the next 'time period'
						//
						val s_since_firstPWTryDuringThisTimePeriod = System.currentTimeMillis() - _dateOf_firstPWTryDuringThisTimePeriod!!
						val noMoreThanNTriesWithin_s = 30
						if (s_since_firstPWTryDuringThisTimePeriod > noMoreThanNTriesWithin_s) { // enough time has passed since this group began - only reset the "time period" with tries->0 and val this pass through as valid check
							_dateOf_firstPWTryDuringThisTimePeriod = null // not strictly necessary to do here as we reset the number of tries during this time period to zero just above
							Log.d("Passwords", "There were more than ${maxLegal_numberOfTriesDuringThisTimePeriod} password entry attempts during this time period but the last attempt was more than ${noMoreThanNTriesWithin_s}s ago, so letting this go.")
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
	//
	// Runtime - Imperatives - Private - Setting/changing Password
	fun obtainNewPasswordFromUser(isForChangePassword: Boolean)
	{
		val wasFirstSetOfPasswordAtRuntime = this.hasUserEnteredValidPasswordYet == false // it's ok if we derive this here instead of in obtainNewPasswordFromUser because this fn will only be called, if setting the pw for the first time, if we have not yet accepted a valid PW yet
		// for possible revert:
		val old_password = this.password // this may be undefined
		val old_passwordType = this.passwordType
		//
		this.passwordEntryDelegate!!.getUserToEnterNewPasswordAndType(
			isForChangePassword = isForChangePassword,
			enterNewPasswordAndType_cb =
			cb@ { didCancel_orNull, obtainedPasswordString, userSelectedTypeOfPassword ->
				if (didCancel_orNull == true) {
					this.canceledWhileEnteringNewPassword_fns.invoke(this, "")
					this.unguard_getNewOrExistingPassword()
					return@cb // just silently exit after unguarding
				}
				//
				// I. Validate features of pw before trying and accepting
				if (userSelectedTypeOfPassword == PasswordType.PIN) {
					if (obtainedPasswordString!!.count() < PasswordController.minPasswordLength) { // this is too short. get back to them with a validation err by re-entering obtainPasswordFromUser_cb
						this.unguard_getNewOrExistingPassword()
						val err_str = this.context.resources.getString(R.string.enter_longer_pin)
						this.erroredWhileSettingNewPassword_fns(this, err_str)
						return@cb // bail
					}
					// TODO: check if all numbers
					// TODO: check that numbers are not all just one number
				} else if (userSelectedTypeOfPassword == PasswordType.password) {
					if (obtainedPasswordString!!.count() < PasswordController.minPasswordLength) { // this is too short. get back to them with a validation err by re-entering obtainPasswordFromUser_cb
						this.unguard_getNewOrExistingPassword()
						val err_str = this.context.resources.getString(R.string.enter_longer_password)
						this.erroredWhileSettingNewPassword_fns.invoke(this, err_str)
						return@cb // bail
					}
					// TODO: check if password content too weak?
				} else { // this is weird - code fault or cracking attempt?
					this.unguard_getNewOrExistingPassword()
					val err_str = this.context.resources.getString(R.string.unrecognized_password_type)
					this.erroredWhileSettingNewPassword_fns.invoke(this, err_str)
					throw AssertionError(err_str) // consider fatal
				}
				if (isForChangePassword) {
					if (this.password == obtainedPasswordString) { // they are disallowed from using change pw to enter the same pw… despite that being convenient for dev ;)
						this.unguard_getNewOrExistingPassword()
						//
						var err_str: String
						if (userSelectedTypeOfPassword == PasswordType.password) {
							err_str = this.context.resources.getString(R.string.enter_fresh_password)
						} else if (userSelectedTypeOfPassword == PasswordType.PIN) {
							err_str = this.context.resources.getString(R.string.enter_fresh_pin)
						} else {
							err_str = this.context.resources.getString(R.string.unrecognized_password_type)
							throw AssertionError(err_str) // considered fatal
						}
						this.erroredWhileSettingNewPassword_fns.invoke(this, err_str)
						return@cb // bail
					}
				}
				//
				// II. hang onto new pw, pw type, and state(s)
				Log.d("Passwords", "Obtained ${userSelectedTypeOfPassword!!} ${obtainedPasswordString!!.length} chars long")
				this.password = obtainedPasswordString!!
				this.passwordType = userSelectedTypeOfPassword!!
				//
				// III. finally, save doc (and unlock on success) so we know a pw has been entered once before
				val err_str = this.saveToDisk()
				if (err_str != null) {
					this.unguard_getNewOrExistingPassword()
					if (wasFirstSetOfPasswordAtRuntime && this.password != null) { // is this correct?
						throw AssertionError("Unexpected wasFirstSetOfPasswordAtRuntime && this.password != null")
					}
					this.password = old_password // they'll have to try again - and revert to old pw rather than null for changePassword (should be null for first pw set)
					this.passwordType = old_passwordType
					//
					this.erroredWhileSettingNewPassword_fns.invoke(this, err_str!!)
					return@cb
				}
				// detecting & emiting first set or handling result of change saves
				if (wasFirstSetOfPasswordAtRuntime) {
					this.unguard_getNewOrExistingPassword()
					// specific emit
					this.setFirstPasswordDuringThisRuntime_fns.invoke(this, "")
					// general purpose emit
					this.obtainedNewPassword_fns.invoke(this, "")
					return@cb // prevent fallthough
				}
				// then, it's a change password
				val changePassword_err_orNull = this._tellRegistrants_doChangePassword() // returns error
				if (changePassword_err_orNull == null) { // actual success - we can return early
					this.unguard_getNewOrExistingPassword()
					// specific emit
					this.registrantsAllChangedPassword_fns.invoke(this, "")
					// general purpose emit
					this.obtainedNewPassword_fns.invoke(this, "")
					//
					return@cb
				}
				// try to revert save files to old password...
				this.password = old_password // first revert, so consumers can read reverted value
				this.passwordType = old_passwordType
				//
				val revert_save_errStr_orNull = this.saveToDisk()
				if (revert_save_errStr_orNull != null) {
					assert(false) // Couldn't saveToDisk to revert failed changePassword... in debug mode, trigger
					// ^-- asserts aren't enabled in testing nor debug mode .. what can this be replaced with?
				} else { // continue trying to revert
					val revert_registrantsChangePw_err_orNull = this._tellRegistrants_doChangePassword() // this may well fail
					if (revert_registrantsChangePw_err_orNull != null) {
						assert(false) // Some registrants couldn't revert failed changePassword; in debug mode, treat this as fatal
						// ^-- asserts aren't enabled in testing nor debug mode .. what can this be replaced with?
					} else {
						// revert successful
					}
				}
				// finally, notify of error while changing password
				this.unguard_getNewOrExistingPassword() // important
				this.erroredWhileSettingNewPassword_fns.invoke(this, changePassword_err_orNull) // the original changePassword_err_orNull
			}
		)
	}
	//
	// Imperatives - Persistence
	fun saveToDisk(): String? // err_str?
	{
		if (this.password == null) {
			val err_str = "Code fault: saveToDisk musn't be called until a password has been set"
			return err_str
		}
		val plaintextString = this.plaintextMessageToSaveForUnlockChallenges
		val encrypted_base64String = PersistableObject.new_encryptedStringFrom(plaintextString, this.password!!)
		this.messageAsEncryptedDataForUnlockChallenge_base64String = encrypted_base64String // it's important that we hang onto this in memory so we can access it if we need to change the password later
		if (this._id == null) {
			this._id = DocumentFileDescription.new_documentId()
		}
		val persistableDocument = mapOf<String, Any>(
			DictKey._id.rawValue to this._id!!,
			DictKey.passwordType.rawValue to this.passwordType.rawValue,
			DictKey.messageAsEncryptedDataForUnlockChallenge_base64String.rawValue to this.messageAsEncryptedDataForUnlockChallenge_base64String!!
		)
		val documentFileString = PersistableObject.new_plaintextJSONStringFromDocumentDict(persistableDocument)
		val err_str = DocumentPersister.Write(
			documentFileWithString = documentFileString,
			id = this._id!!,
			collectionName = this.collectionName
		)
		if (err_str != null) {
			Log.e("Passwords", "Error while persisting ${this}: ${err_str}")
		}
		//
		return err_str
	}
	//
	// Imperatives - Coordinated Events - Registration
	fun addRegistrantForChangePassword(registrant: ChangePasswordRegistrant)
	{
		this._addRegistrantTo(
			registrant = registrant,
			mutable_weakRefsTo_registrants = this.weakRefsTo_changePasswordRegistrants
		)
	}
	fun removeRegistrantForChangePassword(registrant: ChangePasswordRegistrant)
	{
		this._removeRegistrantFrom(
			registrant = registrant,
			mutable_weakRefsTo_registrants = this.weakRefsTo_changePasswordRegistrants
		)
	}
	fun addRegistrantForDeleteEverything(registrant: DeleteEverythingRegistrant)
	{
		this._addRegistrantTo(
			registrant = registrant,
			mutable_weakRefsTo_registrants = this.weakRefsTo_deleteEverythingRegistrants
		)
	}
	fun removeRegistrantForDeleteEverything(registrant: DeleteEverythingRegistrant)
	{
		this._removeRegistrantFrom(
			registrant = registrant,
			mutable_weakRefsTo_registrants = this.weakRefsTo_deleteEverythingRegistrants
		)
	}
	private fun _addRegistrantTo(
		registrant: PasswordControllerEventParticipant,
		mutable_weakRefsTo_registrants: MutableList<WeakRefTo_EventParticipant>
	) {
		mutable_weakRefsTo_registrants.add(
			WeakRefTo_EventParticipant(value = WeakReference(registrant))
		)
	}
	private fun _removeRegistrantFrom(
		registrant: PasswordControllerEventParticipant,
		mutable_weakRefsTo_registrants: MutableList<WeakRefTo_EventParticipant>
	): Boolean {
		var index: Int? = null
		this._iterateRegistrants(
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
		return true
	}
	private fun _iterateRegistrants(
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
	private fun _tellRegistrants_doDeleteEverything(): String? // first encountered err_str
	{
		var err_str: String? = null
		this._iterateRegistrants(
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
	private fun _tellRegistrants_doChangePassword(): String? // err_str
	{
		var err_str: String? = null
		this._iterateRegistrants(
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
		this.onceBooted cb@{
			if (this.hasUserEnteredValidPasswordYet == false) {
				val err_str = "initiate_changePassword called but hasUserEnteredValidPasswordYet == false. This should be disallowed in the UI"
				throw AssertionError(err_str)
			}
			// guard
			if (this.isAlreadyGettingExistingOrNewPWFromUser == true) {
				val err_str = "initiate_changePassword called but isAlreadyGettingExistingOrNewPWFromUser == true. This should be precluded in the UI"
				throw AssertionError(err_str)
				// though only need to wait for it to be obtained
			}
			this.guard_getNewOrExistingPassword()
			//
			// ^-- we're relying on having checked above that user has entered a valid pw already
			val isForChangePassword = true // we'll use this in a couple places
			this._getUserToEnterTheirExistingPassword(
				isForChangePassword = isForChangePassword,
				isForAuthorizingAppActionOnly = false,
				fn = cb2@{ didCancel_orNull, validationErr_orNull, entered_existingPassword ->
					if (validationErr_orNull != null) { // takes precedence over cancel
						this.unguard_getNewOrExistingPassword()
						this.errorWhileChangingPassword_fns.invoke(this, validationErr_orNull)
						return@cb2
					}
					if (didCancel_orNull == true) {
						this.unguard_getNewOrExistingPassword()
						this.canceledWhileChangingPassword_fns.invoke(this, "")
						return@cb2 // just silently exit after unguarding
					}
					val isGoodEnteredPassword = this.withExistingPassword_isCorrect(
						enteredPassword = entered_existingPassword!!
					)
					if (isGoodEnteredPassword == false) {
						this.unguard_getNewOrExistingPassword()
						val err_str = this.new_incorrectPasswordValidationErrorMessageString
						this.errorWhileChangingPassword_fns.invoke(this, err_str)
						return@cb2
					}
					// passwords match checked as necessary, we can proceed
					this.obtainNewPasswordFromUser(
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
		this._deconstructBootedStateAndClearPassword(
			isForADeleteEverything = true,
			optl__hasFiredWill_fn = hasFiredWill_cb@{ cb ->
				// reset state cause we're going all the way back to pre-boot
				this.hasBooted = false // require this pw controller to boot
				this.password = null // this is redundant but is here for clarity
				this._id = null
				this.messageAsEncryptedDataForUnlockChallenge_base64String = null
				//
				// first try to have registrants delete everything
				val registrant__first_err_str = this._tellRegistrants_doDeleteEverything()
				if (registrant__first_err_str != null) {
					cb(registrant__first_err_str)
					return@hasFiredWill_cb
				}
				//
				// now we can go ahead and delete the pw record
				val (err_str, _) = DocumentPersister.RemoveAllDocuments(collectionName = this.collectionName)
				if (err_str != null) {
					cb(err_str)
					return@hasFiredWill_cb
				}
				Log.d("Passwords", "Deleted password record.")
				//
				this.initializeRuntimeAndBoot() // now trigger a boot before we call cb (tho we could do it after - consumers will wait for boot)
				cb(null)
			},
			optl__fn = fn_cb@{ err_str ->
				if (err_str != null) {
					Log.e("Passwords", "Error while deleting everything: ${err_str}")
					this.didErrorWhileDeletingEverything_fns.invoke(this, err_str)
					throw AssertionError("Errored while deleting everything. Restart app. Error: ${err_str}")
					// just fatalError here so app can be relaunched and regain sane state
				}
				this.havingDeletedEverything_didDeconstructBootedStateAndClearPassword_fns.invoke(this, "")
			}
		)
	}
	//
	// Runtime - Imperatives - Boot-state deconstruction/teardown
	fun _deconstructBootedStateAndClearPassword(
		isForADeleteEverything: Boolean,
		optl__hasFiredWill_fn: ((
			cb: (err_str: String?) -> Unit
		) -> Unit)?,
		optl__fn: ((
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
				if (err_str != null) {
					fn(err_str)
					return@cb
				}
				// trigger deconstruction of booted state and require password
				this.password = null // clear pw in memory
				this.hasBooted = false // require this pw controller to boot
				this._id = null
				this.messageAsEncryptedDataForUnlockChallenge_base64String = null
				//
				// we're not going to call WhenBootedAndPasswordObtained_PasswordAndType because consumers will call it for us after they tear down their booted state with the "will" event and try to boot/decrypt again when they get this "did" event
				this.didDeconstructBootedStateAndClearPassword_fns.invoke(this, "")
				//
				this.initializeRuntimeAndBoot() // now trigger a boot before we call cb (tho we could do it after - consumers will wait for boot)
				//
				fn(null)
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
			fn = cb@{
				if (this.hasUserEnteredValidPasswordYet == false) {
					val err_etr = "initiate_verifyUserAuthenticationForAction called but hasUserEnteredValidPasswordYet == false. This should be disallowed in the UI"
					throw AssertionError(err_etr)
//					return@cb
				}
				// guard
				if (this.isAlreadyGettingExistingOrNewPWFromUser == true) {
					val err_str = "initiate_changePassword called but isAlreadyGettingExistingOrNewPWFromUser == true. This should be precluded in the UI"
					throw AssertionError(err_str)
					// only need to wait for it to be obtained
//					return@cb
				}
				this.guard_getNewOrExistingPassword()
				//
				// ^-- we're relying on having checked above that user has entered a valid pw already
				fun _proceedTo_verifyVia_passphrase()
				{
					this._getUserToEnterTheirExistingPassword(
						isForChangePassword = false,
						isForAuthorizingAppActionOnly = true,
						customNavigationBarTitle = customNavigationBarTitle,
						fn = enterExisting_fn@{ didCancel_orNull, validationErr_orNull, entered_existingPassword ->
							if (validationErr_orNull != null) { // takes precedence over cancel
								this.unguard_getNewOrExistingPassword()
								this.errorWhileAuthorizingForAppAction_fns.invoke(this, validationErr_orNull)
								return@enterExisting_fn
							}
							if (didCancel_orNull == true) {
								this.unguard_getNewOrExistingPassword()
								//
								// currently there's no need of a .canceledWhileAuthorizingForAppAction note post here
								canceled_fn?.let { it() } // but must call cb
								//
								return@enterExisting_fn // just silently exit after unguarding
							}
							val isGoodEnteredPassword = this.withExistingPassword_isCorrect(
								enteredPassword = entered_existingPassword!!
							)
							if (isGoodEnteredPassword == false) {
								this.unguard_getNewOrExistingPassword()
								val err_str = this.new_incorrectPasswordValidationErrorMessageString
								this.errorWhileAuthorizingForAppAction_fns.invoke(this, err_str)
								return@enterExisting_fn
							}
							//
							this.unguard_getNewOrExistingPassword() // must be called
							this.successfullyAuthenticatedForAppAction_fns.invoke(this, "") // this must be posted so the PresentationController can dismiss the entry modal
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
}