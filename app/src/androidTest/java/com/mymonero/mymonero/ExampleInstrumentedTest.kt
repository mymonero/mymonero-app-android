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
	{
		override var password: String? = "dummy password"
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
			val plaintext_documentContentString = PersistableObject.new_plaintextJSONStringFrom(
				encrypted_documentContentString,
				passwordProvider = MockedPasswordProvider
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
			val plaintext_documentContentString = PersistableObject.new_plaintextJSONStringFrom(
				encrypted_documentContentString,
				passwordProvider = MockedPasswordProvider
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
}