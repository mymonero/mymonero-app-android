//
//  PersistableObject.kt
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
package com.mymonero.Persistence

import android.util.Log
import com.mymonero.KotlinUtils.EventEmitter
import com.mymonero.Passwords.Password
import com.mymonero.Passwords.PasswordProvider
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import tgio.rncryptor.RNCryptorNative
import java.nio.charset.Charset

open class PersistableObject
{
	//
	// Properties
	var passwordProvider: PasswordProvider
	//
	var _id: String? = null
	var insertedAt_sSinceEpoch: Long? = null // stored on disk as a String to preserve Long
	//
	var didFailToInitialize_flag: Boolean? = null
	var didFailToBoot_flag: Boolean? = null
	var didFailToBoot_errStr: String? = null
	//
	// Interface - Events
	// boot state change notification declarations for your convenience - not posted for you - see Wallet.swift
	val booted_fns = EventEmitter<PersistableObject, String>()
	val failedToBoot_fns = EventEmitter<PersistableObject, String>()
	//
	val willBeDeinitialized_fns = EventEmitter<PersistableObject, String>() // this is necessary since views like UITableView and UIPickerView won't necessarily call .prepareForReuse() on an unused cell (e.g. after logged-in-runtime teardown), leaving PersistableObject instances hanging around
	//
	val willBeDeleted_fns = EventEmitter<PersistableObject, String>() // this (or 'was') may end up being redundant with new .willBeDeinitialized
	val wasDeleted_fns = EventEmitter<PersistableObject, String>()
	//
	// For override
	open fun collectionName(): String {
		assert(false, {
			"You must override and implement this"
		})
		return ""
	}
	open fun new_dictRepresentation(): MutableDocumentJSON
	{
		var dict = mutableMapOf<String, Any>()
		dict["_id"] = this._id!!
		this.insertedAt_sSinceEpoch?.let {
			dict["insertedAt_sSinceEpoch"] = it.toString() // store as string and decode on parse b/c Long gets converted to double
		}
		//
		// Note: Override this method and add data you would like encrypted – but call on super
		return dict as MutableDocumentJSON
	}
	//
	// Lifecycle - Setup
	constructor(passwordProvider: PasswordProvider)
	{ // placed here for inserts
		this.passwordProvider = passwordProvider
	}
	constructor(passwordProvider: PasswordProvider, plaintextData: DocumentJSON)
	{
		this.passwordProvider = passwordProvider
		//
		this._id = plaintextData["_id"] as? DocumentId
		(plaintextData["insertedAt_sSinceEpoch"] as String).let {
			this.insertedAt_sSinceEpoch = it.toLong() // must parse from string to long
		}
		// Subclassers: Override and extract data but call on super
	}
	//
	// Lifecycle - Teardown
	// TODO: may need to add a manual deinit method called by ListController so we can call .willBeDeinitialized for observers

	//
	// Interface - Accessors - Convenience - Document Deserialization
	companion object {
		fun new_plaintextDocumentDictFromJSONString(plaintextJSONString: String): Map<String, Any>
		{
			val jsonAdapter: JsonAdapter<Map<String, Any>> = Moshi.Builder().build().adapter(
				Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
			)
			val dict = jsonAdapter.fromJson(plaintextJSONString)

			return dict!!
		}
		fun new_plaintextJSONStringFromDocumentDict(dict: Map<String, Any>): String
		{
			val jsonAdapter: JsonAdapter<Map<String, Any>> = Moshi.Builder().build().adapter(
				Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
			)
			return jsonAdapter.toJson(dict)
		}
		//
		fun new_plaintextStringFrom(encryptedString: String, password: Password): String
		{ // assumes pw has been entered
			return RNCryptorNative().decrypt(encryptedString, password)
		}
		fun new_encryptedStringFrom(plaintextString: String, password: Password): String
		{ // assumes pw has been entered
			val encryptedString = RNCryptorNative().encrypt(plaintextString, password).toString(Charset.forName("UTF-8"))

			return encryptedString
		}
	}
	//
	// Internal - Accessors
	fun new_encrypted_serializedFileRepresentation(): String {
		val plaintextDict = this.new_dictRepresentation()
		val plaintextString = new_plaintextJSONStringFromDocumentDict(plaintextDict)
		val encryptedString = new_encryptedStringFrom(plaintextString, this.passwordProvider.password!!)
		//
		return encryptedString
	}

	val _shouldInsertNotUpdate: Boolean
		get() = this._id == null
	//
	// Interface - Imperatives - Saving
	fun saveToDisk(): String? // errStr
	{
		if (this._shouldInsertNotUpdate) {
			return this._saveToDisk_insert()
		} else {
			return this._saveToDisk_update()
		}
	}
	//
	// Internal - Imperatives - Writing
	// For these, we presume consumers/parents/instantiators have only created this wallet if they have gotten the password
	fun _saveToDisk_insert(): String? // errStr
	{
		assert(this._id == null)
		if (this.passwordProvider.password == null) {
			assert(false)
			return null // Asked to insert new when no password exists. Probably ok if currently tearing down logged-in runtime. Ensure self is not being prevented from being freed.
		}
		// only generate _id here after checking shouldInsertNotUpdate since that relies on _id
		this._id = DocumentFileDescription.new_documentId() // generating a new UUID
		// and since we know this is an insertion, let's any other initial centralizable data
		this.insertedAt_sSinceEpoch = System.currentTimeMillis()
		// and now that those values have been placed, we can generate the dictRepresentation
		val errStr = this.__write()
		if (errStr != null) {
			Log.e("Persistence", "Error while saving new object: ${errStr!!}")
		} else {
			Log.v("Persistence", "Saved new ${this}.")
		}
		return errStr
	}
	fun _saveToDisk_update(): String? // errStr
	{
		assert(this._id == null)
		if (this.passwordProvider.password == null) {
			assert(false)
			return null // Asked to update new when no password exists. Probably ok if currently tearing down logged-in runtime. Ensure self is not being prevented from being freed.
		}
		val errStr = this.__write()
		if (errStr != null) {
			Log.e("Persistence", "Error while saving new object: ${errStr!!}")
		} else {
			Log.v("Persistence", "Saved new ${this}.")
		}
		return errStr
	}
	fun __write(): String? // err_str
	{
		val stringToWrite = this.new_encrypted_serializedFileRepresentation()
		//
		return DocumentPersister.Write(stringToWrite, this._id!!, this.collectionName())
	}
	// - Deleting
	fun delete(): String? // err_str
	{
		if (this.passwordProvider.password == null) {
			assert(false)
			return null // Asked to delete when no password exists. Unexpected.
		}
		if (this.insertedAt_sSinceEpoch == null || this._id == null) {
			Log.w("Persistence", "Asked to delete() but had not yet been saved.")
			// posting notifications so UI updates, e.g. to pop views etc
			this.willBeDeleted_fns.invoke(this, "")
			this.wasDeleted_fns.invoke(this, "")
			return null // no error
		}
		assert(this._id != null)
		this.willBeDeleted_fns.invoke(this, "")
		val (err_str, _) = DocumentPersister.RemoveDocuments(
			collectionName = this.collectionName(),
			ids = listOf(this._id!!)
		)
		if (err_str != null) {
			Log.e("Persistence", "Error while deleting object: ${err_str!!}")
		} else {
			Log.d("Persistence", "Deleted ${this}.")
			// NOTE: handlers of this should dispatch async so err_str can be returned -- it would be nice to post this on next-tick but self might have been released by then
			this.wasDeleted_fns.invoke(this, "")
		}
		return err_str
	}
}





// TODO: implement equals support

