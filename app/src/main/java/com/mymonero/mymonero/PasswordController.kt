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
import tgio.rncryptor.RNCryptorNative
import java.util.Timer
import kotlin.concurrent.schedule

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

interface PasswordProvider
{ // you can use this type for dependency-injecting a PasswordController implementation; see PersistableObject
	var password: Password?
}


interface PasswordEntryDelegate
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
	//
	// To support equals
	fun identifier(): String
}
fun isEqual(l: PasswordEntryDelegate, r: PasswordEntryDelegate): Boolean
{ // TODO: this may need to be ported somehow
	return l.identifier() == r.identifier()
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
	val changedPassword_fns = EventEmitter<PasswordController, String>()
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
	val willDeconstructBootedStateAndClearPassword_fns = EventEmitter<PasswordController, String>()
	val didDeconstructBootedStateAndClearPassword_fns = EventEmitter<PasswordController, String>()
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
				assert(false) // throw? ".hasUserSavedAPassword: ${err_str!)}"
				return false
			}
			val numberOfIds = ids!!.count()
			if (numberOfIds > 1) {
				assert(false) // throw? "Illegal: Should be only one document"
				return false
			} else if (numberOfIds == 0) {
				return false
			}
			return true
		}
	var messageAsEncryptedDataForUnlockChallenge_base64String: String? = null
	var isAlreadyGettingExistingOrNewPWFromUser: Boolean? = null
	private var passwordEntryDelegate: PasswordEntryDelegate? = null // someone in the app must set this by calling setPasswordEntryDelegate(to:)
	fun setPasswordEntryDelegate(to_delegate: PasswordEntryDelegate)
	{
		if (this.passwordEntryDelegate != null) {
			// TODO: throw here? not meant to be caught... fatalError would be better
			assert(false) // "setPasswordEntryDelegate called but this.passwordEntryDelegate already exists"
			return
		}
		this.passwordEntryDelegate = to_delegate
	}
	fun clearPasswordEntryDelegate(from_existingDelegate: PasswordEntryDelegate)
	{
		if (this.passwordEntryDelegate == null) {
			// TODO: throw here? not meant to be caught... fatalError would be better
			assert(false) // "clearPasswordEntryDelegate called but no passwordEntryDelegate exists"
			return
		}
		if (isEqual(this.passwordEntryDelegate!!, from_existingDelegate) == false) {
			// TODO: throw here? not meant to be caught... fatalError would be better
			assert(false) // "clearPasswordEntryDelegate called but passwordEntryDelegate does not match"
			return
		}
		this.passwordEntryDelegate = null
	}
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
//		NotificationCenter.default.addObserver(self, selector: #selector(UserIdle_userDidBecomeIdle), name: UserIdle.NotificationNames.userDidBecomeIdle.notificationName, object: null)
	}
	fun initializeRuntimeAndBoot()
	{
		assert(this.hasBooted == false) // "\(#function) called while already booted"
		val (err_str, documentContentStrings) = DocumentPersister.AllDocuments(
			collectionName = this.collectionName
		)
		if (err_str != null) {
			Log.e("Passwords", "Fatal error while loading ${this.collectionName}: ${err_str!!}")
			assert(false) // TODO: fatalError?
			return
		}
		val documentContentStrings_count = documentContentStrings!!.count()
		if (documentContentStrings_count  > 1) {
			Log.e("Passwords", "Unexpected state while loading ${this.collectionName}: more than one saved doc.")
			assert(false)
			return
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
					Log.e("Passwords", "${err_str}")
					// TODO: fatalError
					return
				}
			}
			//
			this.hasBooted = true
			this._callAndFlushAllBlocksWaitingForBootToExecute()
//			Log.d("Passwords", "Booted \(self) and called all waiting blocks. Waiting for unlock.")
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
			userCanceled_fn?.let { it() }
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
				Log.e("Passwords", "PasswordController/OnceBootedAndPasswordObtained hasCalledBack already true")
				assert(false) // TODO: fatalError?
				return false // ^- shouldn't happen but just in case…
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
		this.isAlreadyGettingExistingOrNewPWFromUser = true
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
				Log.e("Passwords", "${err_str}")
				this.unguard_getNewOrExistingPassword()
				assert(false) // TODO: fatalError? err_str
				return
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
				this._didObtainPassword(obtainedPasswordString!!)
				// all done
				this.unguard_getNewOrExistingPassword()
				//
				this.obtainedCorrectExistingPassword_fns(this, "")
			}
		}
	}
	//
	// Runtime - Imperatives - Private - Requesting password from user
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
		assert(isForChangePassword == false || isForAuthorizingAppActionOnly == false) // both shouldn't be true
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
					assert(false)
					return@cb // exit to prevent fallthrough
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
							assert(false)
						}
						this.erroredWhileSettingNewPassword_fns.invoke(this, err_str)
						return@cb // bail
					}
				}
				//
				// II. hang onto new pw, pw type, and state(s)
				Log.d("Passwords", "Obtained ${userSelectedTypeOfPassword!!} ${obtainedPasswordString!!.length} chars long")
				this._didObtainPassword(obtainedPasswordString!!)
				this.passwordType = userSelectedTypeOfPassword!!
				//
				// III. finally, save doc (and unlock on success) so we know a pw has been entered once before
				val err_str = this.saveToDisk()
				if (err_str != null) {
					this.unguard_getNewOrExistingPassword()
					this.password = null // they'll have to try again
					//
					this.erroredWhileSettingNewPassword_fns.invoke(this, err_str!!)
					return@cb
				}
				this.unguard_getNewOrExistingPassword()
				// detecting & emiting first set or change
				if (wasFirstSetOfPasswordAtRuntime) {
					this.setFirstPasswordDuringThisRuntime_fns.invoke(this, "")
				} else {
					this.changedPassword_fns.invoke(this, "")

				}
				// general purpose emit
				this.obtainedNewPassword_fns.invoke(this, "")
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
	// Delegation - Password
	fun _didObtainPassword(password: Password)
	{
		this.password = password
	}
}