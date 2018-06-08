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
import kotlinx.android.synthetic.main.activity_main.*

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

import android.util.Log
import org.junit.Assert

import java.util.UUID

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
	val mockedSavedObjects__addtlValDummy = "Some extra test data"
	object MockedPasswordProvider: PasswordProvider
	{ // this class is no longer necessary
		override var password: Password? = "dummy password"
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
		obj.addtlVal = mockedSavedObjects__addtlValDummy
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
			assertEquals(mockedSavedObjects__addtlValDummy, listedObjectInstance.addtlVal)
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
			assertTrue(PasswordController.password == null && PasswordController.passwordType == null)
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
			assertTrue(PasswordController.password != null && PasswordController.passwordType != null)
			//
			enterExistingPassword_cb(
				false, // didn't cancel
				this.getExistingUserInput
			)
		}
	}
	@Test fun mockedPasswords__correctEntry_getPassword_createIfNecessary()
	{
		PasswordController.setPasswordEntryDelegate(MockedPasswords_CorrectEntryDelegate) // for this test
		//
		val _1 = PasswordController.erroredWhileSettingNewPassword_fns.startObserving(
			{ emitter, err_str ->
				assertTrue(err_str, false) // should never see this
			}
		)
		val _2 = PasswordController.erroredWhileGettingExistingPassword_fns.startObserving(
			{ emitter, err_str ->
				assertTrue(err_str, false) // should never see this
			}
		)
		val _3 = PasswordController.canceledWhileEnteringNewPassword_fns.startObserving(
			{ emitter, err_str ->
				assertTrue("Unexpected cancel", false) // should never see this
			}
		)
		val _4 = PasswordController.canceledWhileEnteringExistingPassword_fns.startObserving(
			{ emitter, err_str ->
				assertTrue("Unexpected cancel", false) // should never see this
			}
		)
		//
		PasswordController.OnceBootedAndPasswordObtained(
			fn = { password, passwordType ->
				Log.d(mockedPasswords__LogTag, "Obtained ${passwordType} from user ${password}")
				//
				assertTrue(PasswordController.password == password)
				assertTrue(PasswordController.passwordType == passwordType)
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
			assertTrue(PasswordController.password == null) // because we haven't entered it yet
			assertTrue(PasswordController.passwordType == MockedPasswords_CorrectEntryDelegate.expectedPasswordType)
			//
			enterExistingPassword_cb(
				false, // didn't cancel
				this.userInput // feed incorrect password - and expect fail
			)
		}
	}
	@Test fun mockedPasswords__incorrectEntry_getPassword()
	{
		PasswordController.setPasswordEntryDelegate(MockedPasswords_IncorrectEntryDelegate) // for this test
		//
		var didError = false
		val _1 = PasswordController.erroredWhileSettingNewPassword_fns.startObserving(
			{ emitter, err_str ->
				assertTrue(err_str, false) // should never see this
			}
		)
		val _2 = PasswordController.erroredWhileGettingExistingPassword_fns.startObserving(
			{ emitter, err_str ->
				didError = true
				// we expect this case
				assertEquals(err_str, "Incorrect password") // this may be too fragile but making it dynamic might make the test less useful
			}
		)
		val _3 = PasswordController.canceledWhileEnteringNewPassword_fns.startObserving(
			{ emitter, dummy ->
				assertTrue("Unexpected cancel", false) // should never see this
			}
		)
		val _4 = PasswordController.canceledWhileEnteringExistingPassword_fns.startObserving(
			{ emitter, dummy ->
				assertTrue("Unexpected cancel", false) // should never see this
			}
		)
		//
		PasswordController.OnceBootedAndPasswordObtained(
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
		PasswordController.setPasswordEntryDelegate(MockedPasswords_VerifyUserAuth_Correct_EntryDelegate) // for this test
		//
		var didError = false
		val _1 = PasswordController.erroredWhileSettingNewPassword_fns.startObserving(
			{ emitter, err_str ->
				assertTrue(err_str, false) // should never see this
			}
		)
		val _2 = PasswordController.erroredWhileGettingExistingPassword_fns.startObserving(
			{ emitter, err_str ->
				assertTrue(err_str, false) // should never see this
			}
		)
		val _3 = PasswordController.canceledWhileEnteringNewPassword_fns.startObserving(
			{ emitter, dummy ->
				assertTrue("Unexpected cancel", false) // should never see this
			}
		)
		val _4 = PasswordController.canceledWhileEnteringExistingPassword_fns.startObserving(
			{ emitter, dummy ->
				assertTrue("Unexpected cancel", false) // should never see this
			}
		)
		//
		val _Z = PasswordController.errorWhileAuthorizingForAppAction_fns.startObserving(
			{ emitter, err_str ->
				assertTrue(err_str, false) // not expecting this here
			}
		)
		val _Y = PasswordController.successfullyAuthenticatedForAppAction_fns.startObserving(
			{ emitter, dummy ->
				// expecting this
			}
		)
		//
		var successfulAuthSeen = false
		PasswordController.OnceBootedAndPasswordObtained(
			fn = { password, passwordType ->
				assertEquals("Unexpectedly different password after successful user auth", MockedPasswords_CorrectEntryDelegate.createNewUserInput, password)
				PasswordController.initiate_verifyUserAuthenticationForAction(
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
		PasswordController.setPasswordEntryDelegate(MockedPasswords_VerifyUserAuth_Correct_EntryDelegate) // for initial unlock
		//
		var didError = false
		val _1 = PasswordController.erroredWhileSettingNewPassword_fns.startObserving(
			{ emitter, err_str ->
				assertTrue(err_str, false) // should never see this
			}
		)
		val _2 = PasswordController.erroredWhileGettingExistingPassword_fns.startObserving(
			{ emitter, err_str ->
				assertTrue("Specifically not expecting to see this erroredWhileGettingExistingPassword_fns invocation on an incorrect user pw entry for authorization purposes!", false)
			}
		)
		var didSeeErrorWhileAuthorizingForAppAction = false
		val _Z = PasswordController.errorWhileAuthorizingForAppAction_fns.startObserving(
			{ emitter, err_str ->
				didSeeErrorWhileAuthorizingForAppAction = true
			}
		)
		val _Y = PasswordController.successfullyAuthenticatedForAppAction_fns.startObserving(
			{ emitter, dummy ->
				assertTrue("Unexpected success during incorrect auth", false) // not expecting this here
			}
		)
		val _3 = PasswordController.canceledWhileEnteringNewPassword_fns.startObserving(
			{ emitter, dummy ->
				assertTrue("Unexpected cancel", false) // should never see this
			}
		)
		val _4 = PasswordController.canceledWhileEnteringExistingPassword_fns.startObserving(
			{ emitter, dummy ->
				assertTrue("Unexpected cancel", false) // should never see this
			}
		)
		//
		var successfulAuthSeen = false
		PasswordController.OnceBootedAndPasswordObtained(
			fn = { password, passwordType ->
				assertEquals("Unexpectedly different password after successful user auth", MockedPasswords_CorrectEntryDelegate.createNewUserInput, password)
				//
				// now switch over to this for incorrect entry:
				PasswordController.clearPasswordEntryDelegate(MockedPasswords_VerifyUserAuth_Correct_EntryDelegate) // must first unset this or we will cause a runtime assertion exception
				PasswordController.setPasswordEntryDelegate(MockedPasswords_VerifyUserAuth_Incorrect_EntryDelegate)
				//
				PasswordController.initiate_verifyUserAuthenticationForAction(
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
				assertTrue("Expected pw to match initial correct entry on enter existing for change pw", PasswordController.password == MockedPasswords_CorrectEntryDelegate.createNewUserInput)
				assertTrue("Expected pw type to match initial correct entry on enter existing for change pw", PasswordController.passwordType == MockedPasswords_CorrectEntryDelegate.expectedPasswordType)
				//
				enterExistingPassword_cb(
					false, // didn't cancel
					MockedPasswords_CorrectEntryDelegate.createNewUserInput // being asked to verify original password
				)
			} else {
				assertTrue("Expected pw to be nil on entry that was not for change pw", PasswordController.password == null)
				assertTrue("Expected pw type not to be nil on initial boot not for changing pw because one should have been saved", PasswordController.passwordType != null)
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
			assertTrue("Expected pw not to be nil on entering new password", PasswordController.password == MockedPasswords_CorrectEntryDelegate.createNewUserInput)

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
		PasswordController.setPasswordEntryDelegate(MockedPasswords_ChangePasswordEntryDelegate) // for this test
		//
		PasswordController.addRegistrantForChangePassword(MockedPasswords_ChangePasswordRegistrant_NoError)
		//
		val _1 = PasswordController.erroredWhileSettingNewPassword_fns.startObserving(
			{ emitter, err_str ->
				assertTrue(err_str, false) // should never see this
			}
		)
		val _2 = PasswordController.erroredWhileGettingExistingPassword_fns.startObserving(
			{ emitter, err_str ->
				assertTrue(err_str, false) // should never see this - unless we're running this test after having already changed the password
			}
		)
		val _3 = PasswordController.canceledWhileEnteringNewPassword_fns.startObserving(
			{ emitter, dummy ->
				assertTrue("Unexpected cancel", false) // should never see this
			}
		)
		val _4 = PasswordController.canceledWhileEnteringExistingPassword_fns.startObserving(
			{ emitter, dummy ->
				assertTrue("Unexpected cancel", false) // should never see this
			}
		)
		val _5 = PasswordController.errorWhileChangingPassword_fns.startObserving(
			{ emitter, err_str ->
				assertTrue(err_str, false) // should never see this
			}
		)
		val _6 = PasswordController.canceledWhileChangingPassword_fns.startObserving(
			{ emitter, dummy ->
				assertTrue("Unexpected cancel", false) // should never see this
			}
		)
		//
		var changePWFinished = false
		PasswordController.OnceBootedAndPasswordObtained(
			fn = { password, passwordType ->
				assertEquals("On booting pw controller for change pw test, password was not MockedPasswords_CorrectEntryDelegate.createNewUserInput", MockedPasswords_CorrectEntryDelegate.createNewUserInput, password)
				// now that booted, we can change pw
				PasswordController.initiate_changePassword()
				// and wait for errors, if any...
				assertEquals("Changed password not as expected", MockedPasswords_ChangePasswordEntryDelegate.changePasswordTo_userInput, PasswordController.password)
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
	object MockedPasswords_DeleteEverythingEntryDelegate: PasswordEntryDelegate
	{
		val uuid = UUID.randomUUID().toString()
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
			assertFalse("Unexpected isForChangePassword", isForChangePassword)
			assertTrue("Expected pw to be nil on entry that was not for change pw", PasswordController.password == null)
			assertTrue("Expected pw type not to be nil on initial boot not for changing pw because one should have been saved", PasswordController.passwordType != null)
			//
			enterExistingPassword_cb(
				false,
				MockedPasswords_ChangePasswordEntryDelegate.changePasswordTo_userInput // enter changed password
			)
		}
		public override fun getUserToEnterNewPasswordAndType(
			isForChangePassword: Boolean,
			enterNewPasswordAndType_cb: (
				didCancel_orNull: Boolean?,
				obtainedPasswordString: Password?,
				passwordType: PasswordType?
			) -> Unit
		) {
			assertTrue("Unexpected call of getUserToEnterNewPasswordAndType", false)
		}
	}
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
		PasswordController.setPasswordEntryDelegate(MockedPasswords_DeleteEverythingEntryDelegate) // for this test
		//
		PasswordController.addRegistrantForDeleteEverything(MockedPasswords_DeleteEverythingRegistrant_DidError)
		// we must add this or the test will fail, b/c the delete everything will succeed
		//
		val _1 = PasswordController.erroredWhileSettingNewPassword_fns.startObserving(
			{ emitter, err_str ->
				assertTrue(err_str, false) // should never see this
			}
		)
		val _2 = PasswordController.erroredWhileGettingExistingPassword_fns.startObserving(
			{ emitter, err_str ->
				assertTrue(err_str, false) // should never see this - unless we're running this test after having already changed the password
			}
		)
		val _3 = PasswordController.canceledWhileEnteringNewPassword_fns.startObserving(
			{ emitter, dummy ->
				assertTrue("Unexpected cancel", false) // should never see this
			}
		)
		val _4 = PasswordController.canceledWhileEnteringExistingPassword_fns.startObserving(
			{ emitter, dummy ->
				assertTrue("Unexpected cancel", false) // should never see this
			}
		)
		val _5 = PasswordController.errorWhileChangingPassword_fns.startObserving(
			{ emitter, err_str ->
				assertTrue(err_str, false) // should never see this
			}
		)
		val _6 = PasswordController.canceledWhileChangingPassword_fns.startObserving(
			{ emitter, dummy ->
				assertTrue("Unexpected cancel", false) // should never see this
			}
		)
		val _7 = PasswordController.havingDeletedEverything_didDeconstructBootedStateAndClearPassword_fns.startObserving(
			{ emitter, dummy ->
				assertTrue("Unexpected success of deleteEverything in didError test", false)
			}
		)
		var didSeeErrorDuringDeleteEverything = false
		val _8 = PasswordController.didErrorWhileDeletingEverything_fns.startObserving(
			{ emitter, err_str ->
				// we expect this to fail here
				assertEquals("err_str returned differs from the expected str", MockedPasswords_DeleteEverythingRegistrant_DidError.errStr, err_str)
				didSeeErrorDuringDeleteEverything = true
			}
		)
		//
		var deleteEverythingFinished = false
		PasswordController.OnceBootedAndPasswordObtained(
			fn = { password, passwordType ->
				assertEquals("On booting pw controller for delete everything test, password was not MockedPasswords_ChangePasswordEntryDelegate.changePasswordTo_userInput", MockedPasswords_ChangePasswordEntryDelegate.changePasswordTo_userInput, password)
				// now that booted, we can change pw
				PasswordController.initiate_deleteEverything()
				// and wait for errors, if any...
				Thread.sleep(5) // almost certain this is not necessary for waiting for any assert fails in the emitter listeners above
				deleteEverythingFinished = true // we should basically just be able to assert false here
				assertTrue("Delete everything unexpectedly finished without seeing error", didSeeErrorDuringDeleteEverything)
			},
			userCanceled_fn = {
				assertTrue("Unexpected cancel", false) // not expecting that
			}
		)
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
		PasswordController.setPasswordEntryDelegate(MockedPasswords_DeleteEverythingEntryDelegate) // for this test
		//
		PasswordController.addRegistrantForDeleteEverything(MockedPasswords_DeleteEverythingRegistrant_NoError)
		// add this rather than the didError one or the test will fail
		//
		val _1 = PasswordController.erroredWhileSettingNewPassword_fns.startObserving(
			{ emitter, err_str ->
				assertTrue(err_str, false) // should never see this
			}
		)
		val _2 = PasswordController.erroredWhileGettingExistingPassword_fns.startObserving(
			{ emitter, err_str ->
				assertTrue(err_str, false) // should never see this - unless we're running this test after having already changed the password
			}
		)
		val _3 = PasswordController.canceledWhileEnteringNewPassword_fns.startObserving(
			{ emitter, dummy ->
				assertTrue("Unexpected cancel", false) // should never see this
			}
		)
		val _4 = PasswordController.canceledWhileEnteringExistingPassword_fns.startObserving(
			{ emitter, dummy ->
				assertTrue("Unexpected cancel", false) // should never see this
			}
		)
		val _5 = PasswordController.errorWhileChangingPassword_fns.startObserving(
			{ emitter, err_str ->
				assertTrue(err_str, false) // should never see this
			}
		)
		val _6 = PasswordController.canceledWhileChangingPassword_fns.startObserving(
			{ emitter, dummy ->
				assertTrue("Unexpected cancel", false) // should never see this
			}
		)
		var didSee_havingDeletedEverything = false
		val _7 = PasswordController.havingDeletedEverything_didDeconstructBootedStateAndClearPassword_fns.startObserving(
			{ emitter, dummy ->
				didSee_havingDeletedEverything = true
				assertEquals("Password still there after deleting everything", null, PasswordController.password)
			}
		)
		val _8 = PasswordController.didErrorWhileDeletingEverything_fns.startObserving(
			{ emitter, err_str ->
				assertTrue(err_str, false) // we don't expect this to fail here
			}
		)
		//
		var deleteEverythingFinished = false
		PasswordController.OnceBootedAndPasswordObtained(
			fn = { password, passwordType ->
				assertEquals("On booting pw controller for delete everything test, password was not MockedPasswords_ChangePasswordEntryDelegate.changePasswordTo_userInput", MockedPasswords_ChangePasswordEntryDelegate.changePasswordTo_userInput, password)
				// now that booted, we can change pw
				PasswordController.initiate_deleteEverything()
				// and wait for errors, if any...
				Thread.sleep(5) // almost certain this is not necessary for waiting for any assert fails in the emitter listeners above
				deleteEverythingFinished = true // we should basically just be able to assert false here
				assertTrue("Delete everything unexpectedly finished without seeing error", didSee_havingDeletedEverything)
			},
			userCanceled_fn = {
				assertTrue("Unexpected cancel", false) // not expecting that
			}
		)
		//
		// This may not be necessary but the above code may become asynchronous
		Thread.sleep(50) // TODO: see if we can remove this
		//
		assertTrue("Unexpectedly didn't finish deleteEverything", deleteEverythingFinished)
		assertTrue("Unexpectedly didn't enter MockedPasswords_DeleteEverythingRegistrant_NoError change pw method", MockedPasswords_DeleteEverythingRegistrant_NoError.didEnter_deleteEverything)
		assertTrue("Unexpectedly didn't see 'havingDeletedEverything...' event", didSee_havingDeletedEverything)
	}
}