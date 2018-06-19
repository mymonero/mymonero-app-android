//
//  ExampleInstrumentedTest.kt
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

package com.mymonero.mymonero

import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

import android.util.Log
import com.mymonero.Application.MainApplication
import com.mymonero.Application.UserIdleController
import com.mymonero.Currencies.CcyConversionRatesController
import com.mymonero.Currencies.Currency
import com.mymonero.Currencies.CurrencySymbol
import com.mymonero.MyMoneroCore.DoubleFromMoneroAmount
import com.mymonero.MyMoneroCore.FormattedString
import com.mymonero.MyMoneroCore.MoneroAmount
import com.mymonero.MyMoneroCore.MoneroAmountFrom
import com.mymonero.Passwords.*
import com.mymonero.Persistence.*
import com.mymonero.Settings.IdleTimeoutAfterS_SettingsProvider
import com.mymonero.Settings.SettingsController
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Assert
import java.math.RoundingMode
import java.util.*
import java.util.concurrent.TimeUnit

import kotlin.concurrent.schedule
import kotlin.concurrent.scheduleAtFixedRate

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */

val mockedSavedObjects__CollectionName = "MockedSavedObjects" // this is outside the tests class for access within a nested class - unsure why that's not otherwise possible
//
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
	//
	// Environment
    @Test fun useAppContext()
	{
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getTargetContext()
        assertEquals("com.mymonero.mymonero", appContext.packageName)
    }
	//
	// DocumentPersister
	val mockedPlainStringDocs__LogTag = "mockedPlainStringDocs"
	val mockedPlainStringDocs__CollectionName = "TestDocuments"
	@Test fun mockedPlainStringDoc__insert()
	{
		val id = DocumentFileDescription.new_documentId()
		val err_str = DocumentPersister.Write(
			documentFileWithString = "\"a\":2,\"id\":\"${id}\"",
			id = id,
			collectionName = mockedPlainStringDocs__CollectionName
		)
		assertEquals(null, err_str)
		Log.d(mockedPlainStringDocs__LogTag, "Inserted " + id)
	}
	@Test fun mockedPlainStringDoc__allIds()
	{
		val (err_str, ids) = DocumentPersister.IdsOfAllDocuments(mockedPlainStringDocs__CollectionName)
		assertEquals(null, err_str)
		Assert.assertTrue(ids != null)
		Assert.assertTrue(ids!!.count() > 0) // from __insert
		Log.d(mockedPlainStringDocs__LogTag, "ids: " + ids)
	}
	@Test fun mockedPlainStringDoc__allDocuments()
	{
		val (err_str, strings) = DocumentPersister.AllDocuments(mockedPlainStringDocs__CollectionName)
		assertEquals(null, err_str)
		Assert.assertTrue(strings != null)
		Assert.assertTrue(strings!!.count() > 0) // from __insert
		Log.d(mockedPlainStringDocs__LogTag, "strings: " + strings)
	}
	@Test fun mockedPlainStringDoc__removeAllDocuments()
	{
		val (fetch__err_str, ids) = DocumentPersister.IdsOfAllDocuments(mockedPlainStringDocs__CollectionName)
		assertEquals(null, fetch__err_str)
		Assert.assertTrue(ids != null)
		Assert.assertTrue(ids!!.count() > 0) // from __insert
		//
		val (remove__err_str, numRemoved) = DocumentPersister.RemoveAllDocuments(mockedPlainStringDocs__CollectionName)
		assertEquals(null, remove__err_str)
		Assert.assertTrue(numRemoved!! == ids!!.count()) // from __insert
	}
	//
	// PersistableObject
	val mockedSavedObjects__LogTag = "mockedSavedObjects"
	val mockedSavedObjects__addtlVal_ = "Some extra test data"
	object MockedPasswordProvider: PasswordProvider
	{ // this class is no longer necessary
		override val password: Password?
			get() = "_ password"
	}
	class MockedSavedObject: PersistableObject
	{
		lateinit var addtlVal: String // set this after init to avoid test fail
		constructor(passwordProvider: PasswordProvider): super(passwordProvider)
		constructor(
			passwordProvider: PasswordProvider,
			plaintextData: DocumentJSON
		): super(passwordProvider, plaintextData)
		{
			Assert.assertTrue(this.passwordProvider != null)
			Assert.assertTrue(this._id != null)
			Assert.assertTrue(this.insertedAt_sSinceEpoch != null)
			//
			this.addtlVal = plaintextData["addtlVal"] as String
			Assert.assertTrue(this.addtlVal != null)
		}
		override fun new_dictRepresentation(): MutableDocumentJSON
		{
			var dict = super.new_dictRepresentation()
			dict["addtlVal"] = this.addtlVal
			//
			return dict
		}
		override fun collectionName(): String {
			return mockedSavedObjects__CollectionName
		}
	}
	@Test fun mockedSavedObjects__insertNew()
	{
		val obj = MockedSavedObject(MockedPasswordProvider) // new
		obj.addtlVal = mockedSavedObjects__addtlVal_
		//
		val errStr = obj.saveToDisk()
		assertEquals(null, errStr)
		Assert.assertTrue(obj._id != null)
	}
	@Test fun mockedSavedObjects__loadExisting()
	{
		val (err_str, ids) = DocumentPersister.IdsOfAllDocuments(mockedSavedObjects__CollectionName)
		assertEquals(null, err_str)
		Assert.assertTrue(ids != null)
		Assert.assertTrue(ids!!.count() > 0) // from __insertNew
		//
		val (load__errStr, documentContentStrings) = DocumentPersister.DocumentsData(
			ids = ids,
			collectionName = mockedSavedObjects__CollectionName
		)
		assertEquals(null, load__errStr)
		Assert.assertTrue(documentContentStrings != null)
		Assert.assertTrue(documentContentStrings!!.count() > 0)
		for (encrypted_documentContentString in documentContentStrings) {
			val plaintext_documentContentString = PersistableObject.new_plaintextStringFrom(
				encrypted_documentContentString,
				password = MockedPasswordProvider.password!!
			)
			val plaintext_documentJSON = PersistableObject.new_plaintextDocumentDictFromJSONString(plaintext_documentContentString)
			val listedObjectInstance = MockedSavedObject(
				MockedPasswordProvider,
				plaintext_documentJSON
			)
			Assert.assertTrue(listedObjectInstance._id != null)
			Assert.assertTrue(listedObjectInstance.insertedAt_sSinceEpoch != null)
			assertEquals(mockedSavedObjects__addtlVal_, listedObjectInstance.addtlVal)
		}
	}
	@Test fun mockedSavedObjects__deleteExisting()
	{
		val (err_str, ids) = DocumentPersister.IdsOfAllDocuments(mockedSavedObjects__CollectionName)
		assertEquals(null, err_str)
		Assert.assertTrue(ids != null)
		Assert.assertTrue(ids!!.count() > 0) // from __insertNew
		//
		val (load__errStr, documentContentStrings) = DocumentPersister.DocumentsData(
			ids = ids,
			collectionName = mockedSavedObjects__CollectionName
		)
		assertEquals(null, load__errStr)
		Assert.assertTrue(documentContentStrings != null)
		Assert.assertTrue(documentContentStrings!!.count() > 0)
		for (encrypted_documentContentString in documentContentStrings) {
			val plaintext_documentContentString = PersistableObject.new_plaintextStringFrom(
				encrypted_documentContentString,
				password = MockedPasswordProvider.password!!
			)
			val plaintext_documentJSON = PersistableObject.new_plaintextDocumentDictFromJSONString(plaintext_documentContentString)
			val listedObjectInstance = MockedSavedObject(
				MockedPasswordProvider,
				plaintext_documentJSON
			)
			Assert.assertTrue(listedObjectInstance._id != null)
			Assert.assertTrue(listedObjectInstance.insertedAt_sSinceEpoch != null)
			//
			val delete__errStr = listedObjectInstance.delete()
			Assert.assertTrue(delete__errStr == null)
		}
	}
	//
	// PasswordController
	val mockedPasswords__LogTag = "mockedPasswords"
	//
	object MockedPasswords_CorrectEntryDelegate: PasswordEntryDelegate
	{
		val uuid = UUID.randomUUID().toString()
		val createNewUserInput = "a mock password"
		val getExistingUserInput = "a mock password"
		val expectedPasswordType = PasswordType.password
		//
		override fun identifier(): String {
			return this.uuid
		}

		public override fun getUserToEnterNewPasswordAndType(
			isForChangePassword: Boolean,
			enterNewPasswordAndType_cb: (
				didCancel_orNull: Boolean?,
				obtainedPasswordString: Password?,
				passwordType: PasswordType?
			) -> Unit
		) {
			assertTrue(isForChangePassword == false)
			val passwordController = MainApplication.serviceLocator.passwordController
			assertTrue(passwordController.password == null)
			//
			val userInput = this.createNewUserInput
			val passwordType = PasswordType.new_detectedFromPassword(userInput)
			assertTrue(passwordType == expectedPasswordType)
			//
			enterNewPasswordAndType_cb(
				false, // didn't cancel
				userInput,
				passwordType
			)
		}
		override fun getUserToEnterExistingPassword(
			isForChangePassword: Boolean,
			isForAuthorizingAppActionOnly: Boolean,
			customNavigationBarTitle: String?,
			enterExistingPassword_cb: (
				didCancel_orNull: Boolean?,
				obtainedPasswordString: Password?
			) -> Unit
		) {
			assertTrue(isForChangePassword == false)
			val passwordController = MainApplication.serviceLocator.passwordController
			assertTrue(passwordController.password == null && passwordController.passwordType != null)
			//
			enterExistingPassword_cb(
				false, // didn't cancel
				this.getExistingUserInput
			)
		}
	}
	@Test fun mockedPasswords__correctEntry_getPassword_createIfNecessary()
	{
		val passwordController = MainApplication.serviceLocator.passwordController
		passwordController.setPasswordEntryDelegate(MockedPasswords_CorrectEntryDelegate) // for this test
		//
		val _1 = passwordController.erroredWhileSettingNewPassword_fns.startObserving(
			{ emitter, err_str ->
				assertTrue(err_str, false) // should never see this
			}
		)
		val _2 = passwordController.erroredWhileGettingExistingPassword_fns.startObserving(
			{ emitter, err_str ->
				assertTrue(err_str, false) // should never see this
			}
		)
		val _3 = passwordController.canceledWhileEnteringNewPassword_fns.startObserving(
			{ emitter, err_str ->
				assertTrue("Unexpected cancel", false) // should never see this
			}
		)
		val _4 = passwordController.canceledWhileEnteringExistingPassword_fns.startObserving(
			{ emitter, err_str ->
				assertTrue("Unexpected cancel", false) // should never see this
			}
		)
		//
		passwordController.OnceBootedAndPasswordObtained(
			fn = { password, passwordType ->
				Log.d(mockedPasswords__LogTag, "Obtained ${passwordType} from user ${password}")
				//
				assertTrue(passwordController.password == password)
				assertTrue(passwordController.passwordType == passwordType)
			},
			userCanceled_fn = {
				Log.d(mockedPasswords__LogTag, "User canceled pw input")
				assertTrue(false) // not expecting this
			}
		)
		//
		// This should not be necessary but the above code may become asynchronous
		Thread.sleep(50) // TODO: see if we can remove this
	}
	//
	object MockedPasswords_IncorrectEntryDelegate: PasswordEntryDelegate
	{
		val uuid = UUID.randomUUID().toString()
		val userInput = "an INCORRECT mock password"
		//
		override fun identifier(): String {
			return this.uuid
		}

		public override fun getUserToEnterNewPasswordAndType(
			isForChangePassword: Boolean,
			enterNewPasswordAndType_cb: (
				didCancel_orNull: Boolean?,
				obtainedPasswordString: Password?,
				passwordType: PasswordType?
			) -> Unit
		) {
			assertTrue(false) // Not expecting this - if the tester ran 'correct entry' first
		}
		override fun getUserToEnterExistingPassword(
			isForChangePassword: Boolean,
			isForAuthorizingAppActionOnly: Boolean,
			customNavigationBarTitle: String?,
			enterExistingPassword_cb: (
				didCancel_orNull: Boolean?,
				obtainedPasswordString: Password?
			) -> Unit
		) {
			assertFalse(isForChangePassword)
			val passwordController = MainApplication.serviceLocator.passwordController
			assertTrue(passwordController.password == null) // because we haven't entered it yet
			assertTrue(passwordController.passwordType == MockedPasswords_CorrectEntryDelegate.expectedPasswordType)
			//
			enterExistingPassword_cb(
				false, // didn't cancel
				this.userInput // feed incorrect password - and expect fail
			)
		}
	}
	@Test fun mockedPasswords__incorrectEntry_getPassword()
	{
		val passwordController = MainApplication.serviceLocator.passwordController
		assertTrue("Expected a password to have already been saved for this test", passwordController.hasUserSavedAPassword)
		//
		passwordController.setPasswordEntryDelegate(MockedPasswords_IncorrectEntryDelegate) // for this test
		//
		var didError = false
		val _1 = passwordController.erroredWhileSettingNewPassword_fns.startObserving(
			{ emitter, err_str ->
				assertTrue(err_str, false) // should never see this
			}
		)
		val _2 = passwordController.erroredWhileGettingExistingPassword_fns.startObserving(
			{ emitter, err_str ->
				didError = true
				// we expect this case
				assertEquals(err_str, "Incorrect password") // this may be too fragile but making it dynamic might make the test less useful
			}
		)
		val _3 = passwordController.canceledWhileEnteringNewPassword_fns.startObserving(
			{ emitter, _ ->
				assertTrue("Unexpected cancel", false) // should never see this
			}
		)
		val _4 = passwordController.canceledWhileEnteringExistingPassword_fns.startObserving(
			{ emitter, _ ->
				assertTrue("Unexpected cancel", false) // should never see this
			}
		)
		//
		passwordController.OnceBootedAndPasswordObtained(
			fn = { password, passwordType ->
				assertTrue(false) // should never be allowed to get here with incorrect input
			},
			userCanceled_fn = {
				assertTrue(false) // not expecting that
			}
		)
		//
		// This may not be necessary but the above code may become asynchronous
		Thread.sleep(50) // TODO: see if we can remove this
		//
		assertTrue("Wrong password entered was not picked up", didError)
	}
	//

	object MockedPasswords_SpammingIncorrectEntryDelegate: PasswordEntryDelegate
	{
		val uuid = UUID.randomUUID().toString()
		val userInput = "an INCORRECT mock password"
		//
		val spamTryInterval_ms: Long = 1000
		//
		override fun identifier(): String {
			return this.uuid
		}

		public override fun getUserToEnterNewPasswordAndType(
			isForChangePassword: Boolean,
			enterNewPasswordAndType_cb: (
				didCancel_orNull: Boolean?,
				obtainedPasswordString: Password?,
				passwordType: PasswordType?
			) -> Unit
		) {
			assertTrue(false) // Not expecting this - if the tester ran 'correct entry' first
		}

		override fun getUserToEnterExistingPassword(
			isForChangePassword: Boolean,
			isForAuthorizingAppActionOnly: Boolean,
			customNavigationBarTitle: String?,
			enterExistingPassword_cb: (
				didCancel_orNull: Boolean?,
				obtainedPasswordString: Password?
			) -> Unit
		) {
			assertFalse(isForChangePassword)
			val passwordController = MainApplication.serviceLocator.passwordController
			assertTrue(passwordController.password == null) // because we haven't entered it yet
			assertTrue(passwordController.passwordType == MockedPasswords_CorrectEntryDelegate.expectedPasswordType)
			//
			val self = this
			val timer = Timer("test", false)
			var i = 0
			timer.scheduleAtFixedRate(0, spamTryInterval_ms, {
				Log.d("Test", "Doing ${i}th bad pw entry")
				enterExistingPassword_cb(
					false, // didn't cancel
					self.userInput // feed incorrect password - and expect fail
				)
				i += 1
				if (i == PasswordController.maxLegal_numberOfTriesDuringThisTimePeriod + 1) {
					Log.d("Test", "Cleaning up timer")
					timer.cancel()
					timer.purge()
				}
			})
		}
	}
	@Test fun mockedPasswords__spammingIncorrectEntry_getPassword()
	{
		val passwordController = MainApplication.serviceLocator.passwordController
		assertTrue("Expected a password to have already been saved for this test", passwordController.hasUserSavedAPassword)
		//
		passwordController.setPasswordEntryDelegate(MockedPasswords_SpammingIncorrectEntryDelegate) // for this test
		//
		var didSeeLockOutAfterMaxTries = false
		val _1 = passwordController.erroredWhileSettingNewPassword_fns.startObserving(
			{ emitter, err_str ->
				assertTrue(err_str, false) // should never see this
			}
		)
		var numTriesFailed = 0
		val _2 = passwordController.erroredWhileGettingExistingPassword_fns.startObserving(
			{ emitter, err_str ->
				numTriesFailed += 1
//				Log.d("Test", "numTriesFailed ${numTriesFailed} err_str ${err_str}")
				if (numTriesFailed >= PasswordController.maxLegal_numberOfTriesDuringThisTimePeriod + 1) {
					didSeeLockOutAfterMaxTries = true
					assertEquals(err_str, "As a security precaution, please wait a few moments before trying again.") // this string may be too fragile
				} else {
					assertEquals(err_str, "Incorrect password")
				}
			}
		)
		val _3 = passwordController.canceledWhileEnteringNewPassword_fns.startObserving(
			{ emitter, _ ->
				assertTrue("Unexpected cancel", false) // should never see this
			}
		)
		val _4 = passwordController.canceledWhileEnteringExistingPassword_fns.startObserving(
			{ emitter, _ ->
				assertTrue("Unexpected cancel", false) // should never see this
			}
		)
		//
		passwordController.OnceBootedAndPasswordObtained(
			fn = { password, passwordType ->
				assertTrue(false) // should never be allowed to get here with incorrect input
			},
			userCanceled_fn = {
				assertTrue(false) // not expecting that
			}
		)
		//
		// This may not be necessary but the above code may become asynchronous
		Thread.sleep(
			(PasswordController.maxLegal_numberOfTriesDuringThisTimePeriod+1+2)
				* MockedPasswords_SpammingIncorrectEntryDelegate.spamTryInterval_ms
		)
		//
		assertTrue("Did not see PasswordController lock out password entry attempts due to spam", didSeeLockOutAfterMaxTries)
	}
	//
	object MockedPasswords_VerifyUserAuth_Correct_EntryDelegate: PasswordEntryDelegate
	{
		val uuid = UUID.randomUUID().toString()
		override fun identifier(): String {
			return this.uuid
		}
		//
		public override fun getUserToEnterNewPasswordAndType(
			isForChangePassword: Boolean,
			enterNewPasswordAndType_cb: (
				didCancel_orNull: Boolean?,
				obtainedPasswordString: Password?,
				passwordType: PasswordType?
			) -> Unit
		) {
			assertTrue("Unexpected getUserToEnterNewPassword on verify user auth", false) // Not expecting this - if the tester ran 'correct entry' first
		}
		override fun getUserToEnterExistingPassword(
			isForChangePassword: Boolean,
			isForAuthorizingAppActionOnly: Boolean,
			customNavigationBarTitle: String?,
			enterExistingPassword_cb: (
				didCancel_orNull: Boolean?,
				obtainedPasswordString: Password?
			) -> Unit
		) {
			assertFalse(isForChangePassword)
			//
			enterExistingPassword_cb(
				false, // didn't cancel
				MockedPasswords_CorrectEntryDelegate.createNewUserInput
			)
		}
	}
	@Test fun mockedPasswords__verifyUserAuth_correctEntry()
	{
		val passwordController = MainApplication.serviceLocator.passwordController
		assertTrue("Expected a password to have already been saved for this test", passwordController.hasUserSavedAPassword)
		//
		passwordController.setPasswordEntryDelegate(MockedPasswords_VerifyUserAuth_Correct_EntryDelegate) // for this test
		//
		var didError = false
		val _1 = passwordController.erroredWhileSettingNewPassword_fns.startObserving(
			{ emitter, err_str ->
				assertTrue(err_str, false) // should never see this
			}
		)
		val _2 = passwordController.erroredWhileGettingExistingPassword_fns.startObserving(
			{ emitter, err_str ->
				assertTrue(err_str, false) // should never see this
			}
		)
		val _3 = passwordController.canceledWhileEnteringNewPassword_fns.startObserving(
			{ emitter, _ ->
				assertTrue("Unexpected cancel", false) // should never see this
			}
		)
		val _4 = passwordController.canceledWhileEnteringExistingPassword_fns.startObserving(
			{ emitter, _ ->
				assertTrue("Unexpected cancel", false) // should never see this
			}
		)
		//
		val _Z = passwordController.errorWhileAuthorizingForAppAction_fns.startObserving(
			{ emitter, err_str ->
				assertTrue(err_str, false) // not expecting this here
			}
		)
		val _Y = passwordController.successfullyAuthenticatedForAppAction_fns.startObserving(
			{ emitter, _ ->
				// expecting this
			}
		)
		//
		var successfulAuthSeen = false
		passwordController.OnceBootedAndPasswordObtained(
			fn = { password, passwordType ->
				assertEquals("Unexpectedly different password after successful user auth", MockedPasswords_CorrectEntryDelegate.createNewUserInput, password)
				passwordController.initiate_verifyUserAuthenticationForAction(
					customNavigationBarTitle = "Authenticate",
					canceled_fn = {
						assertTrue("Unexpected cancel during auth", false)
					},
					entryAttempt_succeeded_fn = {
						successfulAuthSeen = true
					}
				)
			},
			userCanceled_fn = {
				assertTrue(false) // not expecting that
			}
		)
		//
		// This may not be necessary but the above code may become asynchronous
		Thread.sleep(50) // TODO: see if we can remove this
		//
		assertTrue("Successful auth not seen", successfulAuthSeen)
	}
	//
	object MockedPasswords_VerifyUserAuth_Incorrect_EntryDelegate: PasswordEntryDelegate
	{
		var didEnter_incorrectEntryMethod = false
		//
		val uuid = UUID.randomUUID().toString()
		override fun identifier(): String {
			return this.uuid
		}
		//
		public override fun getUserToEnterNewPasswordAndType(
			isForChangePassword: Boolean,
			enterNewPasswordAndType_cb: (
				didCancel_orNull: Boolean?,
				obtainedPasswordString: Password?,
				passwordType: PasswordType?
			) -> Unit
		) {
			assertTrue("Unexpected getUserToEnterNewPassword on verify user auth", false) // Not expecting this - if the tester ran 'correct entry' first
		}
		override fun getUserToEnterExistingPassword(
			isForChangePassword: Boolean,
			isForAuthorizingAppActionOnly: Boolean,
			customNavigationBarTitle: String?,
			enterExistingPassword_cb: (
				didCancel_orNull: Boolean?,
				obtainedPasswordString: Password?
			) -> Unit
		) {
			assertFalse(isForChangePassword)
			assertEquals("Authenticate", customNavigationBarTitle) // TODO: share string decl
			//
			didEnter_incorrectEntryMethod = true
			//
			enterExistingPassword_cb(
				false, // didn't cancel
				"some incorrect entry"
			)
		}
	}
	@Test fun mockedPasswords__verifyUserAuth_incorrectEntry()
	{
		val passwordController = MainApplication.serviceLocator.passwordController
		assertTrue("Expected a password to have already been saved for this test", passwordController.hasUserSavedAPassword)
		//
		passwordController.setPasswordEntryDelegate(MockedPasswords_VerifyUserAuth_Correct_EntryDelegate) // for initial unlock
		//
		var didError = false
		val _1 = passwordController.erroredWhileSettingNewPassword_fns.startObserving(
			{ emitter, err_str ->
				assertTrue(err_str, false) // should never see this
			}
		)
		val _2 = passwordController.erroredWhileGettingExistingPassword_fns.startObserving(
			{ emitter, err_str ->
				assertTrue("Specifically not expecting to see this erroredWhileGettingExistingPassword_fns invocation on an incorrect user pw entry for authorization purposes!", false)
			}
		)
		var didSeeErrorWhileAuthorizingForAppAction = false
		val _Z = passwordController.errorWhileAuthorizingForAppAction_fns.startObserving(
			{ emitter, err_str ->
				didSeeErrorWhileAuthorizingForAppAction = true
			}
		)
		val _Y = passwordController.successfullyAuthenticatedForAppAction_fns.startObserving(
			{ emitter, _ ->
				assertTrue("Unexpected success during incorrect auth", false) // not expecting this here
			}
		)
		val _3 = passwordController.canceledWhileEnteringNewPassword_fns.startObserving(
			{ emitter, _ ->
				assertTrue("Unexpected cancel", false) // should never see this
			}
		)
		val _4 = passwordController.canceledWhileEnteringExistingPassword_fns.startObserving(
			{ emitter, _ ->
				assertTrue("Unexpected cancel", false) // should never see this
			}
		)
		//
		var successfulAuthSeen = false
		passwordController.OnceBootedAndPasswordObtained(
			fn = { password, passwordType ->
				assertEquals("Unexpectedly different password after successful user auth", MockedPasswords_CorrectEntryDelegate.createNewUserInput, password)
				//
				// now switch over to this for incorrect entry:
				passwordController.clearPasswordEntryDelegate(MockedPasswords_VerifyUserAuth_Correct_EntryDelegate) // must first unset this or we will cause a runtime assertion exception
				passwordController.setPasswordEntryDelegate(MockedPasswords_VerifyUserAuth_Incorrect_EntryDelegate)
				//
				passwordController.initiate_verifyUserAuthenticationForAction(
					customNavigationBarTitle = "Authenticate",
					canceled_fn = {
						assertTrue("Unexpected cancel during auth", false)
					},
					entryAttempt_succeeded_fn = {
						successfulAuthSeen = true
						// could just assert false here
					}
				)
			},
			userCanceled_fn = {
				assertTrue(false) // not expecting that
			}
		)
		//
		// This may not be necessary but the above code may become asynchronous
		Thread.sleep(50) // TODO: see if we can remove this
		//
		assertTrue("Unexpectedly did not enter incorrect auth pw entry method", MockedPasswords_VerifyUserAuth_Incorrect_EntryDelegate.didEnter_incorrectEntryMethod)
		assertFalse("Successful auth unexpectedly seen", successfulAuthSeen)
		assertTrue("Unsuccessful auth not seen", didSeeErrorWhileAuthorizingForAppAction)
	}
	//
	object MockedPasswords_ChangePasswordEntryDelegate: PasswordEntryDelegate
	{
		val passwordController = MainApplication.serviceLocator.passwordController
		val uuid = UUID.randomUUID().toString()
		val changePasswordTo_userInput = "a changed mock password"
		//
		override fun identifier(): String {
			return this.uuid
		}
		//
		override fun getUserToEnterExistingPassword(
			isForChangePassword: Boolean,
			isForAuthorizingAppActionOnly: Boolean,
			customNavigationBarTitle: String?,
			enterExistingPassword_cb: (
				didCancel_orNull: Boolean?,
				obtainedPasswordString: Password?
			) -> Unit
		) {
			if (isForChangePassword) {
				assertTrue("Expected pw to match initial correct entry on enter existing for change pw", passwordController.password == MockedPasswords_CorrectEntryDelegate.createNewUserInput)
				assertTrue("Expected pw type to match initial correct entry on enter existing for change pw", passwordController.passwordType == MockedPasswords_CorrectEntryDelegate.expectedPasswordType)
				//
				enterExistingPassword_cb(
					false, // didn't cancel
					MockedPasswords_CorrectEntryDelegate.createNewUserInput // being asked to verify original password
				)
			} else {
				assertTrue("Expected pw to be nil on entry that was not for change pw", passwordController.password == null)
				assertTrue("Expected pw type not to be nil on initial boot not for changing pw because one should have been saved", passwordController.passwordType != null)
				//
				enterExistingPassword_cb(
					false,
					MockedPasswords_CorrectEntryDelegate.createNewUserInput // enter original password
				)
			}
		}
		public override fun getUserToEnterNewPasswordAndType(
			isForChangePassword: Boolean,
			enterNewPasswordAndType_cb: (
				didCancel_orNull: Boolean?,
				obtainedPasswordString: Password?,
				passwordType: PasswordType?
			) -> Unit
		) {
			assertTrue("Only expecting to be asked for new pw for change pw since one should be saved already", isForChangePassword)
			assertTrue("Expected pw not to be nil on entering new password", passwordController.password == MockedPasswords_CorrectEntryDelegate.createNewUserInput)

			val userInput = this.changePasswordTo_userInput
			val passwordType = PasswordType.new_detectedFromPassword(userInput)
			assertTrue(passwordType == MockedPasswords_CorrectEntryDelegate.expectedPasswordType)

			enterNewPasswordAndType_cb(
				false,
				userInput,
				passwordType
			)
		}
	}
	object MockedPasswords_ChangePasswordRegistrant_NoError: ChangePasswordRegistrant
	{
		var didEnter_registrant_changePasswordMethod = false
		//
		val uuid = UUID.randomUUID().toString()
		override fun identifier(): String {
			return uuid
		}
		override fun passwordController_ChangePassword(): String? {
			didEnter_registrant_changePasswordMethod = true // assertTrue this later
			return null
		}
	}
	@Test fun mockedPasswords__changePassword()
	{
		val passwordController = MainApplication.serviceLocator.passwordController
		assertTrue("Expected a password to have already been saved for this test", passwordController.hasUserSavedAPassword)
		//
		passwordController.setPasswordEntryDelegate(MockedPasswords_ChangePasswordEntryDelegate) // for this test
		//
		passwordController.addRegistrantForChangePassword(MockedPasswords_ChangePasswordRegistrant_NoError)
		//
		val _1 = passwordController.erroredWhileSettingNewPassword_fns.startObserving(
			{ emitter, err_str ->
				assertTrue(err_str, false) // should never see this
			}
		)
		val _2 = passwordController.erroredWhileGettingExistingPassword_fns.startObserving(
			{ emitter, err_str ->
				assertTrue(err_str, false) // should never see this - unless we're running this test after having already changed the password
			}
		)
		val _3 = passwordController.canceledWhileEnteringNewPassword_fns.startObserving(
			{ emitter, _ ->
				assertTrue("Unexpected cancel", false) // should never see this
			}
		)
		val _4 = passwordController.canceledWhileEnteringExistingPassword_fns.startObserving(
			{ emitter, _ ->
				assertTrue("Unexpected cancel", false) // should never see this
			}
		)
		val _5 = passwordController.errorWhileChangingPassword_fns.startObserving(
			{ emitter, err_str ->
				assertTrue(err_str, false) // should never see this
			}
		)
		val _6 = passwordController.canceledWhileChangingPassword_fns.startObserving(
			{ emitter, _ ->
				assertTrue("Unexpected cancel", false) // should never see this
			}
		)
		//
		var changePWFinished = false
		passwordController.OnceBootedAndPasswordObtained(
			fn = { password, passwordType ->
				assertEquals("On booting pw controller for change pw test, password was not MockedPasswords_CorrectEntryDelegate.createNewUserInput", MockedPasswords_CorrectEntryDelegate.createNewUserInput, password)
				// now that booted, we can change pw
				passwordController.initiate_changePassword()
				// and wait for errors, if any...
				assertEquals("Changed password not as expected", MockedPasswords_ChangePasswordEntryDelegate.changePasswordTo_userInput, passwordController.password)
				Thread.sleep(5) // almost certain this is not necessary for waiting for any assert fails in the emitter listeners above
				changePWFinished = true // probably not necessary here
			},
			userCanceled_fn = {
				assertTrue(false) // not expecting that
			}
		)
		//
		// This may not be necessary but the above code may become asynchronous
		Thread.sleep(50) // TODO: see if we can remove this
		//
// not useful:	assertTrue("Change password didn't finish - this shouldn't be reached before some other assert fails.", changePWFinished)
		assertTrue("Unexpectedly never entered MockedPasswords_ChangePasswordRegistrant_NoError changePW function", MockedPasswords_ChangePasswordRegistrant_NoError.didEnter_registrant_changePasswordMethod)
	}
	//
	object MockedPasswords_DeleteEverythingRegistrant_DidError: DeleteEverythingRegistrant
	{
		val errStr = "Some error while deleting everything"

		val uuid = UUID.randomUUID().toString()
		override fun identifier(): String {
			return uuid
		}
		override fun passwordController_DeleteEverything(): String? {
			return errStr // simulate error
		}
	}
	@Test fun mockedPasswords__deleteEverything_didError()
	{
		val passwordController = MainApplication.serviceLocator.passwordController
		assertTrue("Expected a password to have already been saved for this test", passwordController.hasUserSavedAPassword)
		//
		passwordController.addRegistrantForDeleteEverything(MockedPasswords_DeleteEverythingRegistrant_DidError)
		// we must add this or the test will fail, b/c the delete everything will succeed
		//
		val _7 = passwordController.havingDeletedEverything_didDeconstructBootedStateAndClearPassword_fns.startObserving(
			{ emitter, _ ->
				assertTrue("Unexpected success of deleteEverything in didError test", false)
			}
		)
		var didSeeErrorDuringDeleteEverything = false
		val _8 = passwordController.didErrorWhileDeletingEverything_fns.startObserving(
			{ emitter, err_str ->
				// we expect this to fail here
				assertEquals("err_str returned differs from the expected str", MockedPasswords_DeleteEverythingRegistrant_DidError.errStr, err_str)
				didSeeErrorDuringDeleteEverything = true
			}
		)
		//
		// now that booted, we can change pw
		passwordController.initiate_deleteEverything()
		// and wait for errors, if any...
		Thread.sleep(5) // almost certain this is not necessary for waiting for any assert fails in the emitter listeners above
		assertTrue("Delete everything unexpectedly finished without seeing error", didSeeErrorDuringDeleteEverything)
		//
		// This may not be necessary but the above code may become asynchronous
		Thread.sleep(50) // TODO: see if we can remove this
		//
		assertTrue("Unexpectedly didn't see error during intentional error deleteEverything", didSeeErrorDuringDeleteEverything)
	}
	object MockedPasswords_DeleteEverythingRegistrant_NoError: DeleteEverythingRegistrant
	{
		var didEnter_deleteEverything = false
		//
		val uuid = UUID.randomUUID().toString()
		override fun identifier(): String {
			return uuid
		}
		override fun passwordController_DeleteEverything(): String? {
			didEnter_deleteEverything = true // assertTrue this later
			return null // simulate no error
		}
	}
	@Test fun mockedPasswords__deleteEverything_noError()
	{
		val passwordController = MainApplication.serviceLocator.passwordController
		assertTrue("Expected a password to have already been saved for this test", passwordController.hasUserSavedAPassword)
		//
		//
		passwordController.addRegistrantForDeleteEverything(MockedPasswords_DeleteEverythingRegistrant_NoError)
		// add this rather than the didError one or the test will fail
		//
		var didSee_havingDeletedEverything = false
		val _7 = passwordController.havingDeletedEverything_didDeconstructBootedStateAndClearPassword_fns.startObserving(
			{ emitter, _ ->
				didSee_havingDeletedEverything = true
				assertEquals("Password still there after deleting everything", null, passwordController.password)
			}
		)
		val _8 = passwordController.didErrorWhileDeletingEverything_fns.startObserving(
			{ emitter, err_str ->
				assertTrue(err_str, false) // we don't expect this to fail here
			}
		)
		//
		var deleteEverythingFinished = false
		passwordController.initiate_deleteEverything()
		// and wait for errors, if any...
		Thread.sleep(5) // almost certain this is not necessary for waiting for any assert fails in the emitter listeners above
		assertTrue("Delete everything unexpectedly finished without seeing error", didSee_havingDeletedEverything)
		//
		// This may not be necessary but the above code may become asynchronous
		Thread.sleep(50) // TODO: see if we can remove this
		//
		assertTrue("Unexpectedly didn't enter MockedPasswords_DeleteEverythingRegistrant_NoError change pw method", MockedPasswords_DeleteEverythingRegistrant_NoError.didEnter_deleteEverything)
		assertTrue("Unexpectedly didn't see 'havingDeletedEverything...' event", didSee_havingDeletedEverything)
	}
	//
	// UserIdle
	val mockedUserIdle__LogTag = "mockedUserIdle"
	object MockedUserIdle_CorrectPasswordEntryDelegate: PasswordEntryDelegate
	{
		val uuid = UUID.randomUUID().toString()
		val getExistingUserInput = MockedPasswords_CorrectEntryDelegate.createNewUserInput
		//
		override fun identifier(): String {
			return this.uuid
		}

		public override fun getUserToEnterNewPasswordAndType(
			isForChangePassword: Boolean,
			enterNewPasswordAndType_cb: (
				didCancel_orNull: Boolean?,
				obtainedPasswordString: Password?,
				passwordType: PasswordType?
			) -> Unit
		) {
			assertTrue("Not expecting to be asked to enter new password", false)
		}
		override fun getUserToEnterExistingPassword(
			isForChangePassword: Boolean,
			isForAuthorizingAppActionOnly: Boolean,
			customNavigationBarTitle: String?,
			enterExistingPassword_cb: (
				didCancel_orNull: Boolean?,
				obtainedPasswordString: Password?
			) -> Unit
		) {
			assertTrue(isForChangePassword == false)
			//
			enterExistingPassword_cb(
				false, // didn't cancel
				this.getExistingUserInput
			)
		}
	}
	object MockedUserIdle_Short_IdleTimeoutAfterS_SettingsProvider: IdleTimeoutAfterS_SettingsProvider
	{
		//
		// Constants - Default values
		override val default_appTimeoutAfterS: Long = 5
		//
		// Constants - Special states
		override val appTimeoutAfterS_neverValue: Long = -1
		//
		// Properties
		override val appTimeoutAfterS_nullForDefault_orNeverValue: Long? = null // unused
	}
	@Test fun mockedUserIdle__testIdleBreakThenLockdown()
	{ // This test should be run while the password is the original password - before it's been changed
		//
		val applicationContext = MainApplication.instance.applicationContext
		//
		val idleTimeoutAfterS_settingsProvider = MockedUserIdle_Short_IdleTimeoutAfterS_SettingsProvider // pretending to be the SettingsController
		val userIdleController = UserIdleController()
		userIdleController.init_idleTimeoutAfterS_settingsProvider(idleTimeoutAfterS_settingsProvider)
		userIdleController.setup()
		//
		val passwordController = PasswordController()
		passwordController.init_applicationContext(applicationContext)
		passwordController.init_userIdleController(userIdleController)
		//
		//
		passwordController.setPasswordEntryDelegate(MockedUserIdle_CorrectPasswordEntryDelegate) // for this test
		//
		assertTrue("Expected a password to have already been saved for this test", passwordController.hasUserSavedAPassword)
		assertFalse("Expected a password not to have been entered at the beginning of this test", passwordController.hasUserEnteredValidPasswordYet)
		//
		passwordController.OnceBootedAndPasswordObtained(
			fn = { password, passwordType ->
				assertTrue(idleTimeoutAfterS_settingsProvider.appTimeoutAfterS_nullForDefault_orNeverValue == null)
				assertTrue("Expected a password to have been entered by now", passwordController.hasUserEnteredValidPasswordYet)
				val checkNotYetLockedAfter_s = idleTimeoutAfterS_settingsProvider.default_appTimeoutAfterS - 1 // 1s before idle kicks in
				val checkNotYetLocked_timer = Timer("checkNotYetLocked", false)
				checkNotYetLocked_timer.schedule(
					delay = 1000*checkNotYetLockedAfter_s,
					action = {
						assertFalse("isUserIdle should NOT be true after only ${checkNotYetLockedAfter_s}s", userIdleController.isUserIdle)
						assertTrue("Expected a password to still have been entered here", passwordController.hasUserEnteredValidPasswordYet)
						userIdleController.breakIdle() // simulate an Activity reporting a touch on the screen
						val checkStillNotYetLockedAfter_s = idleTimeoutAfterS_settingsProvider.default_appTimeoutAfterS - 1 // 1s before idle kicks in
						val checkStillNotYetLocked_timer = Timer("checkStillNotYetLocked", false)
						checkStillNotYetLocked_timer.schedule(
							delay = 1000*checkStillNotYetLockedAfter_s,
							action = {
								assertFalse("isUserIdle should NOT be true ${checkNotYetLockedAfter_s}s after breaking user idle", userIdleController.isUserIdle)
								assertNotNull("Password not expected to be null after unlock when user not idle", passwordController.password)
								assertTrue("Password expected to be entered after an unlock when user not idle", passwordController.hasUserEnteredValidPasswordYet)
								val checkIsLockedAfter_s: Long = 1 + 1 // at default_appTimeoutAfterS + 1
								val checkIsLocked_timer = Timer("checkIsLocked", false)
								checkIsLocked_timer.schedule(
									delay = 1000*checkIsLockedAfter_s,
									action = {
										assertTrue("isUserIdle SHOULD be true ${checkIsLockedAfter_s}s after ${checkNotYetLockedAfter_s}s after breaking user idle", userIdleController.isUserIdle)
										assertFalse("Password expected to no longer be after idle", passwordController.hasUserEnteredValidPasswordYet)
										assertNull("Password expected to be null after user idle", passwordController.password)
										//
										passwordController.OnceBootedAndPasswordObtained(
											fn = { password, passwordType ->
												//
												userIdleController.breakIdle() // simulate an Activity reporting a touch on the screen
												// ^-- as if the user entered their password - this would actually be best done in the password entry delegate
												//
												assertTrue("Expected a password to have been entered after idle lockdown -> password re-entry", passwordController.hasUserEnteredValidPasswordYet)
											},
											userCanceled_fn = {
												assertTrue(false)
											}
										)
									}
								)
							}
						)
					}
				)
			},
			userCanceled_fn = {
				assertTrue(false) // not expecting this
			}
		)
		//
		val numberOfTimerDelaysToWaitFor = 3
		val effective_numberOfTimerDelaysToWaitFor = numberOfTimerDelaysToWaitFor + 1 // just for extra padding
		Thread.sleep(effective_numberOfTimerDelaysToWaitFor * idleTimeoutAfterS_settingsProvider.default_appTimeoutAfterS * 1000) // just wait a longish time for the timers above
		//
		// no failure seen by now
	}
	@Test fun mockedUserIdle__verifyUserIdleDisableReEnable() = runBlocking<Unit>
	{ // This test should be run while the password is the original password - before it's been changed
		//
		val idleTimeoutAfterS_settingsProvider = MockedUserIdle_Short_IdleTimeoutAfterS_SettingsProvider // pretending to be the SettingsController
		assertTrue(idleTimeoutAfterS_settingsProvider.appTimeoutAfterS_nullForDefault_orNeverValue == null)
		//
		val userIdleController = UserIdleController()
		userIdleController.init_idleTimeoutAfterS_settingsProvider(idleTimeoutAfterS_settingsProvider)
		userIdleController.setup()
		//
		val checkNotYetLockedAfter_s = idleTimeoutAfterS_settingsProvider.default_appTimeoutAfterS - 1 // 1s before idle kicks in
		delay(checkNotYetLockedAfter_s, TimeUnit.SECONDS)
		//
		assertFalse("isUserIdle should NOT be true after only ${checkNotYetLockedAfter_s}s", userIdleController.isUserIdle)
		val checkIsLockedAfter_s: Long = 1 + 1 // at default_appTimeoutAfterS + 1
		delay(checkIsLockedAfter_s, TimeUnit.SECONDS)
		//
		assertTrue("isUserIdle SHOULD be true ${checkIsLockedAfter_s}s after ${checkNotYetLockedAfter_s}s after breaking user idle", userIdleController.isUserIdle)
		userIdleController.breakIdle() // simulate an Activity reporting a touch on the screen
		assertFalse("isUserIdle should now NOT be true after breaking user idle", userIdleController.isUserIdle)
		val checkStillNotYetLockedAfter_s = idleTimeoutAfterS_settingsProvider.default_appTimeoutAfterS - 1 // 1s before idle kicks in
		delay(checkStillNotYetLockedAfter_s, TimeUnit.SECONDS)
		//
		assertFalse("isUserIdle should NOT be true ${checkNotYetLockedAfter_s}s after breaking user idle", userIdleController.isUserIdle)
		userIdleController.temporarilyDisable_userIdle()
		val checkIsStillNotLockedAfter_s: Long = 1 + 1 // at default_appTimeoutAfterS + 1
		delay(checkIsStillNotLockedAfter_s, TimeUnit.SECONDS)
		//
		assertFalse("isUserIdle should still NOT be true after temporarily disabling user idle", userIdleController.isUserIdle)
		userIdleController.reEnable_userIdle()
		val checkStillNotYetLockedAfterReEnable_s = idleTimeoutAfterS_settingsProvider.default_appTimeoutAfterS - 1 // 1s before idle kicks in
		delay(checkStillNotYetLockedAfterReEnable_s, TimeUnit.SECONDS)
		//
		assertFalse("isUserIdle should still NOT be true after re-enabling user idle", userIdleController.isUserIdle)
		val checkIsFinallyLockedAfter_s: Long = 1 + 1 // at default_appTimeoutAfterS + 1
		delay(checkIsFinallyLockedAfter_s, TimeUnit.SECONDS)
		//
		assertTrue("isUserIdle SHOULD be true ${checkIsFinallyLockedAfter_s}s after ${checkStillNotYetLockedAfterReEnable_s}s after re-enabling user idle", userIdleController.isUserIdle)
	}
	//
	//
	@Test fun mockedCurrencies__verifyConversions()
	{
		val xmrAmount = MoneroAmount("2618000000")
		val xmrAmountDouble = DoubleFromMoneroAmount(xmrAmount)
		val expected_xmrAmountDouble = 0.002618
		assertTrue("Expected xmrAmountDouble of ${xmrAmountDouble} == Double(xmrAmount)", xmrAmountDouble == expected_xmrAmountDouble)
		//
		var didSeeUpdate = false
		CcyConversionRatesController.didUpdateAvailabilityOfRates_fns.startObserving { emitter, _ ->
			didSeeUpdate = true
		}
		//
		val xmrToCcyRatesByCcy = mapOf(
			Currency.USD to 126.66
		)
		CcyConversionRatesController.set_xmrToCcyRatesByCcy(xmrToCcyRatesByCcy)
		//
		assertTrue("Expected to have seen rates update by now", didSeeUpdate)
		//
		val inCurrency_amountDouble = Currency.USD.displayUnitsRounded_amountInCurrency(xmrAmount)
		val expected_inCurrency_amountDouble = 0.33
		assertTrue("Expected inCurrency_amountDouble of ${inCurrency_amountDouble} to equal ${expected_inCurrency_amountDouble}", inCurrency_amountDouble == expected_inCurrency_amountDouble)
		//
		val inCurrency_amountFormattedString = Currency.USD.nonAtomicCurrency_localized_formattedString(
			inCurrency_amountDouble!!,
			decimalSeparator = "." // just to make it possible to do the string comparison here
		)
		val expected_inCurrency_amountFormattedString = "0.33"
		assertTrue("Expected inCurrency_amountFormattedString of ${inCurrency_amountFormattedString} to equal ${expected_inCurrency_amountFormattedString}", inCurrency_amountFormattedString == expected_inCurrency_amountFormattedString)
		//
		//
		val backInMonero_rounded_amountDouble = Currency.rounded_ccyConversionRateCalculated_moneroAmountDouble(
			inCurrency_amountDouble,
			Currency.USD
		)
		val expected_backInMonero_rounded_amountDouble = expected_xmrAmountDouble.toBigDecimal().setScale(
			Currency.ccyConversionRateCalculated_moneroAmountDouble_roundingPlaces,
			RoundingMode.HALF_EVEN /* to mimic round() behavior */
		).toDouble()
		assertTrue("Expected backInMonero_rounded_amountDouble of ${backInMonero_rounded_amountDouble} to equal ${expected_backInMonero_rounded_amountDouble}", backInMonero_rounded_amountDouble == expected_backInMonero_rounded_amountDouble)
	}
	@Test fun moneroAmounts__verifyParsingAndFormatting()
	{
		val correct_xmrAmountDouble = -0.002618
		val correct_xmrAmountDoubleString = "-0.002618"
		val correct_xmrAmount = MoneroAmount("-2618000000")

		val parsed_xmrAmount = MoneroAmountFrom(correct_xmrAmountDoubleString)
		assertTrue("Expected parsed_xmrAmount of ${parsed_xmrAmount} to equal correct_xmrAmount of ${correct_xmrAmount}", parsed_xmrAmount == correct_xmrAmount)
		//
		val formatted_xmrAmountDoubleString = FormattedString(correct_xmrAmount)
		assertTrue("Expected formatted_xmrAmountDoubleString of ${formatted_xmrAmountDoubleString} to equal correct_xmrAmountDoubleString of ${correct_xmrAmountDoubleString}", formatted_xmrAmountDoubleString == correct_xmrAmountDoubleString)
		//
		val converted_xmrAmountDouble = DoubleFromMoneroAmount(correct_xmrAmount)
		assertTrue("Expected converted_xmrAmountDouble of ${converted_xmrAmountDouble} to equal correct_xmrAmountDouble of ${correct_xmrAmountDouble}", converted_xmrAmountDouble == correct_xmrAmountDouble)
		//
		val converted_xmrAmount = MoneroAmountFrom(correct_xmrAmountDouble)
		assertTrue("Expected converted_xmrAmount of ${converted_xmrAmount} to equal correct_xmrAmount of ${correct_xmrAmount}", converted_xmrAmount == correct_xmrAmount)
	}
	//
	//
	@Test fun settings_settingAndGetting()
	{
		// First, determine whether Settings already saved... before any boot
		val settingsAlreadySaved = SettingsController.hasExisting_saved_document
		//
		val controller = MainApplication.serviceLocator/*lazy*/.settingsController
		assertTrue("Expected settingsController.hasBooted", controller.hasBooted)
		//
		val to_specificAPIAddressURLAuthority = "myserver.com"
		val to_appTimeoutAfterS: Long = 60
		val to_authentication__requireWhenSending = !controller.default_authentication__requireWhenSending
		val to_authentication__requireToShowWalletSecrets = !controller.default_authentication__requireToShowWalletSecrets
		val to_authentication__tryBiometric = !controller.default_authentication__tryBiometric
		val to_displayCurrencySymbol = Currency.BRL.currencySymbol
		//
		val expectedInitial_specificAPIAddressURLAuthority: String? = if (settingsAlreadySaved) to_specificAPIAddressURLAuthority else null
		val expectedInitial_appTimeoutAfterS: Long? = if (settingsAlreadySaved) to_appTimeoutAfterS else controller.default_appTimeoutAfterS
		val expectedInitial_authentication__requireWhenSending: Boolean = if (settingsAlreadySaved) to_authentication__requireWhenSending else controller.default_authentication__requireWhenSending
		val expectedInitial_authentication__requireToShowWalletSecrets: Boolean = if (settingsAlreadySaved) to_authentication__requireToShowWalletSecrets else controller.default_authentication__requireToShowWalletSecrets
		val expectedInitial_authentication__tryBiometric: Boolean = if (settingsAlreadySaved) to_authentication__tryBiometric else controller.default_authentication__tryBiometric
		val expectedInitial_displayCurrencySymbol: CurrencySymbol = if (settingsAlreadySaved) to_displayCurrencySymbol else controller.default_displayCurrencySymbol
		//
		assertEquals("Expected controller.specificAPIAddressURLAuthority of ${controller.specificAPIAddressURLAuthority} to equal ${expectedInitial_specificAPIAddressURLAuthority}", expectedInitial_specificAPIAddressURLAuthority, controller.specificAPIAddressURLAuthority)
		assertEquals("Expected controller.appTimeoutAfterS_nullForDefault_orNeverValue of ${controller.appTimeoutAfterS_nullForDefault_orNeverValue} to equal ${expectedInitial_appTimeoutAfterS}", expectedInitial_appTimeoutAfterS, controller.appTimeoutAfterS_nullForDefault_orNeverValue)
		assertEquals("Expected controller.authentication__requireWhenSending of ${controller.authentication__requireWhenSending} to equal ${expectedInitial_authentication__requireWhenSending}", expectedInitial_authentication__requireWhenSending, controller.authentication__requireWhenSending)
		assertEquals("Expected controller.authentication__requireToShowWalletSecrets of ${controller.authentication__requireToShowWalletSecrets} to equal ${expectedInitial_authentication__requireToShowWalletSecrets}", expectedInitial_authentication__requireToShowWalletSecrets, controller.authentication__requireToShowWalletSecrets)
		assertEquals("Expected controller.authentication__tryBiometric of ${controller.authentication__tryBiometric} to equal ${expectedInitial_authentication__tryBiometric}", expectedInitial_authentication__tryBiometric, controller.authentication__tryBiometric)
		assertEquals("Expected controller.displayCurrencySymbol of ${controller.displayCurrencySymbol} to equal ${expectedInitial_displayCurrencySymbol}", expectedInitial_displayCurrencySymbol, controller.displayCurrencySymbol)
		//
		var saw_changed_specificAPIAddressURLAuthority_fns = false
		controller.changed_specificAPIAddressURLAuthority_fns.startObserving({ emitter, _ ->
			assertFalse("Expected saw to be false", saw_changed_specificAPIAddressURLAuthority_fns)
			saw_changed_specificAPIAddressURLAuthority_fns = true
		})
		controller.set_specificAPIAddressURLAuthority(to_specificAPIAddressURLAuthority)
		assertEquals("Expected controller.specificAPIAddressURLAuthority of ${controller.specificAPIAddressURLAuthority} to equal ${to_specificAPIAddressURLAuthority}", controller.specificAPIAddressURLAuthority, to_specificAPIAddressURLAuthority)
		//
		var saw_changed_appTimeoutAfterS_nullForDefault_orNeverValue_fns = false
		controller.changed_appTimeoutAfterS_nullForDefault_orNeverValue_fns.startObserving({ emitter, _ ->
			assertFalse("Expected saw to be false", saw_changed_appTimeoutAfterS_nullForDefault_orNeverValue_fns)
			saw_changed_appTimeoutAfterS_nullForDefault_orNeverValue_fns = true
		})
		controller.set_appTimeoutAfterS_nullForDefault_orNeverValue(to_appTimeoutAfterS)
		assertEquals("Expected controller.appTimeoutAfterS_nullForDefault_orNeverValue of ${controller.appTimeoutAfterS_nullForDefault_orNeverValue} to equal ${to_appTimeoutAfterS}", controller.appTimeoutAfterS_nullForDefault_orNeverValue, to_appTimeoutAfterS)
		//
		var saw_changed_authentication__requireWhenSending_fns = false
		controller.changed_authentication__requireWhenSending_fns.startObserving({ emitter, _ ->
			assertFalse("Expected saw to be false", saw_changed_authentication__requireWhenSending_fns)
			saw_changed_authentication__requireWhenSending_fns = true
		})
		controller.set_authentication__requireWhenSending(to_authentication__requireWhenSending)
		assertEquals("Expected controller.authentication__requireWhenSending of ${controller.authentication__requireWhenSending} to equal ${to_authentication__requireWhenSending}", controller.authentication__requireWhenSending, to_authentication__requireWhenSending)
		//
		var saw_changed_authentication__requireToShowWalletSecrets_fns = false
		controller.changed_authentication__requireToShowWalletSecrets_fns.startObserving({ emitter, _ ->
			assertFalse("Expected saw to be false", saw_changed_authentication__requireToShowWalletSecrets_fns)
			saw_changed_authentication__requireToShowWalletSecrets_fns = true
		})
		controller.set_authentication__requireToShowWalletSecrets(to_authentication__requireToShowWalletSecrets)
		assertEquals("Expected controller.authentication__requireToShowWalletSecrets of ${controller.authentication__requireToShowWalletSecrets} to equal ${to_authentication__requireToShowWalletSecrets}", controller.authentication__requireToShowWalletSecrets, to_authentication__requireToShowWalletSecrets)
		//
		var saw_changed_authentication__tryBiometric_fns = false
		controller.changed_authentication__tryBiometric_fns.startObserving({ emitter, _ ->
			assertFalse("Expected saw to be false", saw_changed_authentication__tryBiometric_fns)
			saw_changed_authentication__tryBiometric_fns = true
		})
		controller.set_authentication__tryBiometric(to_authentication__tryBiometric)
		assertEquals("Expected controller.authentication__tryBiometric of ${controller.authentication__tryBiometric} to equal ${to_authentication__tryBiometric}", controller.authentication__tryBiometric, to_authentication__tryBiometric)
		//
		var saw_changed_displayCurrencySymbol_fns = false
		controller.changed_displayCurrencySymbol_fns.startObserving({ emitter, _ ->
			assertFalse("Expected saw to be false", saw_changed_displayCurrencySymbol_fns)
			saw_changed_displayCurrencySymbol_fns = true
		})
		controller.set_displayCurrencySymbol(to_displayCurrencySymbol)
		assertEquals("Expected controller.displayCurrencySymbol of ${controller.displayCurrencySymbol} to equal ${to_displayCurrencySymbol}", controller.displayCurrencySymbol, to_displayCurrencySymbol)
		//
		Thread.sleep(50) // wait for async notifies - this could be done with a coroutine delay
		assertTrue("Expected to see changed_specificAPIAddressURLAuthority_fns", saw_changed_specificAPIAddressURLAuthority_fns)
		assertTrue("Expected to see changed_appTimeoutAfterS_nullForDefault_orNeverValue_fns", saw_changed_appTimeoutAfterS_nullForDefault_orNeverValue_fns)
		assertTrue("Expected to see changed_authentication__requireWhenSending_fns", saw_changed_authentication__requireWhenSending_fns)
		assertTrue("Expected to see changed_authentication__requireToShowWalletSecrets_fns", saw_changed_authentication__requireToShowWalletSecrets_fns)
		assertTrue("Expected to see changed_authentication__tryBiometric_fns", saw_changed_authentication__tryBiometric_fns)
		assertTrue("Expected to see changed_displayCurrencySymbol_fns", saw_changed_displayCurrencySymbol_fns)
	}
}